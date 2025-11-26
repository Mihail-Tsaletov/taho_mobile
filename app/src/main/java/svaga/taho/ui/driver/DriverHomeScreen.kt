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
import java.util.*

private const val TAG = "DriverHomeScreen"

@Composable
fun DriverHomeScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentOrder by remember { mutableStateOf<DriverOrder?>(null) }
    var routePolyline by remember { mutableStateOf<PolylineMapObject?>(null) }
    var driverMarker by remember { mutableStateOf<PlacemarkMapObject?>(null) }
    var mapObjects by remember { mutableStateOf<MapObjectCollection?>(null) }

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
    val apiService = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            AppModule.ApiProvider::class.java
        ).apiService()
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
                        status = json.getString("status")
                    )

                    currentOrder = order

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
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(12.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = if (order.status == "NEW") "Новый заказ!" else "Заказ принят",
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = if (order.status == "NEW") Color(0xFFE91E63) else Color.Green
                    )

                    Spacer(Modifier.height(12.dp))

                    Text("Откуда: ${order.startAddress}")
                    Text("Куда: ${order.endAddress}")
                    Text("Цена: ${order.price} ₽")

                    Spacer(Modifier.height(16.dp))

                    if (order.status == "NEW") {
                        Row {
                            Button(
                                onClick = {
                                    scope.launch {
                                        try {
                                            apiService.acceptOrder("Bearer $token", order.id)
                                            currentOrder = order.copy(status = "ACCEPTED")
                                        } catch (e: Exception) {
                                            Log.e(TAG, "Не удалось принять заказ", e)
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Принять", color = Color.White)
                            }
                            Spacer(Modifier.width(12.dp))
                            OutlinedButton(
                                onClick = { currentOrder = null },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Отклонить")
                            }
                        }
                    } else {
                        Text("Пассажир: ${order.passengerName}", fontWeight = FontWeight.Medium)
                        Text(
                            text = "Позвонить: ${order.passengerPhone}",
                            color = Color.Blue,
                            modifier = Modifier.clickable {
                                context.startActivity(
                                    Intent(Intent.ACTION_DIAL, Uri.parse("tel:${order.passengerPhone}"))
                                )
                            }
                        )
                        Spacer(Modifier.height(12.dp))
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