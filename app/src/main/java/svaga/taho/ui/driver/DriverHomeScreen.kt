// DriverHomeScreen.kt
package svaga.taho.ui.driver

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.geometry.Polyline
import com.yandex.mapkit.map.*
import com.yandex.mapkit.mapview.MapView
import com.yandex.runtime.image.ImageProvider
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONObject
import svaga.taho.R
import svaga.taho.data.remote.DriverOrder
import svaga.taho.di.AppModule
import svaga.taho.util.SseClient
import svaga.taho.util.playNotificationSound
import java.util.*

private const val TAG = "DriverHomeScreen"

@Composable
fun DriverHomeScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Текущий заказ (новый или активный)
    var currentOrder by remember { mutableStateOf<DriverOrder?>(null) }

    var routePolyline by remember { mutableStateOf<PolylineMapObject?>(null) }
    var driverMarker by remember { mutableStateOf<PlacemarkMapObject?>(null) }
    var mapObjects by remember { mutableStateOf<MapObjectCollection?>(null) }

    var newOrdersSseJob by remember { mutableStateOf<Job?>(null) }
    var orderUpdatesSseJob by remember { mutableStateOf<Job?>(null) }

    val sseClient = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            AppModule.ApiProvider::class.java
        ).sseClient()
    }

    val token by EntryPointAccessors.fromApplication(
        context.applicationContext,
        AppModule.ApiProvider::class.java
    ).tokenManager().tokenFlow.collectAsState(initial = "")


    val apiService = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            AppModule.ApiProvider::class.java
        ).apiService()
    }

    val carIcon = ImageProvider.fromResource(context, R.drawable.ic_car_driver)

    // 1. Подписка на новые заказы — при запуске
    LaunchedEffect(token) {
        if (token?.isNotEmpty() == true) {
            newOrdersSseJob?.cancel()
            newOrdersSseJob = launch {
                sseClient.subscribe(
                    orderId = "driver", // → /api/sse/subscribe/driver
                    token = token!!,
                    scope = this,
                    onUpdate = { json ->
                        Log.d(TAG, "Новый заказ прилетел: $json")

                        val startPointStr = json.getString("startPoint")
                        val endPointStr = json.getString("endPoint")

                        val startCoords = startPointStr.split(",").map { it.trim().toDouble() }
                        val endCoords = endPointStr.split(",").map { it.trim().toDouble() }

                        val order = DriverOrder(
                            id = json.getString("id"),
                            startPoint = Point(startCoords[0], startCoords[1]),
                            endPoint = Point(endCoords[0], endCoords[1]),
                            startAddress = json.getString("startAddress"),
                            endAddress = json.getString("endAddress"),
                            passengerName = json.getString("passengerName"),
                            passengerPhone = json.getString("passengerPhone"),
                            price = json.getString("price"),
                            distance = json.getString("distance"),
                            status = json.getString("status")
                        )

                        currentOrder = order
                        playNotificationSound(context)
                    }
                )
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                MapView(ctx).apply {
                    mapWindow.map.move(CameraPosition(Point(55.7558, 37.6173), 12f, 0f, 0f))
                    mapObjects = mapWindow.map.mapObjects
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                view.onStart()
                MapKitFactory.getInstance().onStart()
            }
        )
        //
        //
        // СРАЗУ ПОЯВЛЯЕТСЯ ТИПА ПРИНЯТЫЙ ЗАКАЗ, НЕТ ОКОШКА С КНОПКОЙ ПРИНЯТЬ
        //
        //
        currentOrder?.let { order ->
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(12.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    if (order.status == "NEW") {
                        // НОВЫЙ ЗАКАЗ — только откуда/куда + кнопка принять
                        Text("Новый заказ!", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Color(0xFFE91E63))
                        Spacer(Modifier.height(12.dp))
                        Text("Откуда: ${order.startAddress}", fontWeight = FontWeight.Medium)
                        Text("Куда: ${order.endAddress}", fontWeight = FontWeight.Medium)
                        Text("Цена: ${order.price} ₽", fontWeight = FontWeight.Bold, fontSize = 20.sp)

                        Spacer(Modifier.height(20.dp))

                        Button(
                            onClick = {
                                scope.launch {
                                    try {
                                        apiService.acceptOrder("Bearer $token", order.id)
                                        currentOrder = order.copy(status = "ACCEPTED")

                                        // 2. ПОДПИСЫВАЕМСЯ НА КОНКРЕТНЫЙ ЗАКАЗ
                                        orderUpdatesSseJob?.cancel()
                                        orderUpdatesSseJob = launch {
                                            sseClient.subscribe(
                                                orderId = order.id,
                                                token = token!!,
                                                scope = this,
                                                onUpdate = { json ->
                                                    val status = json.getString("status")
                                                    when (status) {
                                                        "CANCELLED", "COMPLETED" -> {
                                                            currentOrder = null
                                                            orderUpdatesSseJob?.cancel()
                                                        }
                                                    }
                                                }
                                            )
                                        }

                                        // Строим маршрут
                                        buildRoute(order.startPoint, order.endPoint) { points ->
                                            routePolyline?.let { mapObjects?.remove(it) }
                                            routePolyline = mapObjects?.addPolyline(Polyline(points))
                                                ?.apply { setStrokeColor(0xFF1E88E5.toInt()); strokeWidth = 8f }
                                            animateDriver(order.startPoint, points, mapObjects, carIcon)
                                        }
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Не удалось принять заказ", e)
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5))
                        ) {
                            Text("Принять заказ", color = Color.White, fontSize = 18.sp)
                        }
                    } else {
                        // АКТИВНЫЙ ЗАКАЗ — полная инфа
                        Text("Заказ принят", color = Color.Green, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                        Spacer(Modifier.height(12.dp))
                        Text("Пассажир: ${order.passengerName}")
                        Text(
                            text = "Телефон: ${order.passengerPhone}",
                            color = Color.Blue,
                            modifier = Modifier.clickable {
                                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${order.passengerPhone}")))
                            }
                        )
                        Text("Откуда: ${order.startAddress}")
                        Text("Куда: ${order.endAddress}")

                        Spacer(Modifier.height(16.dp))

                        Button(onClick = {
                            scope.launch {
                                apiService.driverArrived("Bearer $token", order.id)
                            }
                        }) {
                            Text("Я на месте")
                        }
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            newOrdersSseJob?.cancel()
            orderUpdatesSseJob?.cancel()
        }
    }
}

// Анимация машины
private fun animateDriver(start: Point, points: List<Point>, mapObjects: MapObjectCollection?, carIcon: ImageProvider) {
    val marker = mapObjects?.addPlacemark(start)?.apply {
        setIcon(carIcon)
        zIndex = 100f
    }

    var i = 0
    val timer = Timer()
    timer.schedule(object : TimerTask() {
        override fun run() {
            if (i >= points.size) {
                cancel()
                return
            }
            marker?.geometry = points[i]
            i += 5
        }
    }, 0, 80)
}

// Маршрут (простой)
private fun buildRoute(
    from: Point,
    to: Point,
    onReady: (List<Point>) -> Unit
) {
    onReady(listOf(from, to))
}