data class ActiveOrderResponse(
    val id: String,
    val startAddress: String,
    val endAddress: String,
    val status: String,
    val driverName: String? = null,
    val driverPhone: String? = null,
    val price: String? = null
)