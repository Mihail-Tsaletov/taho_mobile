package svaga.taho.data.remote

import ActiveOrderResponse
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.math.BigDecimal
import java.time.LocalDateTime


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
        @Query("statuses") statuses: String = "PENDING,ACCEPTED,IN_PROGRESS"
    ): Response<List<ActiveOrderResponse>>

    @GET("api/orders/getOrdersByDriverIdWithStatuses")
    suspend fun getActiveOrdersForDriver(
        @Header("Authorization") token: String,
        @Query("statuses") statuses: String = "ASSIGNED,ACCEPTED,IN_PROGRESS"
    ): Response<List<DriverOrder>>

    @POST("api/orders/{id}/accept")
    suspend fun acceptOrder(@Header("Authorization") token: String, @Path("id") orderId: String): Response<ResponseBody>

    @POST("api/orders/{id}/arrived")
    suspend fun driverArrived(@Header("Authorization") token: String, @Path("id") orderId: String): Response<ResponseBody>

    @POST("api/orders/{id}/complete")
    suspend fun driverComplete(@Header("Authorization") token: String,
                               @Path("id") orderId: String,
                               @Body trackJson: String): Response<ResponseBody>

    @POST("api/orders/{id}/pickedUp")
    suspend fun driverPickedUp(@Header("Authorization") token: String, @Path("id") orderId: String): Response<ResponseBody>

    @GET("api/users/getUser")
    suspend fun getUserProfile(
        @Header("Authorization") token: String
    ): UserProfileResponse


    @GET("api/users/getDriver")
    suspend fun getDriverProfile(@Header("Authorization") token: String): DriverProfileResponse

    @POST("api/users/getOnLine")
    suspend fun toggleOnlineStatus(@Header("Authorization") token: String): Response<ResponseBody>


    @GET("getAllOrdersByDriverId")
    suspend fun getOrdersByDriverId(
        @Header("Authorization") token: String,
        @Query("driverId") driverId: String
    ): List<OrderWeb>
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

data class UserProfileResponse(
    val name: String,
    val phone: String,
)

data class DriverProfileResponse(
    val driverId: String,
    val userId: String,
    val name: String,
    val phoneNumber: String,
    val status: String
)


///лучше сделать чтобы сервер фильтровал по временому отрезку заказы, то шо так клиент получает все заказы и уже потом думает какие показывать
data class OrderWeb(
    val orderId: String,
    val price: BigDecimal,
    val startAddress: String,
    val endAddress: String,
    val orderTime: LocalDateTime
)