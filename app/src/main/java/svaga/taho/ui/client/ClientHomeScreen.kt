package svaga.taho.ui.client

import android.R.attr.data
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
import com.yandex.mapkit.map.CameraPosition
import com.yandex.mapkit.mapview.MapView
import com.yandex.mapkit.search.*
import com.yandex.mapkit.geometry.BoundingBox
import com.yandex.runtime.Error
import svaga.taho.R
import java.text.SimpleDateFormat
import java.util.*
import android.util.Log
import android.widget.Toast
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import svaga.taho.data.remote.ApiService
import svaga.taho.data.remote.CreateOrderRequest
import svaga.taho.di.AppModule
import kotlin.jvm.java

@Composable
fun ClientHomeScreen() {
    val TAG = "ClientHomeScreen"
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()


    var fromAddress by remember { mutableStateOf("Откуда") }
    var toAddress by remember { mutableStateOf("Куда едем?") }
    var fromPoint by remember { mutableStateOf<Point?>(null) }
    var toPoint by remember { mutableStateOf<Point?>(null) }
    var isOrderPlaced by remember { mutableStateOf(false) }
    var orderTime by remember { mutableStateOf("") }

    var fromInput by remember { mutableStateOf("") }
    var toInput by remember { mutableStateOf("") }
    var fromSuggestions by remember { mutableStateOf<List<SuggestItem>>(emptyList()) }
    var toSuggestions by remember { mutableStateOf<List<SuggestItem>>(emptyList()) }

    var focusedField by remember { mutableStateOf<String?>(null) } // "from" или "to" или null

    val suggestSession = remember {
        SearchFactory.getInstance()
            .createSearchManager(SearchManagerType.COMBINED)
            .createSuggestSession()
    }

    // Подсказки для "Откуда"
    LaunchedEffect(fromInput) {
        val hugeBox = BoundingBox(Point(41.0, 19.0), Point(74.0, 180.0))
        if (focusedField == "from" && fromInput.length > 2) {
            suggestSession.suggest(
                fromInput,
                hugeBox, // вся страна
                SuggestOptions(),
                object : SuggestSession.SuggestListener {
                    override fun onResponse(response: SuggestResponse) {
                        fromSuggestions = response.items.take(8)
                    }
                    override fun onError(error: Error) {
                        fromSuggestions = emptyList()
                    }
                }
            )
        } else {
            fromSuggestions = emptyList()
        }
    }

    // Подсказки для "Куда"
    LaunchedEffect(toInput) {
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
                }
            )
        } else {
            toSuggestions = emptyList()
        }
    }

    DisposableEffect(Unit) {
        MapKitFactory.initialize(context)
        onDispose {}
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Карта — грузится сразу и постоянно
        AndroidView(
            factory = { ctx ->
                MapView(ctx).apply {
                    mapWindow.map.move(CameraPosition(Point(55.7558, 37.6173), 10f, 0f, 0f))
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                view.onStart()
                MapKitFactory.getInstance().onStart()
            }
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.White)
                .padding(16.dp)
        ) {
            if (isOrderPlaced) {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp)) {
                        Text("Заказ создан", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Spacer(Modifier.height(12.dp))
                        Text("Откуда: $fromAddress")
                        Text("Куда: $toAddress")
                        Text("Время: $orderTime")
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(12.dp).background(Color.Yellow, CircleShape))
                            Spacer(Modifier.width(8.dp))
                            Text("В обработке")
                        }
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
                            orderTime = SimpleDateFormat("HH:mm, dd MMM", Locale("ru")).format(Date())
                            isOrderPlaced = true

                            // Формируем строки в формате "lat, lon"
                            val startStr = fromPoint?.let { "${it.latitude}, ${it.longitude}" } ?: ""
                            val endStr = toPoint?.let { "${it.latitude}, ${it.longitude}" } ?: ""

                            val request = CreateOrderRequest(
                                startPoint = startStr,
                                endPoint = endStr,
                                startAddress = fromAddress,
                                endAddress = toAddress
                            )

                            Log.d(TAG, "Отправляем заказ: $request")

                            try {
                                val tokenManager = EntryPointAccessors.fromApplication(
                                    context.applicationContext,
                                    AppModule.ApiProvider::class.java
                                ).tokenManager()

                                // Получаем токен из DataStore
                                val token = tokenManager.tokenFlow.first() ?: throw Exception("Нет токена")

                                // Получаем ApiService
                                val api = EntryPointAccessors.fromApplication(
                                    context.applicationContext,
                                    AppModule.ApiProvider::class.java
                                ).apiService()

                                // Отправляем с заголовком Authorization
                                val response = api.createOrder("Bearer $token", request)
                                // ←←←←←←←←←←←←←←←←←←←←←←←←←←

                                Log.d(TAG, "Заказ успешно создан: $response")
                            } catch (e: Exception) {
                                Log.e(TAG, "Ошибка создания заказа", e)
                                Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    enabled = fromPoint != null && toPoint != null,
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

    DisposableEffect(Unit) {
        onDispose {
            MapKitFactory.getInstance().onStop()
        }
    }
}