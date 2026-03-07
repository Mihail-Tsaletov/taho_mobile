package svaga.taho.data.remote

import com.yandex.mapkit.geometry.Point

data class DriverOrder(
    val id: String,
    val startPoint: String,
    val endPoint: String,
    val startAddress: String,
    val endAddress: String,
    val passengerName: String,
    val passengerPhone: String,
    val price: String? = null,
    val status: String,
    val distance: String,
    val inCity: Boolean
){
    // Удобные геттеры — чтобы не менять весь код
    val startPointLatLon: Point
        get() = parsePoint(startPoint)

    val endPointLatLon: Point
        get() = parsePoint(endPoint)

    private fun parsePoint(str: String): Point {
        val parts = str.split(",").map { it.trim().toDouble() }
        return Point(parts[0], parts[1])
    }
}