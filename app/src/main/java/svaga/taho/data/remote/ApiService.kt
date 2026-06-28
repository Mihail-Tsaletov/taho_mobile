package svaga.taho.data.remote

import ActiveOrderResponse
import android.R
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter


interface ApiService {

    @POST("api/auth/register")
    suspend fun register(@Body request: RegisterRequest): Any

    @POST("api/auth/send-telegram-code")
    suspend fun sendTelegramCode(@Body body: Map<String, String>): Response<Unit>

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
        @Query("statuses") statuses: String = "PENDING,ACCEPTED,IN_PROGRESS,ARRIVED,ASSIGNED"
    ): Response<List<ActiveOrderResponse>>

    @GET("api/orders/getOrdersByDriverIdWithStatuses")
    suspend fun getActiveOrdersForDriver(
        @Header("Authorization") token: String,
        @Query("statuses") statuses: String = "ASSIGNED,ACCEPTED,IN_PROGRESS,ARRIVED"
    ): Response<List<DriverOrder>>

    @POST("api/orders/{id}/accept")
    suspend fun acceptOrder(
        @Header("Authorization") token: String,
        @Path("id") orderId: String,
        @Query("timeToArrive") timeToArrive: String? = null
    ): Response<ResponseBody>

    @POST("api/orders/{id}/cancel")
    suspend fun cancelOrder(@Header("Authorization") token: String, @Path("id") orderId: String): Response<ResponseBody>
    @POST("api/orders/{id}/arrived")
    suspend fun driverArrived(@Header("Authorization") token: String, @Path("id") orderId: String): Response<ResponseBody>

    @POST("api/orders/{id}/complete")
    suspend fun driverComplete(@Header("Authorization") token: String,
                               @Path("id") orderId: String,
                               @Body trackJson: String,
                               @Query("downtime") downtime: String? = null,
                               @Query("zaezd") zaezd: Int = 0): Response<ResponseBody>

    @POST("api/orders/{id}/pickedUp")
    suspend fun driverPickedUp(@Header("Authorization") token: String, @Path("id") orderId: String): Response<ResponseBody>

    @GET("api/users/getUser")
    suspend fun getUserProfile(
        @Header("Authorization") token: String
    ): UserProfileResponse


    @GET("api/users/getDriver")
    suspend fun getDriverProfile(@Header("Authorization") token: String): DriverProfileResponse

    @POST("api/users/getOnLine")
    suspend fun toggleOnlineStatus(
        @Header("Authorization") token: String,
        @Query("parkingId") parkingId: Int? = null
    ): Response<ResponseBody>

    @GET("api/orders/getAllOrdersByDriverId")
    suspend fun getOrdersByDriverId(
        @Header("Authorization") token: String,
        @Query("driverId") driverId: String,
        @Query("from") from: String = "nulаl",
        @Query("to") to: String = "nulаl"
    ): List<OrderWeb>

    @POST("api/orders/createByDriver")
    suspend fun createOrderByDriver(
        @Header("Authorization") token: String,
        @Body request: CreateOrderRequest
    ): Response<ResponseBody>

    @POST("api/orders/calculate-price")
    suspend fun calculatePrice(
        @Header("Authorization") token: String,
        @Body request: Map<String, String>
    ): Response<Map<String, Any>>

    @PUT("api/orders/setPriceBetweenDistricts")
    suspend fun setPriceBetweenDistricts(
        @Header("Authorization") token: String,
        @Body request: Map<String, String>
    ): Response<Unit>


}



data class OrderWeb(
    val orderId: String,
    val price: BigDecimal?,
    val startAddress: String,
    val endAddress: String,
    val orderTime: String  // ← строка в ISO формате
) {
    val createdAt: LocalDateTime
        get() = LocalDateTime.parse(orderTime, DateTimeFormatter.ISO_DATE_TIME)
}


data class CreateOrderRequest(
    val startPoint: String,
    val endPoint: String,
    val startAddress: String,
    val endAddress: String,
    val pet: Boolean = false,
    val load: Boolean = false
)

data class RegisterRequest(
    val phone: String,
    val name: String,
    val password: String,
    val role: String,
   // val code: String
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
    val status: String,
    val balance: BigDecimal,
    val parkId: Int? = null,
    val numberInLine: Int
)


