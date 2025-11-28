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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import svaga.taho.R
import svaga.taho.data.remote.DriverOrder
import svaga.taho.di.AppModule
import svaga.taho.ui.client.sseJob
import svaga.taho.util.SseClient
import svaga.taho.util.playNotificationSound
import java.util.*

private const val TAG = "DriverHomeScreen"
var sseJob by mutableStateOf<Job?>(null)

@Composable
fun DriverHomeScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // СОСТОЯНИЯ
    var incomingOrder by remember { mutableStateOf<DriverOrder?>(null) }     // новый заказ
    var activeOrder by remember { mutableStateOf<DriverOrder?>(null) }       // принятый заказ
    var routePolyline by remember { mutableStateOf<PolylineMapObject?>(null) }
    var driverMarker by remember { mutableStateOf<PlacemarkMapObject?>(null) }
    var mapObjects by remember { mutableStateOf<MapObjectCollection?>(null) }
    // ←←←←←←←←←←←←←←←←←←←←←←←←

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

    // 1. ПОДПИСКА НА НОВЫЕ ЗАКАЗЫ
    LaunchedEffect(token) {
        if (token?.isNotEmpty() == true) {
            newOrdersSseJob?.cancel()
            newOrdersSseJob = launch {
                sseClient.subscribe(
                    orderId = "driver",
                    token = token!!,
                    scope = this,
                    onUpdate = { json ->
                        Log.d(TAG, "Новый заказ прилетел: $json")

                        val order = DriverOrder(
                            id = json.getString("id"),
                            startPoint = json.getString("startPoint"),
                            endPoint = json.getString("endPoint"),
                            startAddress = json.getString("startAddress"),
                            endAddress = json.getString("endAddress"),
                            passengerName = json.getString("passengerName"),
                            passengerPhone = json.getString("passengerPhone"),
                            price = json.getString("price"),
                            distance = json.getString("distance"),
                            status = "ASSIGNED"
                        )

                        incomingOrder = order
                        playNotificationSound(context)
                    }
                )
            }
        }
    }


    // 2. ПРИНЯТИЕ ЗАКАЗА
    val acceptOrder: (DriverOrder) -> Unit = { order ->
        scope.launch {
            try {
                apiService.acceptOrder("Bearer $token", order.id)

                activeOrder = order.copy(status = "ACCEPTED")
                incomingOrder = null

                // Запускаем SSE для конкретного заказа
                orderUpdatesSseJob?.cancel()
                orderUpdatesSseJob = launch {
                    sseClient.subscribe(
                        orderId = order.id,
                        token = token!!,
                        scope = this,
                        onUpdate = { json ->
                            val status = json.optString("status")
                            when (status) {
                                "CANCELLED", "COMPLETED" -> {
                                    activeOrder = null
                                    orderUpdatesSseJob?.cancel()
                                }
                            }
                        }
                    )
                }

                // Строим маршрут
                buildRoute(order.startPointLatLon, order.endPointLatLon) { points ->
                    routePolyline?.let { mapObjects?.remove(it) }
                    routePolyline = mapObjects?.addPolyline(Polyline(points))
                        ?.apply { setStrokeColor(0xFF1E88E5.toInt()); strokeWidth = 8f }

                    animateDriver(order.startPointLatLon, points, mapObjects, carIcon, scope)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Не удалось принять заказ", e)
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

        // ←←←←←←←←←←←←←←←←←←←←←←←←
        // НОВЫЙ ЗАКАЗ — плашка с кнопкой "Принять"
        incomingOrder?.let { order ->
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE91E63))
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("Новый заказ!", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    Text("Откуда: ${order.startAddress}", color = Color.White)
                    Text("Куда: ${order.endAddress}", color = Color.White)
                    Text("Цена: ${order.price} ₽", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)

                    Spacer(Modifier.height(20.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Button(
                            onClick = { acceptOrder(order) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Принять", color = Color(0xFFE91E63))
                        }
                        OutlinedButton(
                            onClick = { incomingOrder = null },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Отклонить", color = Color.White)
                        }
                    }
                }
            }
        }
        // ←←←←←←←←←←←←←←←←←←←←←←←←

        // ←←←←←←←←←←←←←←←←←←←←←←←←
        // АКТИВНЫЙ ЗАКАЗ — полная карточка
        activeOrder?.let { order ->
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Заказ принят", color = Color.Green, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    Text("Пассажир: ${order.passengerName}")
                    Text(
                        "Телефон: ${order.passengerPhone}",
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
        // ←←←←←←←←←←←←←←←←←←←←←←←←
    }

   // АКТИВНЫЙ ЗАКАЗ ДЛЯ ВОДИТЕЛЯ
    val activeOrderManager = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            AppModule.ApiProvider::class.java
        )
            .activeOrderManager()
    }
    val activeDriverOrder by activeOrderManager.activeOrderDriver.collectAsState()

    // Загружаем активный заказ при запуске
    LaunchedEffect(Unit) {
        activeOrderManager.loadActiveOrderForDriver()
    }

    LaunchedEffect(activeDriverOrder) {
        activeDriverOrder?.let { order ->
            Log.d(TAG, "Активный заказ для водителя загружен: ${order.id}")

            incomingOrder = order

            // Строим маршрут
            buildRoute(order.startPointLatLon, order.endPointLatLon) { points ->
                routePolyline?.let { mapObjects?.remove(it) }
                routePolyline = mapObjects?.addPolyline(Polyline(points))
                    ?.apply { setStrokeColor(0xFF1E88E5.toInt()); strokeWidth = 8f }

                animateDriver(order.startPointLatLon, points, mapObjects, carIcon, scope)
            }

            // Запускаем SSE для этого заказа
            sseJob?.cancel()
            sseJob = launch {
                sseClient.subscribe(
                    orderId = order.id,
                    token = token!!,
                    scope = this,
                    onUpdate = { json ->
                        val status = json.optString("status")
                        when (status) {
                            "CANCELLED", "COMPLETED" -> {
                                incomingOrder = null
                                sseJob?.cancel()
                                sseJob = null
                            }
                        }
                    }
                )
            }
        }
    }

   /* Box(modifier = Modifier.fillMaxSize()) {
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
                    if (order.status == "ASSIGNED") {
                        // НОВЫЙ ЗАКАЗ — только откуда/куда + кнопка принять
                        Text(
                            "Новый заказ!",
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            color = Color(0xFFE91E63)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("Откуда: ${order.startAddress}", fontWeight = FontWeight.Medium)
                        Text("Куда: ${order.endAddress}", fontWeight = FontWeight.Medium)
                        Text(
                            "Цена: ${order.price} ₽",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )

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
                                        buildRoute(
                                            order.startPointLatLon,
                                            order.endPointLatLon
                                        ) { points ->
                                            routePolyline?.let { mapObjects?.remove(it) }
                                            routePolyline =
                                                mapObjects?.addPolyline(Polyline(points))
                                                    ?.apply {
                                                        setStrokeColor(0xFF1E88E5.toInt()); strokeWidth =
                                                        8f
                                                    }
                                            animateDriver(
                                                order.startPointLatLon,
                                                points,
                                                mapObjects,
                                                carIcon,
                                                scope
                                            )
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
                        Text(
                            "Заказ принят",
                            color = Color.Green,
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("Пассажир: ${order.passengerName}")
                        Text(
                            text = "Телефон: ${order.passengerPhone}",
                            color = Color.Blue,
                            modifier = Modifier.clickable {
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_DIAL,
                                        Uri.parse("tel:${order.passengerPhone}")
                                    )
                                )
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
    }*/

    DisposableEffect(Unit) {
        onDispose {
            newOrdersSseJob?.cancel()
            orderUpdatesSseJob?.cancel()
            sseJob?.cancel()
            sseJob = null
            Log.d(TAG, "Экран закрыт — SSE отключён")
            MapKitFactory.getInstance().onStop()
        }
    }
}

// Анимация машины
private fun animateDriver(
    start: Point, points: List<Point>,
    mapObjects: MapObjectCollection?,
    carIcon: ImageProvider,
    coroutineScope: CoroutineScope
) {
    val marker = mapObjects?.addPlacemark(start)?.apply {
        val marker = mapObjects?.addPlacemark(start)?.apply {
            setIcon(carIcon)
            zIndex = 100f
        } ?: return

        var index = 0

        coroutineScope.launch(Dispatchers.Main) {
            while (index < points.size) {
                marker.geometry = points[index]
                index += 5
                delay(80) // скорость анимации
            }
        }
    }
}

// Маршрут (простой)
private fun buildRoute(
    from: Point,
    to: Point,
    onReady: (List<Point>) -> Unit
) {
    onReady(listOf(from, to))
}