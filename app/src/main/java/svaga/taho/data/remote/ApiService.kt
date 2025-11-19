package svaga.taho.data.remote

import retrofit2.http.Body
import retrofit2.http.POST

interface ApiService {

    @POST("api/auth/register") // ← поменяй путь на свой
    suspend fun register(@Body request: RegisterRequest): Any // можно Unit или твой ответ

    @POST("api/auth/login")   // ← поменяй путь на свой
    suspend fun login(@Body request: LoginRequest): LoginResponse
}

data class RegisterRequest(
    val phone: String,
    val name: String,
    val password: String, // если нужно
    val role: String // если нужно
)

data class LoginRequest(
    val phone: String,
    val password: String
)

data class LoginResponse(
    val token: String
)