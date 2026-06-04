// DriverHomeScreen.kt
package svaga.taho.ui.driver

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
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
import svaga.taho.data.local.TokenManager
import svaga.taho.data.remote.DriverOrder
import svaga.taho.di.AppModule
import svaga.taho.ui.auth.AuthViewModel
import svaga.taho.ui.menu.AppDrawerContentForDriver
import androidx.core.net.toUri
import com.yandex.mapkit.geometry.BoundingBox
import com.yandex.mapkit.search.SearchFactory
import com.yandex.mapkit.search.SearchManagerType
import com.yandex.mapkit.search.SuggestItem
import com.yandex.mapkit.search.SuggestOptions
import com.yandex.mapkit.search.SuggestResponse
import com.yandex.mapkit.search.SuggestSession
import svaga.taho.util.WaitingState
import svaga.taho.util.location.TrackManager
import java.math.BigDecimal
import java.math.RoundingMode
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.foundation.lazy.items
import svaga.taho.data.remote.CreateOrderRequest
import svaga.taho.service.TahoSseService
import svaga.taho.ui.components.CallOperatorButton
import svaga.taho.util.adaptiveDp
import svaga.taho.util.adaptiveSp
import svaga.taho.util.playRepeatingNotificationSound
import svaga.taho.util.stopNotificationSound

private const val TAG = "DriverHomeScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverHomeScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    //Для работы Drawer
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val authViewModel: AuthViewModel = hiltViewModel()

    val waitingTimerManager = remember {
        EntryPointAccessors.fromApplication(context.applicationContext, AppModule.ApiProvider::class.java).waitingTimerManager()
    }

    val driverViewModel: DriverViewModel = hiltViewModel()
    val currentOrder by driverViewModel.currentOrder.collectAsState()
    val isArrived by driverViewModel.isArrived.collectAsState()
    val isPickedUp by driverViewModel.isPickedUp.collectAsState()
    val isTracking by driverViewModel.isTracking.collectAsState()
    val shouldTrack by driverViewModel.shouldTrack.collectAsState()
    val waitingState by waitingTimerManager.state.collectAsState()
    val savedPaidWaitingMinutes by driverViewModel.savedPaidWaitingMinutes.collectAsState()
    val showRejected by driverViewModel.showRejected.collectAsState()
    val shouldCloseAssignedCard by driverViewModel.shouldCloseAssignedCard.collectAsState()
    val completedPrice by driverViewModel.completedPrice.collectAsState()




    // СОСТОЯНИЯ
    var routePolyline by remember { mutableStateOf<PolylineMapObject?>(null) }
    var driverMarker by remember { mutableStateOf<PlacemarkMapObject?>(null) }
    var mapObjects by remember { mutableStateOf<MapObjectCollection?>(null) }
    var driverName by remember { mutableStateOf("Загрузка...") }
    var driverStatus by remember { mutableStateOf("OFFLINE") }
    var showStatusSheet by remember { mutableStateOf(false) }
    var newOrdersSseJob by remember { mutableStateOf<Job?>(null) }
    var orderUpdatesSseJob by remember { mutableStateOf<Job?>(null) }

    var showCreateOrderSheet by remember { mutableStateOf(false) }
    var createFromAddress by remember { mutableStateOf("") }
    var createToAddress by remember { mutableStateOf("") }
    var createFromPoint by remember { mutableStateOf<Point?>(null) }
    var createToPoint by remember { mutableStateOf<Point?>(null) }
    var createFromSuggestions by remember { mutableStateOf<List<SuggestItem>>(emptyList()) }
    var createToSuggestions by remember { mutableStateOf<List<SuggestItem>>(emptyList()) }
    var createFocusedField by remember { mutableStateOf<String?>(null) }
    var isCreatingOrder by remember { mutableStateOf(false) }
    var queuePosition by remember { mutableIntStateOf(0) }
    var parkName by remember { mutableStateOf<String?>(null) }

    var showTimeToArriveDialog by remember { mutableStateOf(false) }
    var pendingOrder by remember { mutableStateOf<DriverOrder?>(null) }

    val zaezdCount by driverViewModel.zaezdCount.collectAsState()
    var showZaezdConfirmDialog by remember { mutableStateOf(false) }
    var showCompleteConfirmDialog by remember { mutableStateOf(false) }



    val createSuggestSession = remember {
        SearchFactory.getInstance()
            .createSearchManager(SearchManagerType.COMBINED)
            .createSuggestSession()
    }
    val sseEventBus = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            AppModule.ApiProvider::class.java
        ).sseEventBus()
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
        "AVAILABLE" -> {
            val color = when (parkName) {           // или profile.parkId, если удобнее
                "Черема" -> Color(0xFF2196F3)       // Синий для parkId = 1
                "Город"  -> Color(0xFF4CAF50)       // Зелёный для parkId = 2
                else     -> Color(0xFF4CAF50)       // по умолчанию зелёный
            }
            Triple(color, "На линии", true)
        }


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





/**  val carIcon = ImageProvider.fromResource(context, R.drawable.ic_car_driver)

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
 */
    /**
     * Открывает Яндекс.Карты с маршрутом до нужной точки.
     * [point] — строка в формате "lat,lon" (как хранится в DriverOrder)
     * [label] — подпись точки назначения в приложении
     */
    @RequiresApi(Build.VERSION_CODES.DONUT)
    fun openYandexMapsNavigation(context: Context, order: DriverOrder, isPickedUp: Boolean) {
        val (sLat, sLon) = order.startPoint.split(",").map { it.trim() }
        val (eLat, eLon) = order.endPoint.split(",").map { it.trim() }

        val routeUri = if (!isPickedUp) {
            // ~ означает "от текущей геопозиции" — Яндекс берёт GPS сам
            "yandexmaps://maps.yandex.ru/?rtext=~$sLat,$sLon&rtt=auto&z=14"
        } else {
            // Явный маршрут: место посадки → конечная точка
            "yandexmaps://maps.yandex.ru/?rtext=$sLat,$sLon~$eLat,$eLon&rtt=auto&z=14"
        }

        val intent = Intent(Intent.ACTION_VIEW, routeUri.toUri()).apply {
            setPackage("ru.yandex.yandexmaps")
        }

        // Fallback если Яндекс.Карты не установлены
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        } else {
            val webUri = routeUri
                .replace("yandexmaps://maps.yandex.ru/", "https://maps.yandex.ru/")
                .toUri()
            context.startActivity(Intent(Intent.ACTION_VIEW, webUri))
        }
    }

    suspend fun loadDriverProfile() {
        try {
            val profile = apiService.getDriverProfile("Bearer $token")
            driverName = userName ?: "Имя не указано"
            driverStatus = profile.status
            tokenManager.saveDriverId(profile.driverId)
            parkName = when (profile.parkId) {
                1 -> "Черема"
                2 -> "Город"
                else -> null
            }
            queuePosition = profile.numberInLine
            Log.d(TAG, "Номер в очереди $queuePosition")
            if (profile.status == "OFFLINE" || profile.parkId == null) {
                parkName = null
            }

        } catch (e: Exception) {
            Log.e(TAG, "Ошибка загрузки профиля", e)
            driverName = "Ошибка получения имени"
            driverStatus = "Неверный статус"
        }
    }

    // 1. ПОДПИСКА НА НОВЫЕ ЗАКАЗЫ — ВСЕГДА!
    // Читаем новые заказы и обновления из шины — сервис пишет сюда
    // 1. ПОДПИСКА НА НОВЫЕ ЗАКАЗЫ — ВСЕГДА!
    LaunchedEffect(Unit) {
        sseEventBus.events.collect { json ->
            val myPos = json.optInt("myPosition", -1)
            if (myPos >= 0) {
                queuePosition = myPos
                Log.d(TAG, "Позиция в очереди: $myPos")
                return@collect
            }

            val status = json.optString("status", "UNKNOWN")
            Log.d(TAG, "=== SSE EVENT === status: $status | currentOrder.id=${currentOrder?.id} | currentOrder.status=${currentOrder?.status} | json=$json")

            // CANCELLED обрабатываем первым — не зависит от статуса заказа
            if (status == "CANCELLED") {
                Log.d(TAG, "Получен CANCELLED → проверяем, нужно ли закрывать заказ")
                if (currentOrder != null) {
                    Log.d(TAG, "У нас был заказ (ID: ${currentOrder?.id}) → сбрасываем его")
                    stopNotificationSound()
                    driverViewModel.resetOrderStates()
                    return@collect
                }
            }

            // Если есть активный заказ — обновление его статуса
            if (currentOrder?.status in listOf("ACCEPTED", "PICKED_UP", "ARRIVED", "IN_PROGRESS")) {
                Log.d(TAG, "Обработка как обновление активного заказа")
                when (status) {
                    "COMPLETED" -> {
                        Log.d(TAG, "Завершение заказа → полный сброс")
                        val price = json.optString("price").takeIf { it.isNotBlank() && it != "null" }
                        if (price != null) {
                            driverViewModel.showCompletedPrice(price)
                        }
                        driverViewModel.resetOrderStates()
                        stopNotificationSound()


                        scope.launch {
                            delay(1000)
                            loadDriverProfile()
                            TahoSseService.start(context, orderId = "driver", role = "DRIVER")
                        }

                    }
                    "CANCELLED" -> {
                        Log.d(TAG, "Статус CANCELLED получен → закрываем карту принятия заказа")
                        // Если заказ в статусе ASSIGNED — это значит его взял другой водитель
                        if (currentOrder?.status == "ASSIGNED") {
                            Log.d(TAG, "Заказ был ASSIGNED (ID: ${currentOrder?.id}) → закрываем его полностью")
                            stopNotificationSound()
                            driverViewModel.setCurrentOrder(null)  // ← Сразу сбрасываем заказ
                            driverViewModel.resetOrderStates()
                        }
                    }
                    "ARRIVED" -> driverViewModel.setArrived(true)
                    "PICKED_UP" -> {
                        driverViewModel.setPickedUp(true)
                        if (shouldTrack && !isTracking) {
                            driverViewModel.setTracking(true)
                            TrackManager.startTracking(context, onPointAdded = {}, onError = {})
                        }
                    }

                    "REJECTED"              -> {
                        driverViewModel.onOrderRejected()
                        loadDriverProfile()
                    }
                }
                return@collect
            }

            // ←←← НОВЫЙ ЗАКАЗ
            if (status == "ASSIGNED" || json.has("id")) {
                Log.d(TAG, "ДЕТЕКТИРОВАН НОВЫЙ ЗАКАЗ ASSIGNED!")

                val order = runCatching {
                    DriverOrder(
                        id = json.getString("id"),
                        startPoint = json.getString("startPoint"),
                        endPoint = json.getString("endPoint"),
                        startAddress = json.getString("startAddress"),
                        endAddress = json.getString("endAddress"),
                        passengerName = json.getString("passengerName"),
                        passengerPhone = json.getString("passengerPhone"),
                        price = json.optString("price"),
                        distance = json.optString("distance"),
                        status = "ASSIGNED",
                        inCity = json.optBoolean("inCity", true)
                    )
                }.getOrNull()

                if (order == null) {
                    Log.e(TAG, "Не удалось распарсить новый заказ")
                    return@collect
                }

                Log.d(TAG, "Новый заказ успешно распарсен: ${order.id} от ${order.startAddress} → ${order.endAddress}")

                // Критично: полный сброс перед новым заказом
                driverViewModel.resetOrderStates()
                driverViewModel.setCurrentOrder(order)
                driverViewModel.setShouldTrack(!order.inCity)

                playRepeatingNotificationSound(context)
                Log.d(TAG, "✅ НОВЫЙ ЗАКАЗ УСТАНОВЛЕН В VIEWMODEL")
            }
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

    LaunchedEffect(isArrived) {
        if (isArrived && !isPickedUp) {
            waitingTimerManager.onArrived(scope)
        }
    }

    LaunchedEffect(isPickedUp) {
        if (isPickedUp) {
            val minutes = waitingTimerManager.paidEndTime.value
            Log.d(TAG, "Сохраняем платное ожидание перед сбросом: $minutes")
            driverViewModel.savePaidWaitingMinutes(minutes)
            waitingTimerManager.reset()
        }
    }

    LaunchedEffect(true) {  // true — константа, срабатывает только один раз
        Log.d(TAG, "Первый запуск экрана — загружаем активный заказ")
        activeOrderManager.loadActiveOrderForDriver()
    }

    LaunchedEffect(activeDriverOrder?.id) {
        activeDriverOrder?.let { order ->
            Log.d(TAG, "Активный заказ загружен: ${order.id}, статус: ${order.status}")

            // Если заказ завершён или отменён на сервере — сбрасываем всё
            if (order.status in listOf("COMPLETED", "CANCELLED")) {
                driverViewModel.resetOrderStates()
                Log.d(TAG, "Сервер вернул COMPLETED/CANCELLED → полный сброс")
                return@let
            }


            // Если заказ тот же и статус не изменился — пропускаем
            if (currentOrder?.id == order.id && currentOrder?.status == order.status) {
                Log.d(TAG, "Заказ и статус уже актуальны — пропускаем")
                return@let
            }

            // Обновляем заказ и состояния
            driverViewModel.setCurrentOrder(order)
            driverViewModel.setShouldTrack(!order.inCity)
                //setupOrder(order)

            when (order.status) {
                "ARRIVED" -> {
                    driverViewModel.setArrived(true)
                    driverViewModel.setPickedUp(false)
                    driverViewModel.setTracking(false)
                    Log.d(TAG, "ARRIVED → isArrived = true")
                }

                "PICKED_UP" -> {
                    driverViewModel.setArrived(true)
                    driverViewModel.setPickedUp(true)
                    driverViewModel.setTracking(shouldTrack)
                    Log.d(TAG, "PICKED_UP → isPickedUp = true, трекинг = $shouldTrack")
                }

                "IN_PROGRESS" -> {
                    driverViewModel.setArrived(true)
                    driverViewModel.setPickedUp(true)
                    driverViewModel.setTracking(shouldTrack)
                    Log.d(TAG, "IN_PROGRESS → все состояния активны")
                }

                "ASSIGNED", "ACCEPTED" -> {
                    driverViewModel.setArrived(false)
                    driverViewModel.setPickedUp(false)
                    driverViewModel.setTracking(false)
                    Log.d(TAG, "ASSIGNED/ACCEPTED → сброс состояний")
                    stopNotificationSound()
                }

                "REJECTED"              -> {
                    driverViewModel.onOrderRejected()
                    activeOrderManager.clear()
                    loadDriverProfile()
                    return@LaunchedEffect
                }

                else -> {
                    Log.w(TAG, "Неизвестный статус: ${order.status} — оставляем как есть")
                    // НЕ сбрасываем автоматически — ждём SSE или ручного сброса
                }
            }
        } ?: run {
            Log.d(TAG, "Активный заказ null от менеджера")
            // Сбрасываем только если локально заказ ещё есть
            if (currentOrder != null) {
                Log.d(TAG, "Локально заказ был — сбрасываем")
                driverViewModel.resetOrderStates()
            }
        }
    }

    LaunchedEffect(createFromAddress, createFocusedField) {
        val box = BoundingBox(Point(41.0, 19.0), Point(74.0, 180.0))
        if (createFocusedField == "from" && createFromAddress.length > 2) {
            createSuggestSession.suggest(createFromAddress, box, SuggestOptions(), object : SuggestSession.SuggestListener {
                override fun onResponse(response: SuggestResponse) { createFromSuggestions = response.items.take(6) }
                override fun onError(error: com.yandex.runtime.Error) { createFromSuggestions = emptyList() }
            })
        } else createFromSuggestions = emptyList()
    }

    LaunchedEffect(createToAddress, createFocusedField) {
        val box = BoundingBox(Point(41.0, 19.0), Point(74.0, 180.0))
        if (createFocusedField == "to" && createToAddress.length > 2) {
            createSuggestSession.suggest(createToAddress, box, SuggestOptions(), object : SuggestSession.SuggestListener {
                override fun onResponse(response: SuggestResponse) { createToSuggestions = response.items.take(6) }
                override fun onError(error: com.yandex.runtime.Error) { createToSuggestions = emptyList() }
            })
        } else createToSuggestions = emptyList()
    }

    LaunchedEffect(currentOrder?.id, isPickedUp, shouldTrack) {
        if (currentOrder == null || !isPickedUp || !shouldTrack || !TrackManager.isTracking().not()) {
            return@LaunchedEffect
        }
        if (isPickedUp && shouldTrack && !TrackManager.isTracking()) {

            Log.d(TAG, "Экран вернулся — возобновляем трекинг")
            TrackManager.startTracking(
                context = context,
                onPointAdded = { point ->
                    Log.d(TAG, "Точка (возобновление): ${point.latitude}, ${point.longitude}")
                },
                onError = { error ->
                    Log.e(TAG, error)
                }
            )
            driverViewModel.setTracking(true)
        }
    }

    // Функция загрузки профиля

    // ФУНКЦИЯ ДЛЯ ПРИНЯТИЯ ЗАКАЗА
    val acceptOrder: (String) -> Unit = { timeToArrive ->
        currentOrder?.let { order ->
            scope.launch {
                try {
                    loadDriverProfile()
                    stopNotificationSound()
                    apiService.acceptOrder("Bearer $token", order.id, timeToArrive)
                    driverViewModel.setCurrentOrder(order.copy(status = "ACCEPTED"))

                    // Запускаем сервис на конкретный orderId — обновления придут через SseEventBus
                    TahoSseService.start(context, orderId = order.id, role = "DRIVER")

                    Log.d(TAG, "Заказ принят → сервис запущен для orderId=${order.id}")
                } catch (e: Exception) {
                    Log.e(TAG, "Ошибка принятия заказа", e)
                    Toast.makeText(context, "Не удалось принять заказ", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    LaunchedEffect(token) {
        loadDriverProfile()
        if (driverStatus != "OFFLINE") {
            TahoSseService.start(context, orderId = "driver", role = "DRIVER")
        }
    }

    // Функция смены статуса
    suspend fun toggleStatus(parkId: Int? = null) {
        if (driverStatus in listOf("BUSY", "ASSIGNED", "IN_PROGRESS")) {
            Log.d(TAG, "Нельзя менять статус во время заказа: $driverStatus")
            return
        }

        try {
            val response = apiService.toggleOnlineStatus(
                token     = "Bearer $token",
                parkingId = parkId
            )

            if (response.isSuccessful) {
                val newStatus = response.body()?.string()?.trim()
                if (newStatus != null) {
                    driverStatus = newStatus
                    Log.d(TAG, "Статус изменён на: $newStatus")

                    if (newStatus == "OFFLINE") {
                        queuePosition = 0
                        parkName = null
                        TahoSseService.stop(context)  // ← останавливаем сервис
                    } else {
                        // Вышел на линию — запускаем сервис для получения новых заказов
                        TahoSseService.start(context, orderId = "driver", role = "DRIVER")
                    }
                }
                delay(500)
                loadDriverProfile()
            } else {
                Log.e(TAG, "Ошибка сервера: ${response.code()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Исключение при toggleStatus", e)
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
                    title = { },
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
                            mapWindow.map.move(CameraPosition(Point(48.0397, 38.7697), 12f, 0f, 0f))
                            mapWindow.map.mapObjects
                            // Устанавливаем русский язык для подписей
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
                        .padding(16.adaptiveDp())
                        .clip(CircleShape)
                        .background(statusColor)
                        .clickable(enabled = statusClickable) { showStatusSheet = true }
                        .padding(horizontal = 35.adaptiveDp(), vertical = 8.adaptiveDp())
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = statusText,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.adaptiveSp()
                        )

                        when {
                            currentOrder != null -> {
                                Text(text = "🚗 В заказе", color = Color.White.copy(alpha = 0.8f), fontSize = 12.adaptiveSp())
                            }
                            queuePosition > 0 -> {
                                Text(
                                    text = "Очередь: $queuePosition",
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 13.adaptiveSp(),
                                    fontWeight = FontWeight.Medium
                                )
                                parkName?.let {
                                    Text(
                                        text = it,
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontSize = 12.adaptiveSp()
                                    )
                                }
                            }
                        }
                    }



                }

                // ОДНО ОКНО — В ЗАВИСИМОСТИ ОТ СТАТУСА
                currentOrder?.let { order ->

                    Card(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(16.adaptiveDp()),
                        colors = CardDefaults.cardColors(
                            containerColor = if (order.status == "ASSIGNED") Color(0xFFE91E63) else Color.White
                        ),
                        elevation = CardDefaults.cardElevation(12.adaptiveDp())
                    ) {
                        if (showRejected) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3F3)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.adaptiveDp()),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        "Заказ отклонён менеджером",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 20.adaptiveSp(),
                                        color = Color(0xFFE53935)
                                    )
                                    Spacer(Modifier.height(8.adaptiveDp()))
                                    Text(
                                        "Ваш заказ был отменён менеджером. Для уточнения деталей свяжитесь с оператором.",
                                        fontSize = 14.adaptiveSp(),
                                        color = Color.Gray
                                    )
                                    Spacer(Modifier.height(20.adaptiveDp()))
                                    Button(
                                        onClick = {
                                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:+71234567890"))  //TODO Исправить везде номер телефона манагера
                                            context.startActivity(intent)
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF757575))
                                    ) {
                                        Text("Связаться с оператором", color = Color.White)
                                    }
                                    Spacer(Modifier.height(8.adaptiveDp()))
                                    Button(
                                        onClick = {
                                            driverViewModel.dismissRejected()
                                            driverViewModel.resetOrderStates()
                                            stopNotificationSound()
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5))
                                    ) {
                                        Text("Закрыть", color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            return@Card
                        }
                        Column(modifier = Modifier.padding(20.adaptiveDp())) {
                            if (order.status == "ASSIGNED" ) { // ← НОВЫЙ ЗАКАЗ
                                Text(
                                    "Новый заказ!",
                                    color = Color.White,
                                    fontSize = 24.adaptiveSp(),
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(12.adaptiveDp()))
                                Text("Откуда: ${order.startAddress}", color = Color.White)
                                Text("Куда: ${order.endAddress}", color = Color.White)
                                Log.d(TAG, "Цена за поездку ==== ${order.price}")
                                Text(
                                    text = if (order.price.isNullOrBlank() || order.price == "null") {
                                        "Цена: по таксометру"
                                    } else {
                                        "Цена: ${order.price} ₽"
                                    },
                                    color = Color.White,
                                    fontSize = 20.adaptiveSp(),
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(20.adaptiveDp()))
                                Row(horizontalArrangement = Arrangement.spacedBy(16.adaptiveDp())) {
                                    Button(
                                        onClick = {
                                            stopNotificationSound()
                                            pendingOrder = order
                                            showTimeToArriveDialog = true
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Принять", color = Color(0xFFE91E63))
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            scope.launch {
                                                try {
                                                    stopNotificationSound()
                                                    apiService.cancelOrder("Bearer $token", order.id)
                                                } catch (e: Exception) {

                                                    stopNotificationSound()
                                                    Log.e(TAG, "Ошибка отклонения заказа", e)
                                                }
                                                driverViewModel.setCurrentOrder(null)
                                                driverViewModel.resetOrderStates()
                                                loadDriverProfile()
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Отклонить", color = Color.White)
                                    }
                                }
                            } else { // ← АКТИВНЫЙ ЗАКАЗ
                                Text("Заказ принят",
                                    color = Color.Green,
                                    fontSize = 22.adaptiveSp(),
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(5.adaptiveDp()))
                                Text("Пассажир: ${order.passengerName}")
                                /** Text("Телефон: ${order.passengerPhone}",
                                    color = Color.Blue,
                                    modifier = Modifier.clickable {
                                        context.startActivity(
                                            Intent(
                                                Intent.ACTION_DIAL,
                                                "tel:${order.passengerPhone}".toUri()
                                            )
                                        )
                                    }
                                ) */
                                Text("Откуда: ${order.startAddress}")
                                Text("Куда: ${order.endAddress}")

                                Spacer(Modifier.height(4.adaptiveDp()))

                                Button(
                                    onClick = {
                                        openYandexMapsNavigation(
                                            context = context,
                                            order = order,
                                            isPickedUp = isPickedUp
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFFC3F1D) // фирменный красный Яндекса
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LocationOn,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.adaptiveDp())
                                    )
                                    Spacer(Modifier.width(4.adaptiveDp()))
                                    Text(
                                        text = if (!isPickedUp) "Навигация к пассажиру" else "Навигация к цели",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Spacer(Modifier.height(4.adaptiveDp()))
                                when {
                                    isPickedUp -> {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "Заездов: $zaezdCount",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.adaptiveSp(),
                                                color = Color(0xFF1E88E5)
                                            )
                                            Button(
                                                onClick = { showZaezdConfirmDialog = true },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5))
                                            ) {
                                                Text("+1 заезд", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.adaptiveSp())
                                            }
                                        }

                                        Spacer(Modifier.height(4.adaptiveDp()))

                                        Button(
                                            onClick = { showCompleteConfirmDialog = true },
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE91E63))
                                        ) {
                                            Text("Завершить заказ", color = Color.White, fontSize = 18.adaptiveSp())
                                        }
                                    }
                                    isArrived -> {
                                        when (val ws = waitingState) {
                                            is WaitingState.FreeWaiting -> {
                                                val mins = ws.secondsLeft / 60
                                                val secs = ws.secondsLeft % 60
                                                Text(
                                                    text = "Бесплатное ожидание: %02d:%02d".format(mins, secs),
                                                    color = Color(0xFF4CAF50),
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 16.adaptiveSp()
                                                )
                                            }
                                            is WaitingState.PaidWaiting -> {
                                                val mins = ws.secondsElapsed / 60
                                                val secs = ws.secondsElapsed % 60
                                                Column {
                                                    Text(
                                                        text = "Платное ожидание: %02d:%02d".format(mins, secs),
                                                        color = Color(0xFFFF9800),
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 16.adaptiveSp()
                                                    )
                                                    Text(
                                                        text = "Максимум 15 минут",
                                                        color = Color.Gray,
                                                        fontSize = 12.adaptiveSp()
                                                    )
                                                }
                                            }
                                            is WaitingState.Expired -> {
                                                LaunchedEffect(Unit) {
                                                    try {
                                                        val endTime = waitingTimerManager.paidEndTime.value
                                                        // apiService.driverComplete("Bearer $token", order.id, trackJson, paidEndTime)
                                                        driverViewModel.resetOrderStates()
                                                        waitingTimerManager.reset()
                                                    } catch (e: Exception) {
                                                        Log.e(TAG, "Ошибка автозавершения по таймеру", e)
                                                    }
                                                }
                                                Text(
                                                    text = "Ожидание истекло — заказ завершается",
                                                    color = Color.Red,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 16.adaptiveSp()
                                                )
                                            }
                                            else -> {}
                                        }

                                        Spacer(Modifier.height(5.adaptiveDp()))
                                        Button(
                                            onClick = {
                                                scope.launch {
                                                    try {
                                                        apiService.driverPickedUp(
                                                            "Bearer $token",
                                                            order.id
                                                        )
                                                        driverViewModel.setPickedUp(true)

                                                        if (shouldTrack && !isTracking) {
                                                            driverViewModel.setTracking(true)
                                                            Log.d(TAG, "Трекинг запущен вручную (кнопка «Забрал пассажира»)")

                                                            // ← НОВЫЙ ВЫЗОВ (без scope!)
                                                            TrackManager.startTracking(
                                                                context = context,
                                                                onPointAdded = { point ->
                                                                    Log.d(TAG, "Точка добавлена: ${point.latitude}, ${point.longitude}")
                                                                    // Здесь можно добавить обновление polyline на карте, если нужно
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
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
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
                                                        driverViewModel.setArrived(true)
                                                        scope.launch { waitingTimerManager.onArrived(scope) }
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

                CallOperatorButton(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.adaptiveDp())
                )
                if (currentOrder == null) {
                    FloatingActionButton(
                        onClick = { showCreateOrderSheet = true },
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.adaptiveDp()),
                        containerColor = Color(0xFF4CAF50),
                        contentColor = Color.White
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Создать заказ",
                            modifier = Modifier.size(28.adaptiveDp())
                        )
                    }
                }

// Модалка создания заказа
                if (showCreateOrderSheet) {
                    ModalBottomSheet(
                        onDismissRequest = { showCreateOrderSheet = false }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.adaptiveDp())
                                .navigationBarsPadding()
                        ) {
                            Text(
                                "Создать заказ",
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.adaptiveSp()
                            )
                            Spacer(Modifier.height(16.adaptiveDp()))

                            // Поле Откуда
                            OutlinedTextField(
                                value = createFromAddress,
                                onValueChange = { createFromAddress = it },
                                label = { Text("Откуда") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onFocusChanged { if (it.isFocused) createFocusedField = "from" },
                                singleLine = true
                            )
                            if (createFocusedField == "from" && createFromSuggestions.isNotEmpty()) {
                                LazyColumn(modifier = Modifier.heightIn(max = 200.adaptiveDp())) {
                                    items(createFromSuggestions) { item ->
                                        val text = item.displayText ?: item.title.text
                                        Text(
                                            text = text,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    createFromAddress = text
                                                    createFromPoint = item.center
                                                    createFocusedField = null
                                                }
                                                .padding(12.adaptiveDp())
                                        )
                                        HorizontalDivider()
                                    }
                                }
                            }

                            Spacer(Modifier.height(12.adaptiveDp()))

                            // Поле Куда
                            OutlinedTextField(
                                value = createToAddress,
                                onValueChange = { createToAddress = it },
                                label = { Text("Куда") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onFocusChanged { if (it.isFocused) createFocusedField = "to" },
                                singleLine = true
                            )
                            if (createFocusedField == "to" && createToSuggestions.isNotEmpty()) {
                                LazyColumn(modifier = Modifier.heightIn(max = 200.adaptiveDp())) {
                                    items(createToSuggestions) { item ->
                                        val text = item.displayText ?: item.title.text
                                        Text(
                                            text = text,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    createToAddress = text
                                                    createToPoint = item.center
                                                    createFocusedField = null
                                                }
                                                .padding(12.adaptiveDp())
                                        )
                                        HorizontalDivider()
                                    }
                                }
                            }

                            Spacer(Modifier.height(8.adaptiveDp()))

                            Button(
                                onClick = {
                                    scope.launch {
                                        isCreatingOrder = true
                                        try {
                                            val startStr = createFromPoint?.let { "${it.latitude}, ${it.longitude}" } ?: ""
                                            val endStr = createToPoint?.let { "${it.latitude}, ${it.longitude}" } ?: ""
                                            val response = apiService.createOrderByDriver(
                                                "Bearer $token",
                                                CreateOrderRequest(
                                                    startPoint = startStr,
                                                    endPoint = endStr,
                                                    startAddress = createFromAddress,
                                                    endAddress = createToAddress,
                                                    pet = null,
                                                    load = null //todo не должно быть налла
                                                )

                                            )
                                            delay(1000)

                                            if (!response.isSuccessful) {
                                                val error = response.errorBody()?.string()
                                                throw Exception("Ошибка сервера: ${response.code()} $error")
                                            }

                                            val createdOrder = response.body()
                                                ?: throw Exception("Пустой ответ сервера")

                                            Log.d(TAG, "Order created: $createdOrder")


                                            driverViewModel.setArrived(true)
                                            driverViewModel.setPickedUp(true)
                                            driverViewModel.setTracking(false)

                                            showCreateOrderSheet = false
                                            createFromAddress = ""
                                            createToAddress = ""
                                            createFromPoint = null
                                            createToPoint = null

                                            Toast.makeText(context, "Заказ создан", Toast.LENGTH_SHORT).show()

                                        } catch (e: Exception) {
                                            Log.e(TAG, "Ошибка создания заказа", e)
                                            Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                                        } finally {
                                            isCreatingOrder = false
                                        }
                                    }
                                },
                                enabled = createFromPoint != null && createToPoint != null && !isCreatingOrder,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (isCreatingOrder) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.adaptiveDp()),
                                        color = Color.White
                                    )
                                } else {
                                    Text("Создать заказ")
                                }
                            }
                        }
                    }
                }
                if (showTimeToArriveDialog) {
                    AlertDialog(
                        onDismissRequest = { showTimeToArriveDialog = false },
                        title = { Text("Время до прибытия", fontWeight = FontWeight.Bold) },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(4.adaptiveDp())) {
                                listOf("5 минут", "10 минут", "15+ минут").forEach { time ->
                                    Button(
                                        onClick = {
                                            stopNotificationSound()
                                            showTimeToArriveDialog = false
                                            acceptOrder(time)
                                            stopNotificationSound()
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5))
                                    ) {
                                        Text(time, color = Color.White)
                                    }
                                }
                            }
                        },
                        confirmButton = {},
                        dismissButton = {
                            TextButton(onClick = { showTimeToArriveDialog = false }) {
                                Text("Отмена")
                            }
                        }
                    )
                }
                if (showStatusSheet) {
                    DriverStatusBottomSheet(
                        driverName = driverName,
                        driverStatus = driverStatus,
                        onToggleStatus = { parkingId -> toggleStatus(parkingId) },
                        onDismiss = { showStatusSheet = false }
                    )
                }
                // Диалог итоговой стоимости поездки
                completedPrice?.let { price ->
                    AlertDialog(
                        onDismissRequest = { driverViewModel.dismissCompletedPrice() },
                        title = {
                            Text(
                                text = "Поездка завершена",
                                fontWeight = FontWeight.Bold
                            )
                        },
                        text = {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Стоимость поездки:",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color.Gray
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "$price ₽",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF4CAF50)
                                )
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = { driverViewModel.dismissCompletedPrice() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5))
                            ) {
                                Text("Закрыть", color = Color.White)
                            }
                        }
                    )
                }
                if (showZaezdConfirmDialog) {
                    AlertDialog(
                        onDismissRequest = { showZaezdConfirmDialog = false },
                        title = { Text("Подтверждение", fontWeight = FontWeight.Bold) },
                        text = { Text("Добавить +1 заезд? Текущее количество: $zaezdCount") },
                        confirmButton = {
                            Button(
                                onClick = {
                                    driverViewModel.incrementZaezd()
                                    showZaezdConfirmDialog = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5))
                            ) {
                                Text("Да, добавить", color = Color.White)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showZaezdConfirmDialog = false }) {
                                Text("Отмена", color = Color.Gray)
                            }
                        }
                    )
                }
                if (showCompleteConfirmDialog) {
                    AlertDialog(
                        onDismissRequest = { showCompleteConfirmDialog = false },
                        title = { Text("Завершить заказ?", fontWeight = FontWeight.Bold) },
                        text = {
                            Column {
                                Text("Вы уверены, что хотите завершить поездку?")
                                if (zaezdCount > 0) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = "Заездов: $zaezdCount",
                                        color = Color(0xFF1E88E5),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showCompleteConfirmDialog = false
                                    scope.launch {
                                        val waitingMinutes = savedPaidWaitingMinutes
                                        val zaezdValue = if (zaezdCount > 0) zaezdCount else 0
                                        try {
                                            val trackJson = if (shouldTrack) {
                                                val finalJson = TrackManager.stopTrackingAndGetJson()
                                                finalJson
                                            } else {
                                                TrackManager.stopTrackingAndGetJson()
                                                "[]"
                                            }

                                            val response = apiService.driverComplete(
                                                "Bearer $token",
                                                currentOrder?.id ?: return@launch,
                                                trackJson,
                                                downtime = waitingMinutes,
                                                zaezd = zaezdValue
                                            )

                                            if (response.isSuccessful) {
                                                Toast.makeText(context, "Поездка завершена", Toast.LENGTH_SHORT).show()
                                                TrackManager.clearTrack()
                                                driverViewModel.setTracking(false)
                                                driverViewModel.resetOrderStates() // сбросит и zaezdCount
                                                loadDriverProfile()
                                                createFromAddress = ""
                                                createToAddress = ""
                                                createFromPoint = null
                                                createToPoint = null
                                                createFocusedField = null
                                            } else {
                                                Log.e(TAG, "Ошибка driverComplete: код ${response.code()}")
                                            }
                                        } catch (e: Exception) {
                                            Log.e(TAG, "Критическая ошибка при завершении заказа", e)
                                            Toast.makeText(context, "Ошибка завершения: ${e.message}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE91E63))
                            ) {
                                Text("Завершить", color = Color.White)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showCompleteConfirmDialog = false }) {
                                Text("Отмена", color = Color.Gray)
                            }
                        }
                    )
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            newOrdersSseJob?.cancel()
            orderUpdatesSseJob?.cancel()
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