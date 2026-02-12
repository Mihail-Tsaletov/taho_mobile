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
import java.text.SimpleDateFormat
import java.util.*
import android.util.Log
import com.yandex.mapkit.map.PlacemarkMapObject
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Job
import svaga.taho.data.local.TokenManager
import svaga.taho.data.remote.CreateOrderRequest
import svaga.taho.di.AppModule
import svaga.taho.ui.auth.AuthViewModel
import svaga.taho.ui.menu.AppDrawerContent
import kotlin.coroutines.resume

private const val TAG = "ClientHomeScreen"

private var sseJob by mutableStateOf<Job?>(null)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientHomeScreen(navController: NavController) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Основные состояния заказа
    var fromAddress by remember { mutableStateOf("Откуда") }
    var toAddress   by remember { mutableStateOf("Куда едем?") }
    var fromPoint   by remember { mutableStateOf<Point?>(null) }
    var toPoint     by remember { mutableStateOf<Point?>(null) }
    var isOrderPlaced by remember { mutableStateOf(false) }
    var orderTime    by remember { mutableStateOf("") }

    var showOrderDetails by remember { mutableStateOf(false) }
    var currentStatus by remember { mutableStateOf("В обработке") }
    var driverName   by remember { mutableStateOf<String?>(null) }
    var driverPhone  by remember { mutableStateOf<String?>(null) }

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

    val searchManager = remember {
        SearchFactory.getInstance().createSearchManager(SearchManagerType.COMBINED)
    }

    // SSE и API
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

    val authViewModel: AuthViewModel = hiltViewModel()
    val tokenManager: TokenManager = hiltViewModel<AuthViewModel>().tokenManager

    val userName by tokenManager.nameFlow.collectAsState(initial = "Загрузка...")
    val userPhone by tokenManager.phoneFlow.collectAsState(initial = "Загрузка...")

    val activeOrderManager = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            AppModule.ApiProvider::class.java
        ).activeOrderManager()
    }
    val activeOrder by activeOrderManager.activeOrder.collectAsState()

    // Загрузка активного заказа при запуске экрана
    LaunchedEffect(Unit) {
        activeOrderManager.loadActiveOrderForClient()
    }

    LaunchedEffect(activeOrder) {
        activeOrder?.let { order ->
            Log.d(TAG, "Активный заказ загружен: ${order.id}")
            showOrderDetails = false
            fromAddress = order.startAddress
            toAddress = order.endAddress
            isOrderPlaced = false

            currentStatus = when (order.status) {
                "ACCEPTED", "PICKED_UP" -> "Заказ принят"
                "ARRIVED" -> "Водитель на месте"
                "IN_PROGRESS" -> "В пути"
                "COMPLETED", "CANCELLED" -> {
                    activeOrderManager.clear()
                    return@LaunchedEffect
                }
                else -> currentStatus
            }
            driverName = order.driverName
            driverPhone = order.driverPhone

            sseJob?.cancel()
            sseJob = coroutineScope.launch {
                sseClient.subscribe(
                    order.id,
                    token,
                    coroutineScope,
                    onUpdate = { json ->
                        val status = json.optString("status", "")
                        if (status.isNotEmpty()) {
                            when (status) {
                                "COMPLETED", "CANCELLED" -> {
                                    currentStatus = if (status == "COMPLETED") "Поездка завершена" else "Заказ отменён"
                                    showOrderDetails = false
                                    isOrderPlaced = false
                                    fromAddress = "Откуда"
                                    toAddress = "Куда едем?"
                                    orderTime = ""
                                    driverName = null
                                    driverPhone = null
                                    activeOrderManager.clear()
                                    sseClient.disconnect()
                                }
                                else -> {
                                    currentStatus = when (status) {
                                        "ACCEPTED", "PICKED_UP" -> "Заказ принят"
                                        "ARRIVED" -> "Водитель на месте"
                                        "IN_PROGRESS" -> "В пути"
                                        "ASSIGNED" -> "Водитель назначен"
                                        else -> "Статус: $status"
                                    }
                                }
                            }
                        }
                        json.optString("driverName").takeIf { it.isNotBlank() }?.let { driverName = it }
                        json.optString("driverPhone").takeIf { it.isNotBlank() }?.let { driverPhone = it }
                    },
                    onError = { Log.e("SSE", "Ошибка", it) }
                )
            }
        } ?: run {
            showOrderDetails = false
            isOrderPlaced = false
            currentStatus = "В обработке"
            driverName = null
            driverPhone = null
            sseJob?.cancel()
        }
    }

    // Подсказки при вводе текста
    LaunchedEffect(fromInput, focusedField) {
        val box = BoundingBox(Point(41.0, 19.0), Point(74.0, 180.0))
        if (focusedField == "from" && fromInput.length > 2) {
            suggestSession.suggest(fromInput, box, SuggestOptions(), object : SuggestSession.SuggestListener {
                override fun onResponse(response: SuggestResponse) { fromSuggestions = response.items.take(8) }
                override fun onError(error: Error) { fromSuggestions = emptyList() }
            })
        } else fromSuggestions = emptyList()
    }

    LaunchedEffect(toInput, focusedField) {
        val box = BoundingBox(Point(41.0, 19.0), Point(74.0, 180.0))
        if (focusedField == "to" && toInput.length > 2) {
            suggestSession.suggest(toInput, box, SuggestOptions(), object : SuggestSession.SuggestListener {
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
        val mode = focusedField ?: return   // если ничего не в фокусе — игнорируем тап

        coroutineScope.launch {
            val address = getAddressFromPoint(point)
            val displayAddr = address.ifBlank { "Выбрано на карте" }

            when (mode) {
                "from" -> {
                    fromPoint = point
                    fromAddress = displayAddr
                    fromInput = displayAddr          // синхронизируем поле ввода
                    updatePlacemark("from", point)
                }
                "to" -> {
                    toPoint = point
                    toAddress = displayAddr
                    toInput = displayAddr            // синхронизируем поле ввода
                    updatePlacemark("to", point)
                }
            }

            // Опционально: убираем фокус после выбора точки
            focusManager.clearFocus()
            focusedField = null
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
            sseJob?.cancel()
            sseJob = null
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
                onCloseDrawer = { scope.launch { drawerState.close() } }
            )
        },
        modifier = Modifier.fillMaxSize().background(Color(0xFFF8FAFC))
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Taho Client") },
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
                            mapWindow.map.move(CameraPosition(Point(55.7558, 37.6173), 10f, 0f, 0f))
                            mapWindow.map.addInputListener(mapInputListener)
                        }.also { mapViewState.value = it }
                    },
                    modifier = Modifier.fillMaxSize(),
                    update = { view ->
                        view.onStart()
                        MapKitFactory.getInstance().onStart()
                    }
                )

                // Карточка активного заказа сверху
                if (activeOrder != null && !showOrderDetails) {
                    Card(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .padding(16.dp)
                            .clickable { showOrderDetails = true },
                        colors = CardDefaults.cardColors(Color(0xFF1E88E5)),
                        elevation = CardDefaults.cardElevation(8.dp)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Активный заказ", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Spacer(Modifier.height(8.dp))
                            Text("От: $fromAddress", color = Color.White.copy(alpha = 0.9f))
                            Text("До: $toAddress", color = Color.White.copy(alpha = 0.9f))
                            Text("Статус: $currentStatus", color = Color.White)
                            driverName?.let { Text("Водитель: $it", color = Color.White) }
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.White)
                        .padding(16.dp)
                ) {
                    if (showOrderDetails || (isOrderPlaced && activeOrder == null)) {
                        // ← Детали заказа (как было раньше)
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Text(
                                    text = "Заказ",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp
                                )
                                Spacer(Modifier.height(12.dp))

                                // Живой статус с точкой
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
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
                                    Spacer(Modifier.width(8.dp))
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

                                Spacer(Modifier.height(16.dp))

                                driverName?.let { name ->
                                    Text("Водитель: $name", fontWeight = FontWeight.Medium, fontSize = 18.sp)
                                }

                                driverPhone?.let { phone ->
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = "Телефон: $phone",
                                        color = Color.Blue,
                                        modifier = Modifier.clickable {
                                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                                            context.startActivity(intent)
                                        }
                                    )
                                }

                                Spacer(Modifier.height(16.dp))

                                Text("Откуда: $fromAddress")
                                Text("Куда: $toAddress")
                                Text("Время: $orderTime")

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

                        Spacer(Modifier.height(12.dp))

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
                                )
                            )

                            if (focusedField == "from" && fromSuggestions.isNotEmpty()) {
                                LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
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
                                                .padding(12.dp),
                                            fontSize = 16.sp
                                        )
                                        HorizontalDivider()
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        // Поле Куда — ВОТ ЭТОТ БЛОК БЫЛ ПРОПУЩЕН
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
                                )
                            )

                            if (focusedField == "to" && toSuggestions.isNotEmpty()) {
                                LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
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
                                                .padding(12.dp),
                                            fontSize = 16.sp
                                        )
                                        HorizontalDivider()
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(20.dp))

                        // Кнопка Заказать
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    orderTime = SimpleDateFormat("HH:mm, dd MMM", Locale("ru")).format(Date())
                                    isOrderPlaced = true

                                    val startStr = fromPoint?.let { "${it.latitude}, ${it.longitude}" } ?: ""
                                    val endStr = toPoint?.let { "${it.latitude}, ${it.longitude}" } ?: ""

                                    val request = CreateOrderRequest(
                                        startPoint = startStr,
                                        endPoint = endStr,
                                        startAddress = fromAddress,
                                        endAddress = toAddress
                                    )

                                    Log.d(TAG, "Создаём заказ: $request")

                                    try {
                                        val api = EntryPointAccessors.fromApplication(
                                            context.applicationContext,
                                            AppModule.ApiProvider::class.java
                                        ).apiService()

                                        Log.d(TAG, "Токен: Bearer $token")
                                        val response = api.createOrder("Bearer $token", request)

                                        val orderId = response.body()?.string()?.trim('"')
                                            ?: throw Exception("Сервер вернул пустой ответ")

                                        Log.d(TAG, "Заказ создан → ID: $orderId")

                                        // Запуск SSE (как было раньше)
                                        sseJob?.cancel()
                                        sseJob = coroutineScope.launch {
                                            sseClient.subscribe(
                                                orderId,
                                                token,
                                                coroutineScope,
                                                onUpdate = { /* ... как раньше ... */ },
                                                onError = { Log.e("SSE", "Ошибка", it) }
                                            )
                                        }

                                        // ← Если хочешь сразу показать детали после создания
                                        // showOrderDetails = true   // раскомментируй, если нужно
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Ошибка при создании заказа", e)
                                        Toast.makeText(context, "Не удалось создать заказ: ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            enabled = fromPoint != null && toPoint != null &&
                                    fromAddress != "Откуда" && fromAddress.isNotBlank() &&
                                    toAddress != "Куда едем?" && toAddress.isNotBlank(),
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            if (isOrderPlaced) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                            } else {
                                Text("Заказать такси", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}