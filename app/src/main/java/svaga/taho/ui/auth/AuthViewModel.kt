package svaga.taho.ui.auth

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import svaga.taho.data.local.TokenManager
import svaga.taho.data.remote.ApiService
import svaga.taho.data.remote.LoginRequest
import svaga.taho.data.remote.RegisterRequest
import svaga.taho.util.parseJwtRole
import javax.inject.Inject


private const val TAG = "AuthViewModel"

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val api: ApiService,
    val tokenManager: TokenManager
) : ViewModel() {

    private val _event = MutableSharedFlow<AuthEvent>(extraBufferCapacity = 1)
    val event = _event.asSharedFlow()
    private val _currentToken = MutableStateFlow<String?>(null)
    val currentToken: StateFlow<String?> = _currentToken.asStateFlow()

    sealed class AuthEvent {
        object ToRegister : AuthEvent()
        object ToLogin : AuthEvent()
        object ToRoleSelection : AuthEvent()
        object ToClientHome : AuthEvent()
        object ToDriverHome : AuthEvent()
        data class Error(val message: String) : AuthEvent()
        object Loading : AuthEvent()
    }

    fun register(phone: String, name: String, password: String) {
        viewModelScope.launch {
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
            } catch (e: Exception) {
                _event.emit(AuthEvent.Error(e.message ?: "Ошибка регистрации"))
            }
        }
    }

    fun login(phone: String, password: String) {
        viewModelScope.launch {
            _event.emit(AuthEvent.Loading)
            try {
                Log.d(TAG, "phone: $phone, password: $password")
                val response = api.login(LoginRequest(phone, password))
                Log.d(TAG, "response: $response")
                val roleFromToken = parseJwtRole(response.token)
                    ?: throw IllegalStateException("Не удалось определить роль из токена")

                // Че то хуйню набезорбазил, надо будет поменять ******************
                val token = response.token
                val profile = api.getUserProfile("Bearer $token")

                tokenManager.saveAuth(response.token, roleFromToken, profile.name,
                    phone = profile.phone)
                ///// ************************
                _currentToken.value = response.token

                when (roleFromToken) {
                    "CLIENT" -> _event.emit(AuthEvent.ToClientHome)
                    "DRIVER" -> _event.emit(AuthEvent.ToRoleSelection)
                    else -> _event.emit(AuthEvent.Error("Неизвестная роль: $roleFromToken"))
                }
            } catch (e: Exception) {
                _event.emit(AuthEvent.Error(e.message ?: "Ошибка входа"))
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