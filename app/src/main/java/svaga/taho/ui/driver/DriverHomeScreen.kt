// DriverHomeScreen.kt
package svaga.taho.ui.driver

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.animation.animateContentSize
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.geometry.Polyline
import com.yandex.mapkit.map.*
import com.yandex.mapkit.mapview.MapView
import com.yandex.runtime.image.ImageProvider
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject
import svaga.taho.R
import svaga.taho.data.remote.DriverOrder
import svaga.taho.di.AppModule
import svaga.taho.ui.auth.AuthViewModel
import svaga.taho.util.SseClient
import android.widget.Toast
import kotlinx.coroutines.Job
import svaga.taho.util.playNotificationSound
import java.util.*

private const val TAG = "DriverHomeScreen"

@Composable
fun DriverHomeScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentOrder by remember { mutableStateOf<DriverOrder?>(null) }
    var activeOrder by remember { mutableStateOf<DriverOrder?>(null) }
    var routePolyline by remember { mutableStateOf<PolylineMapObject?>(null) }
    var driverMarker by remember { mutableStateOf<PlacemarkMapObject?>(null) }
    var mapObjects by remember { mutableStateOf<MapObjectCollection?>(null) }

    var newOrdersSseJob by remember { mutableStateOf<Job?>(null) }

    val carIcon = ImageProvider.fromResource(context, R.drawable.ic_car_driver)

    // Получаем SseClient
    val sseClient = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            AppModule.ApiProvider::class.java
        ).sseClient()
    }

    // Получаем токен
    val tokenManager = remember {
        EntryPointAccessors.fromApplication(context, AppModule.ApiProvider::class.java)
            .tokenManager()
    }
    val authViewModel: AuthViewModel = hiltViewModel()
    val token by authViewModel.currentToken.collectAsState(initial = "")

    // Получаем ApiService
    val api = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            AppModule.ApiProvider::class.java
        ).apiService()
    }


    // 1. Подписка на новые заказы — при запуске
    LaunchedEffect(Unit) {
        if (token?.isNotEmpty() == true) {
            newOrdersSseJob = launch {
                sseClient.subscribe(
                    orderId = "driver", // → /api/sse/subscribe/driver
                    token = token!!,
                    scope = this,
                    onUpdate = { json ->
                        Log.d(TAG, "Новый заказ: $json")

                        val order = DriverOrder(
                            id = json.getString("id"),
                            startPoint = Point(json.getDouble("startLat"), json.getDouble("startLon")),
                            endPoint = Point(json.getDouble("endLat"), json.getDouble("endLon")),
                            startAddress = json.getString("startAddress"),
                            endAddress = json.getString("endAddress"),
                            passengerName = json.getString("passengerName"),
                            passengerPhone = json.getString("passengerPhone"),
                            price = json.getString("price"),
                            status = json.getString("status"),
                            distance = json.getString("distance")
                        )

                        currentOrder = order

                        // Звук + вибрация
                        playNotificationSound(context)
                        //vibrateDevice(context)

                        // Строим маршрут
                        buildRoute(order.startPoint, order.endPoint) { points ->
                            routePolyline?.let { mapObjects?.remove(it) }
                            routePolyline = mapObjects?.addPolyline(Polyline(points))
                                ?.apply { setStrokeColor(0xFF1E88E5.toInt()); strokeWidth = 8f }

                            animateDriver(order.startPoint, points, mapObjects, carIcon)
                        }
                    }
                )
            }
        }
    }

    // SSE — новые заказы
    LaunchedEffect(Unit) {
        if (token?.isNotEmpty() == true) {
            sseClient.subscribe(
                orderId = "", // для водителя — пустой, сервер сам шлёт
                token = token!!,
                scope = this,
                onUpdate = { json ->
                    Log.d(TAG, "Новый заказ: $json")

                    val order = DriverOrder(
                        id = json.getString("id"),
                        startPoint = Point(json.getDouble("startLat"), json.getDouble("startLon")),
                        endPoint = Point(json.getDouble("endLat"), json.getDouble("endLon")),
                        startAddress = json.getString("startAddress"),
                        endAddress = json.getString("endAddress"),
                        passengerName = json.getString("passengerName"),
                        passengerPhone = json.getString("passengerPhone"),
                        price = json.getString("price"),
                        status = json.getString("status"),
                        distance = json.getString("distance")
                    )

                    currentOrder = order

                    // Звук + вибрация
                    playNotificationSound(context)
                    //vibrateDevice(context)

                    // Строим маршрут
                    buildRoute(order.startPoint, order.endPoint) { points ->
                        routePolyline?.let { mapObjects?.remove(it) }
                        routePolyline = mapObjects?.addPolyline(Polyline(points))
                            ?.apply { setStrokeColor(0xFF1E88E5.toInt()); strokeWidth = 8f }

                        animateDriver(order.startPoint, points, mapObjects, carIcon)
                    }
                },
                onError = { e -> Log.e(TAG, "SSE ошибка", e) }
            )
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

        currentOrder?.let { order ->
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp)
                    .animateContentSize(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE91E63)),
                elevation = CardDefaults.cardElevation(16.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = "Новый заказ!",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Откуда: ${order.startAddress}", color = Color.White)
                    Text("Куда: ${order.endAddress}", color = Color.White)
                    Text("Расстояние: ${order.distance}", color = Color.White)
                    Text("Цена: ${order.price} ₽", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)

                    Spacer(Modifier.height(20.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Button(
                            onClick = {
                                // Принять заказ
                                scope.launch {
                                    try {
                                        api.acceptOrder("Bearer $token", order.id)
                                        activeOrder = DriverOrder(
                                            id = order.id,
                                            startPoint = Point(0.0, 0.0), // потом построим маршрут
                                            endPoint = Point(0.0, 0.0),
                                            startAddress = order.startAddress,
                                            endAddress = order.endAddress,
                                            passengerName = order.passengerName,
                                            passengerPhone = order.passengerPhone,
                                            price = order.price,
                                            distance = order.distance,
                                            status = order.status
                                        )
                                        currentOrder = null
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Не удалось принять", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Принять", color = Color(0xFFE91E63), fontSize = 18.sp)
                        }

                        OutlinedButton(
                            onClick = { currentOrder = null },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Отклонить", color = Color.White)
                        }
                    }
                }
            }
        }
        // ←←←←←←←←←←←←←←←←←←←←←←←←
    }

    DisposableEffect(Unit) {
        onDispose {
            sseClient.disconnect()
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

// Простой маршрут (без masstransit)
private fun buildRoute(
    from: Point,
    to: Point,
    onReady: (List<Point>) -> Unit
) {
    // Прямой маршрут — линия от A до B
    onReady(listOf(from, to))
}