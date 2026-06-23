package svaga.taho.ui.client

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.map.*
import com.yandex.mapkit.mapview.MapView
import com.yandex.mapkit.search.*
import com.yandex.mapkit.geometry.BoundingBox
import com.yandex.runtime.Error
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import android.util.Log
import com.yandex.mapkit.map.PlacemarkMapObject
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Menu
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.delay
import svaga.taho.data.local.TokenManager
import svaga.taho.data.remote.CreateOrderRequest
import svaga.taho.di.AppModule
import svaga.taho.service.TahoSseService
import svaga.taho.ui.auth.AuthViewModel
import svaga.taho.ui.components.CallOperatorButton
import svaga.taho.ui.menu.AppDrawerContent
import svaga.taho.util.WaitingState
import svaga.taho.util.adaptiveDp
import svaga.taho.util.adaptiveSp
import kotlin.coroutines.resume
import androidx.core.net.toUri

private const val TAG = "ClientHomeScreen"


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientHomeScreen(navController: NavController) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()



    val waitingTimerManager = remember {
        EntryPointAccessors.fromApplication(context.applicationContext, AppModule.ApiProvider::class.java).waitingTimerManager()
    }
    //val waitingState by waitingTimerManager.state.collectAsState()

    val clientViewModel: ClientViewModel = hiltViewModel()
    val currentStatus by clientViewModel.currentStatus.collectAsState()
    val driverName by clientViewModel.driverName.collectAsState()
    val driverPhone by clientViewModel.driverPhone.collectAsState()
    val showOrderDetails by clientViewModel.showOrderDetails.collectAsState()
    val completionState by clientViewModel.completionState.collectAsState()
    val showCancelled by clientViewModel.showCancelled.collectAsState()
    val showRejected by clientViewModel.showRejected.collectAsState()
    val timeToArrive by clientViewModel.timeToArrive.collectAsState()



    // Основные состояния заказа
    var fromAddress by remember { mutableStateOf("Откуда") }
    var toAddress   by remember { mutableStateOf("Куда едем?") }
    var fromPoint   by remember { mutableStateOf<Point?>(null) }
    var toPoint     by remember { mutableStateOf<Point?>(null) }
    var isOrderPlaced by remember { mutableStateOf(false) }



    // Поиск по тексту (подсказки)
    var fromInput by remember { mutableStateOf("") }
    var toInput   by remember { mutableStateOf("") }
    var fromSuggestions by remember { mutableStateOf<List<SuggestItem>>(emptyList()) }
    var toSuggestions   by remember { mutableStateOf<List<SuggestItem>>(emptyList()) }
    var focusedField    by remember { mutableStateOf<String?>(null) }

    val suggestSession = remember {
        SearchFactory.getInstance()
            .createSearchManager(SearchManagerType.COMBINED)
            .createSuggestSession()
    }

    // Выбор точки на карте
   // var selectingPoint by remember { mutableStateOf<String?>(null) } // "from", "to" или null
    var fromPlacemark  by remember { mutableStateOf<PlacemarkMapObject?>(null) }
    var toPlacemark    by remember { mutableStateOf<PlacemarkMapObject?>(null) }
    val mapViewState   = remember { mutableStateOf<MapView?>(null) }
    var selectingPointMode by remember { mutableStateOf<String?>(null) }

    val searchManager = remember {
        SearchFactory.getInstance().createSearchManager(SearchManagerType.COMBINED)
    }

    //Цена заказа предварительно
    var calculatedPrice by remember { mutableStateOf<Double?>(null) }

    var hasPet by remember { mutableStateOf(false) }
    var hasLoad by remember { mutableStateOf(false) }
    var showExtraOptions by remember { mutableStateOf(false) }


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

    val authViewModel: AuthViewModel = hiltViewModel()
    val tokenManager: TokenManager = hiltViewModel<AuthViewModel>().tokenManager

    val userRole by tokenManager.roleFlow.collectAsState(initial = "Загрузка ....")
    val userName by tokenManager.nameFlow.collectAsState(initial = "Загрузка...")
    val userPhone by tokenManager.phoneFlow.collectAsState(initial = "Загрузка...")

    val activeOrderManager = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            AppModule.ApiProvider::class.java
        ).activeOrderManager()
    }
    val activeOrder by activeOrderManager.activeOrder.collectAsState()

    fun resetOrderState() {
        fromAddress = "Откуда"
        toAddress = "Куда едем?"
        fromInput = ""
        toInput = ""
        fromPoint = null
        toPoint = null
        calculatedPrice = null
        isOrderPlaced = false
        hasPet = false
        hasLoad = false
        showExtraOptions = false
        fromPlacemark?.let {
            mapViewState.value?.mapWindow?.map?.mapObjects?.remove(it)
            fromPlacemark = null
        }

        toPlacemark?.let {
            mapViewState.value?.mapWindow?.map?.mapObjects?.remove(it)
            toPlacemark = null
        }
        activeOrderManager.clear()
    }

    // Загрузка активного заказа при запуске экрана
    LaunchedEffect(Unit) {
        activeOrderManager.loadActiveOrderForClient()
    }


// ← Автоматический расчёт цены при изменении точек
    LaunchedEffect(fromPoint, toPoint) {
        if (fromPoint != null && toPoint != null && token.isNotBlank()) {

            val startStr = "${fromPoint!!.latitude}, ${fromPoint!!.longitude}"
            val endStr   = "${toPoint!!.latitude}, ${toPoint!!.longitude}"

            try {
                val api = EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    AppModule.ApiProvider::class.java
                ).apiService()

                val response = api.calculatePrice(
                    token = "Bearer $token",
                    request = mapOf(
                        "startPoint" to startStr,
                        "endPoint"   to endStr
                    )
                )

                if (response.isSuccessful) {
                    val body = response.body()
                    calculatedPrice = (body?.get("price") as? Number)?.toDouble()
                } else {
                    calculatedPrice = null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка расчёта предварительной цены", e)
                calculatedPrice = null
            }
        } else {
            calculatedPrice = null
        }
    }


    // Запуск SSE только когда есть активный заказ И он ещё не завершён
    // ← ЗАПУСК SSE + УСТАНОВКА НАЧАЛЬНОГО СОСТОЯНИЯ
    // Устанавливаем начальное состояние из загруженного активного заказа
    LaunchedEffect(activeOrder?.id, token) {
        val order = activeOrder ?: return@LaunchedEffect

        Log.d(TAG, "Активный заказ: ID=${order.id}, status=${order.status}")

        fromAddress = order.startAddress
        toAddress = order.endAddress
        clientViewModel.setShowOrderDetails(true)
        clientViewModel.setDriverInfo(order.driverName, order.driverPhone)

        when (order.status) {
            "ACCEPTED", "PICKED_UP" -> clientViewModel.setStatus("Заказ принят")
            "ARRIVED"               -> {
                clientViewModel.setStatus("Водитель на месте")
                }

            "IN_PROGRESS"           -> {
                clientViewModel.setStatus("В пути")
                clientViewModel.onTripStarted()
                waitingTimerManager.reset()

            }
            "ASSIGNED"              -> clientViewModel.setStatus("Водитель назначен")
            "COMPLETED"             -> {
                clientViewModel.onTripCompleted(order.price ?: "По тарифу")
                TahoSseService.stop(context)
                activeOrderManager.clear()
                resetOrderState()
                return@LaunchedEffect
            }
            "CANCELLED"             -> {
                TahoSseService.stop(context)
                clientViewModel.onOrderCancelled()
                activeOrderManager.clear()
                return@LaunchedEffect
            }
            "REJECTED"              -> {
                clientViewModel.onOrderRejected()
                activeOrderManager.clear()
                return@LaunchedEffect
            }
            else -> clientViewModel.setStatus("В обработке")
        }

        // Запускаем фоновый сервис — он держит SSE и показывает уведомления
        TahoSseService.start(context, orderId = order.id, role = "PASSENGER")
    }

// Читаем события из шины — сервис пишет сюда, UI обновляется здесь
    LaunchedEffect(Unit) {
        sseEventBus.events.collect { json ->
            val status = json.optString("status").takeIf { it.isNotBlank() } ?: return@collect
            Log.d(TAG, "EventBus → status: $status   $json")

            json.optString("timeToArrive").takeIf { it.isNotBlank() }?.let {
                clientViewModel.setTimeToArrive(it)
            }

            when (status) {
                "COMPLETED" -> {
                    waitingTimerManager.reset()
                    delay(300) // небольшая задержка, чтобы дать прийти событию с ценой

                    val rawPriceFromEvent = json.optString("price").takeIf { it.isNotBlank() && it != "null" }
                    val priceFromActiveOrder = activeOrder?.price?.takeIf { it.isNotBlank() && it != "null" }

                    val finalPrice = when {
                        rawPriceFromEvent != null -> rawPriceFromEvent
                        priceFromActiveOrder != null -> priceFromActiveOrder
                        else -> "По тарифу"
                    }

                    Log.d(TAG, "COMPLETED → finalPrice='$finalPrice' (from event: $rawPriceFromEvent, from active: $priceFromActiveOrder)")

                    clientViewModel.onTripCompleted(finalPrice)
                    activeOrderManager.clear()
                    TahoSseService.stop(context)
                    resetOrderState()
                }
                "CANCELLED" -> {
                    waitingTimerManager.reset()
                    clientViewModel.onOrderCancelled()
                    activeOrderManager.clear()
                    clientViewModel.setTimeToArrive(null)
                    TahoSseService.stop(context)
                }
                "REJECTED" -> {
                    clientViewModel.onOrderRejected()
                    activeOrderManager.clear()
                    TahoSseService.stop(context)
                }
                else -> {
                    when (status) {
                        "ACCEPTED", "PICKED_UP" -> clientViewModel.setStatus("Заказ принят")
                        "ARRIVED"               -> {clientViewModel.setStatus("Водитель на месте")
                            scope.launch { waitingTimerManager.onArrived(scope) } }
                        "IN_PROGRESS"           -> {
                            clientViewModel.setStatus("В пути")
                            clientViewModel.onTripStarted()
                            waitingTimerManager.reset()
                        }
                        "ASSIGNED" -> clientViewModel.setStatus("Водитель назначен")
                        else       -> clientViewModel.setStatus("Статус: $status")
                    }
                    val newDriverName  = json.optString("driverName").takeIf { it.isNotBlank() }
                    val newDriverPhone = json.optString("driverPhone").takeIf { it.isNotBlank() }
                    if (newDriverName != null || newDriverPhone != null) {
                        clientViewModel.setDriverInfo(
                            newDriverName  ?: driverName,
                            newDriverPhone ?: driverPhone
                        )
                    }
                }
            }
        }
    }

    // Подсказки при вводе текста
    LaunchedEffect(fromInput, focusedField) {
        if (focusedField == "from" && fromInput.length > 2) {
            val center = mapViewState.value?.mapWindow?.map?.cameraPosition?.target
                ?: Point(48.0397, 38.7697)
            val delta = 0.5
            val box = BoundingBox(
                Point(center.latitude - delta, center.longitude - delta),
                Point(center.latitude + delta, center.longitude + delta)
            )
            val options = SuggestOptions().apply { userPosition = center }
            suggestSession.suggest(fromInput, box, options, object : SuggestSession.SuggestListener {
                override fun onResponse(response: SuggestResponse) { fromSuggestions = response.items.take(8) }
                override fun onError(error: Error) { fromSuggestions = emptyList() }
            })
        } else fromSuggestions = emptyList()
    }

    LaunchedEffect(toInput, focusedField) {
        if (focusedField == "to" && toInput.length > 2) {
            val center = mapViewState.value?.mapWindow?.map?.cameraPosition?.target
                ?: Point(48.0397, 38.7697)
            val delta = 0.5
            val box = BoundingBox(
                Point(center.latitude - delta, center.longitude - delta),
                Point(center.latitude + delta, center.longitude + delta)
            )
            val options = SuggestOptions().apply { userPosition = center }
            suggestSession.suggest(toInput, box, options, object : SuggestSession.SuggestListener {
                override fun onResponse(response: SuggestResponse) { toSuggestions = response.items.take(8) }
                override fun onError(error: Error) { toSuggestions = emptyList() }
            })
        } else toSuggestions = emptyList()
    }


    // Получение адреса по координатам (обратное геокодирование)
    suspend fun getAddressFromPoint(point: Point): String = suspendCancellableCoroutine { cont ->
        val session = searchManager.submit(point, 16, SearchOptions(), object : Session.SearchListener {
            override fun onSearchResponse(response: Response) {
                val address = response.collection.children.firstOrNull()
                    ?.obj
                    ?.metadataContainer
                    ?.getItem(ToponymObjectMetadata::class.java)
                    ?.address
                    ?.formattedAddress ?: ""
                cont.resume(address)
            }
            override fun onSearchError(error: Error) {
                cont.resume("")
            }
        })
        cont.invokeOnCancellation { session.cancel() }
    }

    // Обновление метки (placemark) на карте
    fun updatePlacemark(type: String, point: Point) {
        val mapObjects = mapViewState.value?.mapWindow?.map?.mapObjects ?: return
        when (type) {
            "from" -> {
                fromPlacemark?.let { mapObjects.remove(it) }
                fromPlacemark = mapObjects.addPlacemark(point)
                // fromPlacemark?.setIcon(...) — можно добавить свою иконку
            }
            "to" -> {
                toPlacemark?.let { mapObjects.remove(it) }
                toPlacemark = mapObjects.addPlacemark(point)
                // toPlacemark?.setIcon(...) — можно добавить свою иконку
            }
        }
    }

    // Обработчик нажатия на карту
    fun handleMapTap(point: Point) {
        val mode = selectingPointMode ?: return
        coroutineScope.launch {
            val address = getAddressFromPoint(point)
            val displayAddr = address.ifBlank { "Выбрано на карте" }
            when (mode) {
                "from" -> {
                    fromPoint = point
                    fromAddress = displayAddr
                    fromInput = displayAddr
                    updatePlacemark("from", point)
                }
                "to" -> {
                    toPoint = point
                    toAddress = displayAddr
                    toInput = displayAddr
                    updatePlacemark("to", point)
                }
            }
            focusManager.clearFocus()
            focusedField = null
            selectingPointMode = null
        }
    }

    // Слушатель нажатий на карту (ОБЯЗАТЕЛЬНО реализуем оба метода!)
    val mapInputListener = remember {
        object : InputListener {
            override fun onMapTap(map: com.yandex.mapkit.map.Map, point: com.yandex.mapkit.geometry.Point) {
                handleMapTap(point)
            }

            override fun onMapLongTap(map: com.yandex.mapkit.map.Map, point: com.yandex.mapkit.geometry.Point) {
                // пусто или Log.d(TAG, "Long tap: $point")
            }
        }
    }

    DisposableEffect(Unit) {
        MapKitFactory.initialize(context)
        onDispose {
            mapViewState.value?.mapWindow?.map?.removeInputListener(mapInputListener)
            mapViewState.value?.onStop()
            MapKitFactory.getInstance().onStop()
        }
    }

    // ──────────────────────────────────────────────────────────────
    //                          ИНТЕРФЕЙС
    // ──────────────────────────────────────────────────────────────

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = false,
        drawerContent = {
            AppDrawerContent(
                navController = navController,
                authViewModel = authViewModel,
                name = userName ?: "Имя не указано",
                phone = userPhone ?: "Телефон не указан",
                role = userRole ?: "",
                onCloseDrawer = { scope.launch { drawerState.close() } }
            )
        },
        modifier = Modifier.fillMaxSize().background(Color(0xFFF8FAFC))
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {},
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Меню")
                        }
                    }
                )
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                AndroidView(
                    factory = { ctx ->
                        MapView(ctx).apply {
                            mapWindow.map.move(CameraPosition(Point(48.0397, 38.7697), 12f, 0f, 0f))
                            mapWindow.map.addInputListener(mapInputListener)
                        }.also { mapViewState.value = it }
                    },
                    modifier = Modifier.fillMaxSize(),
                    update = { view ->
                        view.onStart()
                        MapKitFactory.getInstance().onStart()
                    }
                )
                if (selectingPointMode != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 16.dp, start = 16.dp, end = 16.dp)
                            .background(Color(0xCC000000), shape = MaterialTheme.shapes.medium)
                            .padding(12.dp)
                    ) {
                        Text(
                            text = if (selectingPointMode == "from") "Тапните на карту — откуда" else "Тапните на карту — куда",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }

               CallOperatorButton(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.adaptiveDp())
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.White)
                        .padding(6.adaptiveDp())
                )  {


                    completionState?.let { completion ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(20.adaptiveDp()),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    "Поездка завершена",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.adaptiveSp(),
                                    color = Color(0xFF4CAF50)
                                )
                                Spacer(Modifier.height(12.adaptiveDp()))
                                Text(
                                    "Итого: ${completion.price} ₽",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 28.adaptiveSp(),
                                    color = Color(0xFF2E7D32)
                                )

                                if (completion.durationStr.isNotEmpty()) {
                                    Text(
                                        "Время в пути: ${completion.durationStr}",
                                        fontSize = 16.adaptiveSp(),
                                        color = Color.Gray
                                    )
                                }
                                Spacer(Modifier.height(15.adaptiveDp()))
                                Button(
                                    onClick = {
                                        clientViewModel.dismissCompletion()
                                        resetOrderState()

                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5))
                                ) {
                                    Text("Закрыть", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        return@Column  // не рендерим остальное пока показываем экран завершения
                    }

                    if (showCancelled) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3F3)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(20.adaptiveDp()),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    "Заказ отменён",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.adaptiveSp(),
                                    color = Color(0xFFE53935)
                                )
                                Spacer(Modifier.height(8.adaptiveDp()))

                                Text(
                                    "Вы можете сделать новый заказ или связаться с оператором",
                                    fontSize = 14.adaptiveSp(),
                                    color = Color.Gray
                                )
                                Spacer(Modifier.height(20.adaptiveDp()))
                                Button(
                                    onClick = {
                                        val intent = Intent(Intent.ACTION_DIAL,
                                            "tel:+79495895834".toUri())
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
                                        clientViewModel.dismissCancelled()
                                        resetOrderState()
                                    },

                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5))

                                ) {
                                    Text("Закрыть", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        return@Column
                    }

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
                                        val intent = Intent(Intent.ACTION_DIAL,
                                            "tel:+79495895834".toUri())  //TODO Исправить везде номер телефона манагера
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
                                        clientViewModel.dismissRejected()
                                        resetOrderState()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5))
                                ) {
                                    Text("Закрыть", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        return@Column
                    }

                    if (showOrderDetails || (isOrderPlaced && activeOrder == null)) {
                        // ← Детали заказа
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(20.adaptiveDp())) {
                                Text(
                                    text = "Заказ",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.adaptiveSp()
                                )
                                Spacer(Modifier.height(12.adaptiveDp()))

                                // Живой статус с точкой
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(12.adaptiveDp())
                                            .background(
                                                color = when {
                                                    currentStatus.contains("Заказ принят") -> Color.Green
                                                    currentStatus.contains("Водитель на месте") -> Color.Blue
                                                    currentStatus.contains("Водитель назначен") -> Color(0xFFFFA000)
                                                    currentStatus.contains("В пути") -> Color(0xFF03A9F4)
                                                    currentStatus.contains("Поездка завершена") -> Color.Gray
                                                    else -> Color.Yellow
                                                },
                                                shape = CircleShape
                                            )
                                    )
                                    Spacer(Modifier.width(8.adaptiveDp()))
                                    Text(
                                        text = currentStatus,
                                        fontWeight = FontWeight.Medium,
                                        color = when {
                                            currentStatus.contains("принят") -> Color.Green
                                            currentStatus.contains("завершена") -> Color.Gray
                                            else -> Color.Black
                                        }
                                    )
                                }

                                Spacer(Modifier.height(16.adaptiveDp()))

                                driverName?.let { name ->
                                    Text("Водитель: $name", fontWeight = FontWeight.Medium, fontSize = 18.adaptiveSp())
                                }

                             /*   when (val ws = waitingState) {
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
                                                waitingTimerManager.reset()
                                            } catch (e: Exception) {
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
                                } */

                              /**  driverPhone?.let { phone ->
                                    Spacer(Modifier.height(4.adaptiveDp()))
                                    Text(
                                        text = "Телефон: $phone",
                                        color = Color.Blue,
                                        modifier = Modifier.clickable {
                                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                                            context.startActivity(intent)
                                        }
                                    )
                                } */

                                Spacer(Modifier.height(4.adaptiveDp()))

                                Text("Откуда: $fromAddress")
                                Text("Куда: $toAddress")
                                timeToArrive?.let {
                                    Spacer(Modifier.height(4.adaptiveDp()))
                                    Text(
                                        text = "Водитель будет через: $it",
                                        color = Color(0xFF1E88E5),
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 14.adaptiveSp()
                                    )
                                }

                                // Можно добавить кнопку "Отменить заказ", если нужно
                            }
                        }
                    } else {
                        // ← Режим ввода адресов (когда заказа ещё нет)

                        // Кнопки выбора на карте
/*                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Button(
                                onClick = { selectingPoint = "from" },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (selectingPoint == "from") Color.Green else Color.Gray
                                )
                            ) {
                                Text("Откуда на карте")
                            }
                            Button(
                                onClick = { selectingPoint = "to" },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (selectingPoint == "to") Color.Green else Color.Gray
                                )
                            ) {
                                Text("Куда на карте")
                            }
                        }*/

                        Spacer(Modifier.height(4.adaptiveDp()))

                        // Поле Откуда
                        Column {
                            OutlinedTextField(
                                value = if (focusedField == "from") fromInput else fromAddress,
                                onValueChange = { fromInput = it },
                                label = { Text("Откуда") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onFocusChanged { if (it.isFocused) focusedField = "from" },
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                trailingIcon = {
                                    IconButton(onClick = {
                                        selectingPointMode = "from"
                                        focusManager.clearFocus()
                                    }) {
                                        Icon(Icons.Default.LocationOn, contentDescription = "Выбрать на карте", tint = Color(0xFF1E88E5))
                                    }
                                }
                            )

                            if (focusedField == "from" && fromSuggestions.isNotEmpty()) {
                                LazyColumn(modifier = Modifier.heightIn(max = 140.adaptiveDp())) {
                                    items(fromSuggestions) { item ->
                                        val text = item.displayText ?: item.title.text
                                        Text(
                                            text = text,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    fromAddress = text
                                                    fromPoint = item.center
                                                    fromInput = text
                                                    focusedField = null
                                                    focusManager.clearFocus()
                                                }
                                                .padding(12.adaptiveDp()),
                                            fontSize = 16.adaptiveSp()
                                        )
                                        HorizontalDivider()
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(4.adaptiveDp()))

                        // Поле Куда
                        Column {
                            OutlinedTextField(
                                value = if (focusedField == "to") toInput else toAddress,
                                onValueChange = { toInput = it },
                                label = { Text("Куда едем?") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onFocusChanged { if (it.isFocused) focusedField = "to" },
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                trailingIcon = {
                                    IconButton(onClick = {
                                        selectingPointMode = "to"
                                        focusManager.clearFocus()
                                    }) {
                                        Icon(Icons.Default.LocationOn, contentDescription = "Выбрать на карте", tint = Color(0xFF1E88E5))
                                    }
                                }
                            )
                            if (focusedField == "to" && toSuggestions.isNotEmpty()) {
                                LazyColumn(modifier = Modifier.heightIn(max = 140.adaptiveDp())) {
                                    items(toSuggestions) { item ->
                                        val text = item.displayText ?: item.title.text
                                        Text(
                                            text = text,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    toAddress = text
                                                    toPoint = item.center
                                                    toInput = text
                                                    focusedField = null
                                                    focusManager.clearFocus()
                                                }
                                                .padding(12.adaptiveDp()),
                                            fontSize = 16.adaptiveSp()
                                        )
                                        HorizontalDivider()
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(6.adaptiveDp()))

                        // ← Блок с предварительной ценой
                        if (calculatedPrice != null) {
                            val isOutside = calculatedPrice == 404.0   // именно то значение, которое возвращает бэкенд при Outside
                                //рассчет цены с доп опциями
                            val optionsPrice = (if (hasPet) 50 else 0) + (if (hasLoad) 50 else 0)
                            calculatedPrice?.let { price ->
                                val total = price + optionsPrice

                            }

                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isOutside) Color(0xFFFFF3E0) else Color(0xFFE8F5E9)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.adaptiveDp()),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        if (isOutside) "Цена по таксометру" else "Предварительная цена:",
                                        fontSize = 14.adaptiveSp(),
                                        fontWeight = FontWeight.Medium,
                                        color = if (isOutside) Color(0xFFEF6C00) else Color.Unspecified
                                    )
                                    Spacer(Modifier.weight(1f))

                                    if (!isOutside) {
                                        Text(
                                            text = if (optionsPrice > 0){
                                                "${calculatedPrice!!.toInt()} ₽(+ $optionsPrice)₽"}
                                            else {
                                                "${calculatedPrice!!.toInt()} ₽"
                                            },

                                            fontSize = 22.adaptiveSp(),
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF2E7D32)

                                        )

                                    }
                                }
                            }
                            Spacer(Modifier.height(6.adaptiveDp()))
                        }
                        OutlinedButton(
                            onClick = { showExtraOptions = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = if (hasPet || hasLoad) Color(0xFF1E88E5) else Color.Gray
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (hasPet || hasLoad) Color(0xFF1E88E5) else Color.LightGray
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (hasPet || hasLoad) {
                                    buildString {
                                        if (hasPet) append("Животное")
                                        if (hasPet && hasLoad) append(" · ")
                                        if (hasLoad) append("Груз")
                                    }
                                } else {
                                    "Дополнительные опции"
                                },
                                fontSize = 14.sp
                            )
                        }

                        Spacer(Modifier.height(4.adaptiveDp()))
                        // Кнопка Заказать
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    isOrderPlaced = true
                                    selectingPointMode = null

                                    val startStr = fromPoint?.let { "${it.latitude}, ${it.longitude}" } ?: ""
                                    val endStr   = toPoint?.let   { "${it.latitude}, ${it.longitude}" } ?: ""

                                    val request = CreateOrderRequest(
                                        startPoint   = startStr,
                                        endPoint     = endStr,
                                        startAddress = fromAddress,
                                        endAddress   = toAddress,
                                        pet = hasPet,
                                        load = hasLoad
                                    )

                                    try {
                                        val api = EntryPointAccessors.fromApplication(
                                            context.applicationContext,
                                            AppModule.ApiProvider::class.java
                                        ).apiService()

                                        val response = api.createOrder("Bearer $token", request)
                                        val orderId  = response.body()?.string()?.trim('"')
                                            ?: throw Exception("Сервер вернул пустой ответ")

                                        Log.d(TAG, "Заказ создан → ID: $orderId")

                                        // Запускаем сервис — он подпишется на SSE и будет слать события в шину
                                        TahoSseService.start(context, orderId = orderId, role = "PASSENGER")

                                        clientViewModel.setShowOrderDetails(true)

                                    } catch (e: Exception) {
                                        Log.e(TAG, "Ошибка при создании заказа", e)
                                        isOrderPlaced = false
                                        Toast.makeText(context, "Не удалось создать заказ: ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            enabled = fromPoint != null && toPoint != null &&
                                    fromAddress != "Откуда" && fromAddress.isNotBlank() &&
                                    toAddress != "Куда едем?" && toAddress.isNotBlank(),
                            modifier = Modifier.fillMaxWidth().height(56.adaptiveDp()),
                            shape = RoundedCornerShape(16.adaptiveDp())
                        ) {
                            if (isOrderPlaced) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.adaptiveDp()))
                            } else {
                                Text("Заказать такси", fontSize = 18.adaptiveSp(), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

            }
            if (showExtraOptions) {
                ModalBottomSheet(
                    onDismissRequest = { showExtraOptions = false }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .padding(bottom = 32.dp)
                            .navigationBarsPadding()
                    ) {
                        Text(
                            text = "Дополнительные опции",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )

                        Spacer(Modifier.height(20.dp))

                        HorizontalDivider()

                        Spacer(Modifier.height(12.dp))

                        // Животное
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { hasPet = !hasPet }
                                .padding(vertical = 8.dp)
                        ) {
                            Checkbox(
                                checked = hasPet,
                                onCheckedChange = {
                                    hasPet = it
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text("Животное", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                                Text(
                                    "Перевозка домашних питомцев",
                                    fontSize = 13.sp,
                                    color = Color.Gray
                                )
                            }
                        }

                        HorizontalDivider()

                        Spacer(Modifier.height(4.dp))

                        // Груз
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { hasLoad = !hasLoad }
                                .padding(vertical = 8.dp)
                        ) {
                            Checkbox(
                                checked = hasLoad,
                                onCheckedChange = { hasLoad = it }
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text("Груз", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                                Text(
                                    "Наличие крупногабаритного груза",
                                    fontSize = 13.sp,
                                    color = Color.Gray
                                )
                            }
                        }

                        HorizontalDivider()

                        Spacer(Modifier.height(20.dp))

                        Button(
                            onClick = { showExtraOptions = false },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5))
                        ) {
                            Text("Готово", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}