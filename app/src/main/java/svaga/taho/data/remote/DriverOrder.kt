package svaga.taho.data.remote

import com.yandex.mapkit.geometry.Point

data class DriverOrder(
    val id: String,
    val startPoint: Point,
    val endPoint: Point,
    val startAddress: String,
    val endAddress: String,
    val passengerName: String,
    val passengerPhone: String,
    val price: String,
    val status: String
)