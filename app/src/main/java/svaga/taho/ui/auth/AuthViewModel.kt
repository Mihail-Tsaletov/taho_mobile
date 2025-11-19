package svaga.taho.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import svaga.taho.data.local.TokenManager
import svaga.taho.data.remote.ApiService
import svaga.taho.data.remote.LoginRequest
import svaga.taho.data.remote.RegisterRequest
import svaga.taho.util.parseJwtRole
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val api: ApiService,
    val tokenManager: TokenManager
) : ViewModel() {

    private val _event = MutableSharedFlow<AuthEvent>(extraBufferCapacity = 1)
    val event = _event.asSharedFlow()

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
                val response = api.login(LoginRequest(phone, password))
                val roleFromToken = parseJwtRole(response.token)
                    ?: throw IllegalStateException("Не удалось определить роль из токена")

                tokenManager.saveAuth(response.token, roleFromToken)

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