package svaga.taho.ui.auth

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import svaga.taho.data.local.TokenManager
import svaga.taho.data.remote.ApiService
import svaga.taho.data.remote.LoginRequest
import svaga.taho.data.remote.RegisterRequest
import svaga.taho.util.parseJwtRole
import javax.inject.Inject


private const val TAG = "AuthViewModel"

// ── Антиспам: простой rate-limiter, N попыток за окно времени, потом блок ──
private class RateLimiter(
    private val maxAttempts: Int,
    private val windowMs: Long,
    private val blockDurationMs: Long
) {
    private var attemptCount = 0
    private var windowStartedAt = 0L
    private var blockedUntil = 0L

    // true — попытка разрешена (и засчитана), false — заблокировано
    fun tryAttempt(): Boolean {
        val now = System.currentTimeMillis()

        if (now < blockedUntil) return false

        if (now - windowStartedAt > windowMs) {
            windowStartedAt = now
            attemptCount = 0
        }

        attemptCount++

        if (attemptCount > maxAttempts) {
            blockedUntil = now + blockDurationMs
            return false
        }

        return true
    }

    fun secondsLeftBlocked(): Int {
        val left = blockedUntil - System.currentTimeMillis()
        return if (left > 0) ((left + 999) / 1000).toInt() else 0
    }

    fun isBlocked(): Boolean = System.currentTimeMillis() < blockedUntil
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val api: ApiService,
    val tokenManager: TokenManager
) : ViewModel() {

    private val _event = MutableSharedFlow<AuthEvent>(extraBufferCapacity = 1)
    val event = _event.asSharedFlow()
    private val _currentToken = MutableStateFlow<String?>(null)
    val currentToken: StateFlow<String?> = _currentToken.asStateFlow()

    private val _showCodeDialog = MutableStateFlow(false)
    val showCodeDialog: StateFlow<Boolean> = _showCodeDialog.asStateFlow()

    private val _verificationPhone = MutableStateFlow<String?>(null)
    val verificationPhone: StateFlow<String?> = _verificationPhone.asStateFlow()

    private val _isCodeVerified = MutableStateFlow(false)
    val isCodeVerified: StateFlow<Boolean> = _isCodeVerified.asStateFlow()

    private val _verificationCode = MutableStateFlow("")           // ← важно!
    val verificationCode: StateFlow<String> = _verificationCode.asStateFlow()

    private var loginAttempts = 0
    private var lastAttemptTime = 0L

    // ── Антиспам: лимитеры и cooldown для UI ────────────────────────
    // 5 попыток входа за 60 секунд, блок на 30 секунд
    private val loginLimiter = RateLimiter(maxAttempts = 5, windowMs = 60_000L, blockDurationMs = 30_000L)
    // 3 попытки регистрации за 2 минуты, блок на 60 секунд
    private val registerLimiter = RateLimiter(maxAttempts = 3, windowMs = 120_000L, blockDurationMs = 60_000L)

    private val _loginCooldownSeconds = MutableStateFlow(0)
    val loginCooldownSeconds: StateFlow<Int> = _loginCooldownSeconds.asStateFlow()

    private val _registerCooldownSeconds = MutableStateFlow(0)
    val registerCooldownSeconds: StateFlow<Int> = _registerCooldownSeconds.asStateFlow()

    private var loginCooldownTickerJob: Job? = null
    private var registerCooldownTickerJob: Job? = null

    private fun startLoginCooldownTicker() {
        loginCooldownTickerJob?.cancel()
        loginCooldownTickerJob = viewModelScope.launch {
            while (loginLimiter.isBlocked()) {
                _loginCooldownSeconds.value = loginLimiter.secondsLeftBlocked()
                delay(1000)
            }
            _loginCooldownSeconds.value = 0
        }
    }

    private fun startRegisterCooldownTicker() {
        registerCooldownTickerJob?.cancel()
        registerCooldownTickerJob = viewModelScope.launch {
            while (registerLimiter.isBlocked()) {
                _registerCooldownSeconds.value = registerLimiter.secondsLeftBlocked()
                delay(1000)
            }
            _registerCooldownSeconds.value = 0
        }
    }
    // ──────────────────────────────────────────────────────────────


    sealed class AuthEvent {
        object ToRegister : AuthEvent()
        object ToLogin : AuthEvent()
        object ToRoleSelection : AuthEvent()
        object ToClientHome : AuthEvent()
        object ToDriverHome : AuthEvent()
        data class Error(val message: String) : AuthEvent()
        object Loading : AuthEvent()
    }


    fun verifyTelegramCode(code: String) {
        _verificationCode.value = code

        if (code.length == 6) {
            viewModelScope.launch {
                _isCodeVerified.value = true
                _showCodeDialog.value = false
                _event.emit(AuthEvent.Error("Номер успешно подтверждён ✓"))
                // TODO: здесь реальная проверка кода через API
            }
        }
    }

    fun sendTelegramCode(phone: String) {
        viewModelScope.launch {
            _event.emit(AuthEvent.Loading)
            try {
                val response = api.sendTelegramCode(mapOf("phone" to phone))
                if (response.isSuccessful) {
                    _verificationPhone.value = phone
                    _showCodeDialog.value = true
                    _verificationCode.value = ""
                    _isCodeVerified.value = false
                    _event.emit(AuthEvent.Error("Код отправлен в Telegram"))
                } else {
                    _event.emit(AuthEvent.Error("Не удалось отправить код"))
                }
            } catch (e: Exception) {
                _event.emit(AuthEvent.Error("Ошибка отправки кода"))
            }
        }
    }

    fun register(
        phone: String,
        name: String,
        password: String,
        code: String
    ) {
        viewModelScope.launch {
            if (!registerLimiter.tryAttempt()) {
                startRegisterCooldownTicker()
                _event.emit(AuthEvent.Error("Слишком много попыток регистрации. Повторите через ${registerLimiter.secondsLeftBlocked()} сек."))
                return@launch
            }

            _event.emit(AuthEvent.Loading)
            try {
                val response = api.register(
                    RegisterRequest(
                        phone = phone,
                        // code = code,
                        name = name,
                        password = password,
                        role = "CLIENT"
                    )
                )

                // После успешной регистрации сразу логинимся
                login(phone, password)

            } catch (e: retrofit2.HttpException) {
                val message = when (e.code()) {
                    400 -> "Неверный или просроченный код подтверждения"
                    409 -> "Пользователь с таким номером уже зарегистрирован"
                    500 -> "Ошибка сервера, попробуйте позже"
                    else -> "Ошибка: ${e.code()}"
                }
                _event.emit(AuthEvent.Error(message))
            } catch (e: Exception) {
                _event.emit(AuthEvent.Error("Что-то пошло не так"))
            }
        }
    }

    fun register(phone: String, name: String, password: String) {
        viewModelScope.launch {
            if (!registerLimiter.tryAttempt()) {
                startRegisterCooldownTicker()
                _event.emit(AuthEvent.Error("Слишком много попыток регистрации. Повторите через ${registerLimiter.secondsLeftBlocked()} сек."))
                return@launch
            }

            _event.emit(AuthEvent.Loading)
            try {
                api.register(
                    RegisterRequest(
                        phone = phone,
                        name = name,
                        password = password,
                        role = "CLIENT" // по умолчанию
                    )
                )
                // После успешной регистрации — сразу логинимся
                login(phone, password)
            } catch (e: retrofit2.HttpException) {
                val message = when (e.code()) {
                    409 -> "Пользователь с таким номером уже зарегистрирован"
                    400 -> "Проверьте правильность введённых данных"
                    500 -> "Ошибка сервера, попробуйте позже"
                    else -> "Ошибка: ${e.code()}"
                }
                _event.emit(AuthEvent.Error(message))
            } catch (e: java.net.UnknownHostException) {
                _event.emit(AuthEvent.Error("Нет подключения к интернету"))
            } catch (e: java.net.SocketTimeoutException) {
                _event.emit(AuthEvent.Error("Сервер не отвечает, попробуйте позже"))
            } catch (e: Exception) {
                _event.emit(AuthEvent.Error("Что-то пошло не так"))
            }
        }
    }

    fun login(phone: String, password: String) {
        viewModelScope.launch {
            if (!loginLimiter.tryAttempt()) {
                startLoginCooldownTicker()
                _event.emit(AuthEvent.Error("Слишком много попыток входа. Повторите через ${loginLimiter.secondsLeftBlocked()} сек."))
                return@launch
            }

            _event.emit(AuthEvent.Loading)
            try {
                Log.d(TAG, "phone: $phone, password: $password")
                val response = api.login(LoginRequest(phone, password))
                Log.d(TAG, "response: $response")
                val roleFromToken = parseJwtRole(response.token)
                    ?: throw IllegalStateException("Не удалось определить роль из токена")

                // TODO Че то хуйню набезорбазил, надо будет поменять ******************
                val token = response.token
                val profile = api.getUserProfile("Bearer $token")

                tokenManager.saveAuth(response.token, roleFromToken, profile.name,
                    phone = profile.phone)
                tokenManager.saveLastRole(roleFromToken) // ← добавить эту строку

                ///// ************************
                _currentToken.value = response.token
                Log.d(TAG, "PROFILE NAME EBYCHI SLUCHAS: $profile.name")
                when (roleFromToken) {
                    "CLIENT" -> _event.emit(AuthEvent.ToClientHome)
                    "DRIVER" -> _event.emit(AuthEvent.ToDriverHome)
                    else -> _event.emit(AuthEvent.Error("Неизвестная роль: $roleFromToken"))
                }
            } catch (e: retrofit2.HttpException) {
                val message = when (e.code()) {
                    401 -> "Неверный номер телефона или пароль"
                    404 -> "Пользователь не найден"
                    409 -> "Пользователь с таким номером уже существует"
                    500 -> "Ошибка сервера, попробуйте позже"
                    else -> "Ошибка: ${e.code()}"
                }
                _event.emit(AuthEvent.Error(message))
            } catch (e: java.net.UnknownHostException) {
                _event.emit(AuthEvent.Error("Нет подключения к интернету"))
            } catch (e: java.net.SocketTimeoutException) {
                _event.emit(AuthEvent.Error("Сервер не отвечает, попробуйте позже"))
            } catch (e: Exception) {
                _event.emit(AuthEvent.Error("Что-то пошло не так"))
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            tokenManager.clear()
            _event.emit(AuthEvent.ToLogin)
        }
    }
}