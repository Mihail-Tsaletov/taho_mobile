package svaga.taho.data.remote

import ActiveOrderResponse
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import svaga.taho.data.local.TokenManager
import svaga.taho.utils.ActiveOrderManager

interface ApiService {

    @POST("api/auth/register")
    suspend fun register(@Body request: RegisterRequest): Any // можно Unit или твой ответ

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @GET("api/sse/subscribe/driver")
    suspend fun subscribeDriverToNewOrders(): Response<ResponseBody>

    @POST("api/orders")
    suspend fun createOrder(@Header("Authorization") token: String,
                            @Body request: CreateOrderRequest): Response<ResponseBody>

    @GET("api/orders/getOrdersByUserIdWithStatuses")
    suspend fun getActiveOrders(
        @Header("Authorization") token: String,
        @Query("statuses") statuses: String = "PENDING,ACCEPTED,PICKED_UP"
    ): Response<List<ActiveOrderResponse>>

    @GET("api/orders/getOrdersByDriverIdWithStatuses")
    suspend fun getActiveOrdersForDriver(
        @Header("Authorization") token: String,
        @Query("statuses") statuses: String = "ASSIGNED,ACCEPTED,PICKED_UP"
    ): Response<List<DriverOrder>>

    @POST("api/orders/{id}/accept")
    suspend fun acceptOrder(@Header("Authorization") token: String, @Path("id") orderId: String): Response<ResponseBody>

    @POST("api/orders/{id}/arrived")
    suspend fun driverArrived(@Header("Authorization") token: String, @Path("id") orderId: String): Response<ResponseBody>

}

data class CreateOrderRequest(
    val startPoint: String,
    val endPoint: String,
    val startAddress: String,
    val endAddress: String
)

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