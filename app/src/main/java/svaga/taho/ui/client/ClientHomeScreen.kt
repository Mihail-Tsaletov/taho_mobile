package svaga.taho.ui.client

import android.R
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
import com.yandex.mapkit.map.CameraPosition
import com.yandex.mapkit.mapview.MapView
import com.yandex.mapkit.search.*
import com.yandex.mapkit.geometry.BoundingBox
import com.yandex.runtime.Error
import java.text.SimpleDateFormat
import java.util.*
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Menu
import androidx.compose.ui.Alignment
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import svaga.taho.data.local.TokenManager
import svaga.taho.data.remote.CreateOrderRequest
import svaga.taho.di.AppModule
import svaga.taho.ui.auth.AuthViewModel
import svaga.taho.ui.menu.AppDrawerContent
import svaga.taho.util.getCurrentLocation
import kotlin.jvm.java

private const val TAG = "ClientHomeScreen"
private var currentSseJob by mutableStateOf<Job?>(null)
var sseJob by mutableStateOf<Job?>(null)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientHomeScreen(navController: NavController) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()

    // Значения для работы с Driwer
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Состояние UI
    var fromAddress by remember { mutableStateOf("Откуда") }
    var toAddress by remember { mutableStateOf("Куда едем?") }
    var fromPoint by remember { mutableStateOf<Point?>(null) }
    var toPoint by remember { mutableStateOf<Point?>(null) }
    var isOrderPlaced by remember { mutableStateOf(false) }
    var orderTime by remember { mutableStateOf("") }

    var showOrderDetails by remember { mutableStateOf(false) }

    var currentStatus by remember { mutableStateOf("В обработке") }
    var driverName by remember { mutableStateOf<String?>(null) }
    var driverPhone by remember { mutableStateOf<String?>(null) }

    var fromInput by remember { mutableStateOf("") }
    var toInput by remember { mutableStateOf("") }
    var fromSuggestions by remember { mutableStateOf<List<SuggestItem>>(emptyList()) }
    var toSuggestions by remember { mutableStateOf<List<SuggestItem>>(emptyList()) }
    var focusedField by remember { mutableStateOf<String?>(null) }

    val sseClient = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            AppModule.ApiProvider::class.java
        ).sseClient()
    }

    // Получаем токен и ApiService
    val token by EntryPointAccessors.fromApplication(
        context.applicationContext,
        AppModule.ApiProvider::class.java
    ).tokenManager().tokenFlow.collectAsState(initial = "")
    val authViewModel: AuthViewModel = hiltViewModel()

    val tokenManager: TokenManager = hiltViewModel<AuthViewModel>().tokenManager

// Реактивно получаем данные
    val userName by tokenManager.nameFlow.collectAsState(initial = "Загрузка...")
    val userPhone by tokenManager.phoneFlow.collectAsState(initial = "Загрузка...")

    val apiService = remember {
        EntryPointAccessors.fromApplication(context, AppModule.ApiProvider::class.java).apiService()
    }

    // Активный заказ — сверху экрана
    val activeOrderManager = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            AppModule.ApiProvider::class.java
        ).activeOrderManager()
    }
    val activeOrder by activeOrderManager.activeOrder.collectAsState()

    val suggestSession = remember {
        SearchFactory.getInstance()
            .createSearchManager(SearchManagerType.COMBINED)
            .createSuggestSession()
    }

// Загружаем активный заказ при запуске
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

            // Запускаем SSE
            sseJob?.cancel()
            sseJob = coroutineScope.launch {
                sseClient.subscribe(
                    order.id,
                    token.toString(),
                    coroutineScope,
                    onUpdate = { json ->
                        Log.d(TAG, "SSE получил: $json")
                        val status = json.optString("status", "")
                        if (status.isNotEmpty()) {
                            when (status) {
                                "COMPLETED", "CANCELLED" -> {
                                    // ← ПРАВИЛЬНО ПРИСВАИВАЕМ!
                                    currentStatus =
                                        if (status == "COMPLETED") "Поездка завершена" else "Заказ отменён"

                                    // ← СБРАСЫВАЕМ ВСЁ!
                                    showOrderDetails = false
                                    isOrderPlaced = false
                                    fromAddress = "Откуда"
                                    toAddress = "Куда едем?"
                                    orderTime = ""
                                    driverName = null
                                    driverPhone = null

                                    activeOrderManager.clear()
                                    sseClient.disconnect()
                                    Log.d(TAG, "Заказ завершён — всё очищено")
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

                        json.optString("driverName").takeIf { it.isNotBlank() }
                            ?.let { driverName = it }
                        json.optString("driverPhone").takeIf { it.isNotBlank() }
                            ?.let { driverPhone = it }
                    }, onError = { e ->
                        Log.e("SSE", "Ошибка", e)
                    })
            }
        } ?: run {
            // Нет активного заказа — чистим всё
            showOrderDetails = false
            isOrderPlaced = false
            currentStatus = "В обработке"
            driverName = null
            driverPhone = null
            sseJob?.cancel()
        }
    }


    // Подсказки для "Откуда"
    LaunchedEffect(fromInput, focusedField) {
        val hugeBox = BoundingBox(Point(41.0, 19.0), Point(74.0, 180.0))
        if (focusedField == "from" && fromInput.length > 2) {
            suggestSession.suggest(
                fromInput,
                hugeBox,
                SuggestOptions(),
                object : SuggestSession.SuggestListener {
                    override fun onResponse(response: SuggestResponse) {
                        fromSuggestions = response.items.take(8)
                    }

                    override fun onError(error: Error) {
                        fromSuggestions = emptyList()
                    }
                })
        } else fromSuggestions = emptyList()
    }

    // Подсказки для "Куда"
    LaunchedEffect(toInput, focusedField) {
        val hugeBox = BoundingBox(Point(41.0, 19.0), Point(74.0, 180.0))
        if (focusedField == "to" && toInput.length > 2) {
            suggestSession.suggest(
                toInput,
                hugeBox,
                SuggestOptions(),
                object : SuggestSession.SuggestListener {
                    override fun onResponse(response: SuggestResponse) {
                        toSuggestions = response.items.take(8)
                    }

                    override fun onError(error: Error) {
                        toSuggestions = emptyList()
                    }
                })
        } else toSuggestions = emptyList()
    }

    DisposableEffect(Unit) {
        MapKitFactory.initialize(context)
        onDispose { sseClient.disconnect() }
    }

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
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Taho Client") },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    }
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier.fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Карта — грузится сразу и постоянно
                AndroidView(
                    factory = { ctx ->
                        MapView(ctx).apply {
                            mapWindow.map.move(
                                CameraPosition(
                                    Point(
                                        55.7558,
                                        37.6173
                                    ), 10f, 0f, 0f
                                )
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    update = { view -> view.onStart(); MapKitFactory.getInstance().onStart() }
                )

                if (activeOrder != null && !showOrderDetails) {
                    Card(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .padding(16.dp)
                            .clickable {
                                showOrderDetails = true  // открываем нижнюю карточку
                            },
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E88E5)),
                        elevation = CardDefaults.cardElevation(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Активный заказ",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("От: $fromAddress", color = Color.White.copy(alpha = 0.9f))
                            Text("До: $toAddress", color = Color.White.copy(alpha = 0.9f))
                            Text("Статус: $currentStatus", color = Color.White)
                            driverName?.let {
                                Text(
                                    "Водитель: $it",
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                // TODO Вот в этот Column  я запихнул все дальше, если будет вьеб по карточкам
                //  удалить все что что следующего ТУДУ и убрать снизу скобку

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Transparent)
                ) {
                    // Кнопка GPS — сверху справа
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)  // место только для кнопки
                    ) {
                        FloatingActionButton(
                            onClick = {
                                getCurrentLocation(
                                    context = context,
                                    onSuccess = { point, address ->
                                        fromPoint = point
                                        fromAddress = address
                                        fromInput = address
                                        Toast.makeText(context, "Локация: $address", Toast.LENGTH_SHORT).show()
                                    },
                                    onError = { error ->
                                        Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                                    }
                                )
                            },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(56.dp),
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                            contentColor = Color.White,
                            elevation = FloatingActionButtonDefaults.elevation(8.dp)
                        ) {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = "Моя локация"
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))  // зазор между кнопкой GPS и первым полем

                    Column(
                        modifier = Modifier

                            .fillMaxWidth()
                            .background(Color.White)
                            .padding(16.dp)
                    ) {
                        if (showOrderDetails || (isOrderPlaced && activeOrder == null)) {
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

                                    // Живой статус
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(12.dp)
                                                .background(
                                                    color = when {
                                                        currentStatus.contains("Заказ принят") -> Color.Green
                                                        currentStatus.contains("Водитель на месте") -> Color.Blue
                                                        currentStatus.contains("Водитель назначен") -> Color(
                                                            0xFFFFA000
                                                        )

                                                        currentStatus.contains("В пути") -> Color(
                                                            0xFF03A9F4
                                                        )

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

                                    // Водитель
                                    driverName?.let { name ->
                                        Text(
                                            "Водитель: $name",
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 18.sp
                                        )
                                    }

                                    driverPhone?.let { phone ->
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = "Телефон: $phone",
                                            color = Color.Blue,
                                            modifier = Modifier.clickable {
                                                // Позвонить водителю
                                                val intent = Intent(
                                                    Intent.ACTION_DIAL,
                                                    Uri.parse("tel:$phone")
                                                )
                                                context.startActivity(intent)
                                            }
                                        )
                                    }

                                    Spacer(Modifier.height(16.dp))

                                    Text("Откуда: $fromAddress")
                                    Text("Куда: $toAddress")
                                    Text("Время: $orderTime")

                                    // Если нужно — можно добавить кнопку "Отменить" и т.д.
                                }
                            }
                        } else {
                            // Откуда
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

                            // Куда
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

                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        orderTime =
                                            SimpleDateFormat("HH:mm, dd MMM", Locale("ru")).format(
                                                Date()
                                            )
                                        isOrderPlaced = true


                                        // Формируем строки в формате "lat, lon"
                                        val startStr =
                                            fromPoint?.let { "${it.latitude}, ${it.longitude}" }
                                                ?: ""
                                        val endStr =
                                            toPoint?.let { "${it.latitude}, ${it.longitude}" } ?: ""

                                        val request = CreateOrderRequest(
                                            startPoint = startStr,
                                            endPoint = endStr,
                                            startAddress = fromAddress,
                                            endAddress = toAddress
                                        )

                                        Log.d(TAG, "Отправляем заказ: $request")

                                        try {
                                            val api = EntryPointAccessors.fromApplication(
                                                context.applicationContext,
                                                AppModule.ApiProvider::class.java
                                            ).apiService()

                                            // Отправляем с заголовком Authorization
                                            Log.d(TAG, "Заголовок: Bearer $token")
                                            val response = api.createOrder("Bearer $token", request)

                                            val orderId = response.body()?.string()?.trim('"')
                                                ?: throw Exception("Пустой ответ от сервера")

                                            Log.d(TAG, "Заказ создан, ID: $orderId")

                                            // Запускаем SSE сразу после создания заказа
                                            sseJob = coroutineScope.launch {
                                                sseClient.subscribe(
                                                    orderId,
                                                    token.toString(),
                                                    coroutineScope,
                                                    onUpdate = { json ->
                                                        Log.d(TAG, "SSE получил: $json")
                                                        val status = json.optString("status", "")
                                                        if (status.isNotEmpty()) {
                                                            when (status) {
                                                                "COMPLETED", "CANCELLED" -> {
                                                                    // ← ПРАВИЛЬНО ПРИСВАИВАЕМ!
                                                                    currentStatus =
                                                                        if (status == "COMPLETED") "Поездка завершена" else "Заказ отменён"

                                                                    // ← СБРАСЫВАЕМ ВСЁ!
                                                                    showOrderDetails = false
                                                                    isOrderPlaced = false
                                                                    fromAddress = "Откуда"
                                                                    toAddress = "Куда едем?"
                                                                    orderTime = ""
                                                                    driverName = null
                                                                    driverPhone = null

                                                                    activeOrderManager.clear()
                                                                    sseClient.disconnect()
                                                                    Log.d(
                                                                        TAG,
                                                                        "Заказ завершён — всё очищено"
                                                                    )
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
                                                        json.optString("driverName")
                                                            .takeIf { it.isNotBlank() }
                                                            ?.let {
                                                                driverName = it
                                                            }

                                                        json.optString("driverPhone")
                                                            .takeIf { it.isNotBlank() }
                                                            ?.let {
                                                                driverPhone = it
                                                            }
                                                    },
                                                    onError = { e ->
                                                        Log.e("SSE", "Ошибка", e)
                                                    })
                                            }

                                            Log.d(TAG, "Заказ успешно создан: $orderId")
                                        } catch (e: Exception) {
                                            Log.e(TAG, "Ошибка создания заказа", e)
                                            Toast.makeText(
                                                context,
                                                "Ошибка: ${e.message}",
                                                Toast.LENGTH_LONG
                                            )
                                                .show()
                                        }
                                    }
                                },
                                enabled = fromPoint != null && toPoint != null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                if (isOrderPlaced) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                } else {
                                    Text(
                                        "Заказать такси",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                DisposableEffect(Unit) {
                    onDispose {
                        sseJob?.cancel()
                        sseJob = null
                        Log.d(TAG, "Экран закрыт — SSE отключён")
                        MapKitFactory.getInstance().onStop()
                    }
                }
            }
        }
    }
}
