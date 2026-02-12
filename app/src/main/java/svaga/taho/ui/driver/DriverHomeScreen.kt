// DriverHomeScreen.kt
package svaga.taho.ui.driver

import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
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
import svaga.taho.R
import svaga.taho.data.local.TokenManager
import svaga.taho.data.remote.DriverOrder
import svaga.taho.di.AppModule
import svaga.taho.ui.auth.AuthViewModel
import svaga.taho.ui.client.sseJob
import svaga.taho.ui.menu.AppDrawerContentForDriver
import svaga.taho.util.playNotificationSound
import androidx.core.net.toUri
import svaga.taho.util.location.TrackManager
import java.math.BigDecimal
import java.math.RoundingMode

private const val TAG = "DriverHomeScreen"
var sseJob by mutableStateOf<Job?>(null)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverHomeScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    //Для работы Drawer
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val authViewModel: AuthViewModel = hiltViewModel()

    // СОСТОЯНИЯ
    var currentOrder by remember { mutableStateOf<DriverOrder?>(null) }
    var routePolyline by remember { mutableStateOf<PolylineMapObject?>(null) }
    var driverMarker by remember { mutableStateOf<PlacemarkMapObject?>(null) }
    var mapObjects by remember { mutableStateOf<MapObjectCollection?>(null) }
    var isArrived by remember { mutableStateOf(false) }
    var isPickedUp by remember { mutableStateOf(false) }
    var driverName by remember { mutableStateOf("Загрузка...") }
    var driverStatus by remember { mutableStateOf("OFFLINE") }
    var showStatusSheet by remember { mutableStateOf(false) }
    var isTracking by remember { mutableStateOf(false) }
    var shouldTrack by remember { mutableStateOf(false) }
    var newOrdersSseJob by remember { mutableStateOf<Job?>(null) }
    var orderUpdatesSseJob by remember { mutableStateOf<Job?>(null) }
    var driverBalance by remember { mutableStateOf<BigDecimal>(BigDecimal.ZERO) }

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

    // TODO Заменить как в примере для статуса водилы на линии и т.д
    // TODO бро ты умрешь и т.д.
    val tokenManager: TokenManager = hiltViewModel<AuthViewModel>().tokenManager

    // Реактивно получаем данные
    val userName by tokenManager.nameFlow.collectAsState(initial = "Загрузка...")
    val userPhone by tokenManager.phoneFlow.collectAsState(initial = "Загрузка...")

    val (statusColor, statusText, statusClickable) = when (driverStatus) {
        "AVAILABLE" -> Triple(Color(0xFF4CAF50), "На линии", true)
        "OFFLINE" -> Triple(Color(0xFF9E9E9E), "Отдых", true)
        "BUSY", "ASSIGNED", "IN_PROGRESS" -> Triple(Color(0xFFFF9800), "В заказе", false)
        else -> Triple(Color(0xFF9E9E9E), "Неизвестно", false)
    }

    val apiService = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            AppModule.ApiProvider::class.java
        ).apiService()
    }

    val carIcon = ImageProvider.fromResource(context, R.drawable.ic_car_driver)

    // ВСПОМОГАТЕЛЬНАЯ ФУНКЦИЯ — строит маршрут и анимацию
    fun setupOrder(order: DriverOrder) {
        val startPointLatLon = order.startPoint.split(",").let { Point(it[0].toDouble(), it[1].toDouble()) }
        val endPointLatLon = order.endPoint.split(",").let { Point(it[0].toDouble(), it[1].toDouble()) }
        buildRoute(startPointLatLon, endPointLatLon) { points ->
            routePolyline?.let { mapObjects?.remove(it) }
            routePolyline = mapObjects?.addPolyline(Polyline(points))
                ?.apply { setStrokeColor(0xFF1E88E5.toInt()); strokeWidth = 8f }
            animateDriver(startPointLatLon, points, mapObjects, carIcon, scope)
        }
    }

    // 1. ПОДПИСКА НА НОВЫЕ ЗАКАЗЫ — ВСЕГДА!
    LaunchedEffect(token) {
        if (token.isNotEmpty()) {
            Log.d(TAG, "LaunchedEffect(token) сработал → token не пустой")
            newOrdersSseJob?.cancel()
            Log.d(TAG, "Старая newOrdersSseJob отменена (если была)")
            newOrdersSseJob = scope.launch {
                Log.d(TAG, "СТАРТ ПОДПИСКИ НА DRIVER — job запущена")
                try {
                    sseClient.subscribe(
                        orderId = "driver",
                        token = token,
                        scope = this,
                        onUpdate = { json ->
                            Log.d(TAG, "!!! SSE DRIVER RAW !!! → $json") // ← самый важный лог
                            val parsedStatus = try {
                                json.optString("status", "UNKNOWN")
                            } catch (e: Exception) {
                                Log.e(TAG, "Ошибка парсинга json в driver SSE", e)
                                return@subscribe
                            }
                            Log.d(TAG, "Получен статус в driver: $parsedStatus | currentOrder = ${currentOrder?.id} (${currentOrder?.status})")
                            if (currentOrder?.status in listOf("ACCEPTED", "PICKED_UP", "ARRIVED", "IN_PROGRESS")) {
                                Log.d(TAG, "Игнорируем новый заказ — активный уже есть")
                                return@subscribe
                            }
                            Log.d(TAG, "Обрабатываем новый заказ как свободный!")
                            // дальше весь твой код парсинга DriverOrder
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
                                status = "ASSIGNED",
                                inCity = json.optBoolean("inCity", true)
                            )
                            currentOrder = order
                            shouldTrack = !order.inCity
                            playNotificationSound(context)
                        }
                    )
                    Log.d(TAG, "Подписка на driver запущена успешно")
                } catch (e: Exception) {
                    Log.e(TAG, "Критическая ошибка в подписке driver", e)
                }
            }
            Log.d(TAG, "newOrdersSseJob присвоена новая корутина")
        } else {
            Log.w(TAG, "Token пустой — подписка на driver НЕ запущена")
        }
    }

    // 2. ЗАГРУЗКА АКТИВНОГО ЗАКАЗА
    val activeOrderManager = remember {
        EntryPointAccessors.fromApplication(context, AppModule.ApiProvider::class.java)
            .activeOrderManager()
    }
    val activeDriverOrder by activeOrderManager.activeOrderDriver.collectAsState()

    LaunchedEffect(Unit) {
        activeOrderManager.loadActiveOrderForDriver()
    }

    LaunchedEffect(activeDriverOrder) {
        activeDriverOrder?.let { order ->
            Log.d(TAG, "Активный заказ загружен: ${order.id}, статус: ${order.status}")
            currentOrder = order.copy(
                status = when (order.status) {
                    "ASSIGNED" -> "ASSIGNED"
                    else -> "ACCEPTED"
                }
            )
            setupOrder(order)
        }
    }
    // Функция загрузки профиля
    suspend fun loadDriverProfile() {
        try {
            val profile = apiService.getDriverProfile("Bearer $token")
            driverName = userName ?: "Имя не указано"
            driverStatus = profile.status
            tokenManager.saveDriverId(profile.driverId)
            driverBalance = profile.balance  // ← берём баланс
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка загрузки профиля", e)
            driverName = "Ошибка получения имени"
            driverStatus = "СИСИ ПИСЬКИ ВРЫЗВ ПИПИСЬКИ"
        }
    }
    // ФУНКЦИЯ ДЛЯ ПРИНЯТИЯ ЗАКАЗА
    val acceptOrder: () -> Unit = {
        currentOrder?.let { order ->
            scope.launch {
                try {
                    loadDriverProfile()
                    apiService.acceptOrder("Bearer $token", order.id)
                    currentOrder = order.copy(status = "ACCEPTED")
                    // Запускаем SSE для конкретного заказа
                    orderUpdatesSseJob?.cancel()
                    orderUpdatesSseJob = scope.launch {
                        sseClient.subscribe(
                            orderId = order.id,
                            token = token,
                            scope = this,
                            onUpdate = { json ->
                                val status = json.optString("status")
                                when (status) {
                                    "CANCELLED", "COMPLETED" -> {
                                        currentOrder = null
                                        isArrived = false
                                        isPickedUp = false
                                        isTracking = false
                                        shouldTrack = false
                                        scope.launch {
                                            delay(500) // небольшая задержка, чтобы избежать гонки
                                            loadDriverProfile()
                                        }
                                        orderUpdatesSseJob?.cancel()
                                        Log.d(TAG, "Заказ $status → вернулся в режим ожидания новых заказов")
                                        // Принудительный перезапуск подписки на новые заказы
                                        scope.launch {
                                            delay(500)
                                            Log.d(TAG, "ПРИНУДИТЕЛЬНЫЙ ПЕРЕЗАПУСК ПОДПИСКИ DRIVER ПОСЛЕ ЗАВЕРШЕНИЯ")
                                            newOrdersSseJob?.cancel()
                                            newOrdersSseJob = scope.launch {
                                                Log.d(TAG, "Перезапуск: job на driver создана заново")
                                                sseClient.subscribe(
                                                    orderId = "driver",
                                                    token = token,
                                                    scope = this,
                                                    onUpdate = { json ->
                                                        Log.d(TAG, "!!! SSE DRIVER RAW (restart) !!! → $json")
                                                        val parsedStatus = try {
                                                            json.optString("status", "UNKNOWN")
                                                        } catch (e: Exception) {
                                                            Log.e(TAG, "Ошибка парсинга json в driver SSE (restart)", e)
                                                            return@subscribe
                                                        }
                                                        Log.d(TAG, "Получен статус в driver (restart): $parsedStatus | currentOrder = ${currentOrder?.id} (${currentOrder?.status})")
                                                        if (currentOrder?.status in listOf("ACCEPTED", "PICKED_UP", "ARRIVED", "IN_PROGRESS")) {
                                                            Log.d(TAG, "Игнорируем новый заказ — активный уже есть (restart)")
                                                            return@subscribe
                                                        }
                                                        Log.d(TAG, "Обрабатываем новый заказ как свободный! (restart)")
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
                                                            status = "ASSIGNED",
                                                            inCity = json.optBoolean("inCity", true)
                                                        )
                                                        currentOrder = order
                                                        shouldTrack = !order.inCity
                                                        playNotificationSound(context)
                                                    }
                                                )
                                            }
                                        }
                                    }
                                    "ARRIVED" -> {
                                        isArrived = true
                                        Log.d(TAG, "Водитель на месте (ARRIVED)")
                                    }
                                    "PICKED_UP" -> {
                                        isPickedUp = true
                                        if (shouldTrack && !isTracking) {
                                            isTracking = true
                                            TrackManager.startTracking(
                                                context = context,
                                                scope = scope,
                                                onPointAdded = { point ->
                                                    Log.d(TAG, "Точка трека добавлена: ${point.latitude}, ${point.longitude}")
                                                },
                                                onError = { error ->
                                                    Toast.makeText(context, "Ошибка трекинга: $error", Toast.LENGTH_SHORT).show()
                                                }
                                            )
                                        }
                                        Log.d(TAG, "Пассажир забран (PICKED_UP)")
                                    }
                                    "IN_PROGRESS" -> {
                                        // можно что-то дополнительно, если нужно
                                        Log.d(TAG, "Поездка в процессе")
                                    }
                                    else -> {
                                        Log.d(TAG, "Необработанный статус заказа: $status")
                                    }
                                }
                            }
                        )
                    }
                    setupOrder(order)
                } catch (e: Exception) {
                    Log.e(TAG, "Ошибка принятия заказа", e)
                    Toast.makeText(context, "Не удалось принять заказ", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    LaunchedEffect(token) {
        loadDriverProfile()
    }

    // Функция смены статуса
    suspend fun toggleStatus() {
        if (driverStatus in listOf("BUSY", "ASSIGNED", "IN_PROGRESS")) {
            Log.d(TAG, "Нельзя менять статус во время заказа: $driverStatus")
            return
        }
        val response = apiService.toggleOnlineStatus("Bearer $token")
        if (response.isSuccessful) {
            val newStatus = response.body()?.string()?.trim() ?: driverStatus
            driverStatus = newStatus
        }
    }

    LaunchedEffect(currentOrder) {
        Log.d(TAG, "currentOrder изменился → ${currentOrder?.id} / ${currentOrder?.status}")
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = false,
        drawerContent = {
            AppDrawerContentForDriver(
                navController = navController,
                authViewModel = authViewModel,
                name = userName ?: "Имя не указано",
                phone = userPhone ?: "Номера нема",
                onCloseDrawer = { scope.launch { drawerState.close() } }
            )
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Taho Driver") },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    }
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
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

                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                        .clickable(enabled = statusClickable) { showStatusSheet = true }
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = statusText,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        if (driverBalance > BigDecimal.ZERO) {
                            Text(
                                text = "${driverBalance.setScale(0, RoundingMode.HALF_UP)} ₽",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                }

                // ОДНО ОКНО — В ЗАВИСИМОСТИ ОТ СТАТУСА
                currentOrder?.let { order ->
                    Card(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (order.status == "ASSIGNED") Color(0xFFE91E63) else Color.White
                        ),
                        elevation = CardDefaults.cardElevation(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            if (order.status == "ASSIGNED") { // ← НОВЫЙ ЗАКАЗ
                                Text(
                                    "Новый заказ!",
                                    color = Color.White,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(12.dp))
                                Text("Откуда: ${order.startAddress}", color = Color.White)
                                Text("Куда: ${order.endAddress}", color = Color.White)
                                Text(
                                    "Цена: ${order.price} ₽",
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(20.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    Button(
                                        onClick = acceptOrder,
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Принять", color = Color(0xFFE91E63))
                                    }
                                    OutlinedButton(
                                        onClick = { currentOrder = null },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Отклонить", color = Color.White)
                                    }
                                }
                            } else { // ← АКТИВНЫЙ ЗАКАЗ
                                Text("Заказ принят",
                                    color = Color.Green,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(12.dp))
                                Text("Пассажир: ${order.passengerName}")
                                Text("Телефон: ${order.passengerPhone}",
                                    color = Color.Blue,
                                    modifier = Modifier.clickable {
                                        context.startActivity(
                                            Intent(
                                                Intent.ACTION_DIAL,
                                                "tel:${order.passengerPhone}".toUri()
                                            )
                                        )
                                    }
                                )
                                Text("Откуда: ${order.startAddress}")
                                Text("Куда: ${order.endAddress}")
                                Spacer(Modifier.height(16.dp))
                                when {
                                    isPickedUp -> {
                                        Button(
                                            onClick = {
                                                scope.launch {
                                                    try {
                                                        val trackJson = if (shouldTrack) {
                                                            TrackManager.stopTrackingAndGetJson()
                                                        } else {
                                                            "[]" // null, если бэк разрешает
                                                        }
                                                        val response = apiService.driverComplete(
                                                            "Bearer $token",
                                                            order.id,
                                                            trackJson // ← отправляем собранный трек
                                                        )
                                                        if (response.isSuccessful) {
                                                            Toast.makeText(
                                                                context,
                                                                "Поездка завершена, трек отправлен",
                                                                Toast.LENGTH_SHORT
                                                            ).show()
                                                            Log.d(TAG, "Поездка завершена, трек отправлен $trackJson")
                                                            loadDriverProfile()

                                                        } else {
                                                            Toast.makeText(
                                                                context,
                                                                "Ошибка отправки трека",
                                                                Toast.LENGTH_LONG
                                                            ).show()
                                                            Log.e(TAG, "оШИБКА ОТПРАВКИ ТРЕК")
                                                        }

                                                        currentOrder = null
                                                        isArrived = false
                                                        isPickedUp = false
                                                        isTracking = false
                                                        shouldTrack = false

                                                        // Можно сбросить состояние или ждать COMPLETED от SSE
                                                    } catch (e: Exception) {
                                                        Log.e(TAG, "Ошибка завершения заказа", e)
                                                    }
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFFE91E63)
                                            )
                                        ) {
                                            Text(
                                                "Завершить заказ",
                                                color = Color.White,
                                                fontSize = 18.sp
                                            )
                                        }
                                    }
                                    isArrived -> {
                                        Button(
                                            onClick = {
                                                scope.launch {
                                                    try {
                                                        apiService.driverPickedUp(
                                                            "Bearer $token",
                                                            order.id
                                                        )
                                                        isPickedUp = true
                                                        if (shouldTrack && !isTracking) {
                                                            isTracking = true
                                                            Log.d(TAG, "Трекинг нужен (inCity = false)")
                                                            TrackManager.startTracking(
                                                                context = context,
                                                                scope = scope,
                                                                onPointAdded = { point ->
                                                                    Log.d(TAG, "Точка добавлена: ${point.latitude}, ${point.longitude}")
                                                                },
                                                                onError = { error ->
                                                                    Toast.makeText(context, "Ошибка трека: $error", Toast.LENGTH_SHORT).show()
                                                                }
                                                            )
                                                        } else {
                                                            Log.d(TAG, "Трекинг не нужен (inCity = true)")
                                                        }
                                                    } catch (e: Exception) {
                                                        Log.e(TAG, "Ошибка забора пассажира", e)
                                                    }
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFFFF9800)
                                            )
                                        ) {
                                            Text("Забрал пассажира")
                                        }
                                    }
                                    else -> {
                                        Button(
                                            onClick = {
                                                scope.launch {
                                                    try {
                                                        apiService.driverArrived(
                                                            "Bearer $token",
                                                            order.id
                                                        )
                                                        isArrived = true
                                                    } catch (e: Exception) {
                                                        Log.e(TAG, "Ошибка прибытия", e)
                                                    }
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Я на месте")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (showStatusSheet) {
                    DriverStatusBottomSheet(
                        driverName = driverName,
                        driverStatus = driverStatus,
                        onToggleStatus = { toggleStatus() },
                        onDismiss = { showStatusSheet = false },
                        driverBalance = driverBalance
                    )
                }
            }
        }
    }

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
    start: Point,
    points: List<Point>,
    mapObjects: MapObjectCollection?,
    carIcon: ImageProvider,
    coroutineScope: CoroutineScope
) {
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

// Маршрут (простой)
private fun buildRoute(
    from: Point,
    to: Point,
    onReady: (List<Point>) -> Unit
) {
    onReady(listOf(from, to))
}