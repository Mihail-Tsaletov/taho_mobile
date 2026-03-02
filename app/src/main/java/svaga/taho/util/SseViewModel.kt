// SseViewModel.kt
package svaga.taho.ui.driver

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import svaga.taho.data.remote.DriverOrder
import svaga.taho.util.playNotificationSound
import svaga.taho.util.SseClient
import javax.inject.Inject

private const val TAG = "SseViewModel"

/**
 * ViewModel для SSE-подписок.
 * Живёт пока жив NavGraph / Application — не умирает при смене Activity/Composable.
 *
 * Использование в DriverHomeScreen:
 *   val sseViewModel: SseViewModel = hiltViewModel(
 *       viewModelStoreOwner = LocalActivity.current  // ← ключевой момент!
 *   )
 *
 * Если хочешь ещё дольше жить (через несколько Activity) — используй
 * activityViewModels() или viewModel(LocalActivity.current).
 */
@HiltViewModel
class SseViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sseClient: svaga.taho.util.SseClient,
    private val tokenManager: svaga.taho.data.local.TokenManager,
) : ViewModel() {

    // ── Состояния ─────────────────────────────────────────────────────────────

    private val _incomingOrder = MutableStateFlow<DriverOrder?>(null)
    val incomingOrder: StateFlow<DriverOrder?> = _incomingOrder.asStateFlow()

    private val _connectionState = MutableStateFlow<SseConnectionState>(SseConnectionState.Disconnected)
    val connectionState: StateFlow<SseConnectionState> = _connectionState.asStateFlow()

    // ── Jobs ──────────────────────────────────────────────────────────────────

    private var newOrdersJob: Job? = null
    private var orderUpdatesJob: Job? = null
    private var currentToken: String = ""

    // ─────────────────────────────────────────────────────────────────────────
    // PUBLIC API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Вызывай из LaunchedEffect(token) в DriverHomeScreen.
     * ViewModel сама решит нужно ли перезапускать подписку.
     */
    fun onTokenReady(token: String) {
        if (token.isEmpty() || token == currentToken) return
        currentToken = token
        Log.d(TAG, "Token обновлён — перезапускаем driver-подписку")
        startDriverSubscription()
    }

    /**
     * Запускает подписку на обновления конкретного заказа.
     * Вызывается при принятии заказа.
     */
    fun startOrderSubscription(
        orderId: String,
        onStatus: (status: String) -> Unit,
    ) {
        orderUpdatesJob?.cancel()
        orderUpdatesJob = viewModelScope.launch {
            Log.d(TAG, "Старт подписки на заказ $orderId")
            _connectionState.value = SseConnectionState.Connected(orderId)
            try {
                sseClient.subscribe(
                    orderId = orderId,
                    token = currentToken,
                    scope = this,
                    onUpdate = { json ->
                        val status = json.optString("status")
                        Log.d(TAG, "SSE order[$orderId] → $status")
                        onStatus(status)

                        // Автоматический перезапуск driver-подписки после завершения
                        if (status in listOf("CANCELLED", "COMPLETED")) {
                            orderUpdatesJob?.cancel()
                            viewModelScope.launch {
                                delay(500)
                                startDriverSubscription()
                            }
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка подписки на заказ $orderId", e)
                _connectionState.value = SseConnectionState.Error(e.message ?: "Unknown")
            }
        }
    }

    fun cancelOrderSubscription() {
        orderUpdatesJob?.cancel()
        orderUpdatesJob = null
        _connectionState.value = SseConnectionState.Disconnected
        Log.d(TAG, "Подписка на заказ отменена")
    }

    fun clearIncomingOrder() {
        _incomingOrder.value = null
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PRIVATE
    // ─────────────────────────────────────────────────────────────────────────

    private fun startDriverSubscription() {
        newOrdersJob?.cancel()
        newOrdersJob = viewModelScope.launch {
            Log.d(TAG, "Старт driver-подписки (ждём новые заказы)")
            try {
                sseClient.subscribe(
                    orderId = "driver",
                    token = currentToken,
                    scope = this,
                    onUpdate = { json ->
                        Log.d(TAG, "SSE driver RAW → $json")
                        parseIncomingOrder(json)
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка driver-подписки", e)
                // Автоматический реконнект через 3 сек
                delay(3_000)
                Log.d(TAG, "Попытка реконнекта driver-подписки...")
                startDriverSubscription()
            }
        }
    }

    private fun parseIncomingOrder(json: org.json.JSONObject) {
        try {
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
            _incomingOrder.value = order
            playNotificationSound(context)
            Log.d(TAG, "Новый заказ получен: ${order.id}")
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка парсинга входящего заказа", e)
        }
    }

    override fun onCleared() {
        super.onCleared()
        newOrdersJob?.cancel()
        orderUpdatesJob?.cancel()
        Log.d(TAG, "SseViewModel cleared — все подписки отменены")
    }
}

// ─────────────────────────────────────────────────────────────────────────────

sealed class SseConnectionState {
    object Disconnected : SseConnectionState()
    data class Connected(val orderId: String) : SseConnectionState()
    data class Error(val message: String) : SseConnectionState()
}