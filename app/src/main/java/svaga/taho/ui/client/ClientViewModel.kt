package svaga.taho.ui.client

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class TripCompletionState(
    val price: String? = null,
    val durationStr: String = "",
    val startAddress: String = "",
    val endAddress: String = ""
)

@HiltViewModel
class ClientViewModel @Inject constructor() : ViewModel() {

    // ── Статус и инфо о водителе ──────────────────────────────────
    private val _currentStatus = MutableStateFlow("В обработке")
    val currentStatus: StateFlow<String> = _currentStatus.asStateFlow()

    private val _driverName = MutableStateFlow<String?>(null)
    val driverName: StateFlow<String?> = _driverName.asStateFlow()

    private val _driverPhone = MutableStateFlow<String?>(null)
    val driverPhone: StateFlow<String?> = _driverPhone.asStateFlow()

    // ── Экран завершения ──────────────────────────────────────────
    private val _completionState = MutableStateFlow<TripCompletionState?>(null)
    val completionState: StateFlow<TripCompletionState?> = _completionState.asStateFlow()

    // ── Время начала поездки (для подсчёта длительности) ──────────
    private var tripStartTimeMs: Long? = null

    // ── Отображение деталей заказа ────────────────────────────────
    private val _showOrderDetails = MutableStateFlow(false)
    val showOrderDetails: StateFlow<Boolean> = _showOrderDetails.asStateFlow()

    // ── Отображение отмены заказа ────────────────────────────────
    private val _showCancelled = MutableStateFlow(false)
    val showCancelled: StateFlow<Boolean> = _showCancelled.asStateFlow()

    // ── Отображение отмены менеджером заказа ────────────────────────────────
    private val _showRejected = MutableStateFlow(false)
    val showRejected: StateFlow<Boolean> = _showRejected.asStateFlow()

    // ─────────────────────────────────────────────────────────────


    fun setStatus(status: String) {
        _currentStatus.value = status
    }

    fun setDriverInfo(name: String?, phone: String?) {
        _driverName.value = name
        _driverPhone.value = phone
    }

    fun setShowOrderDetails(show: Boolean) {
        _showOrderDetails.value = show
    }

    fun onTripStarted() {
        if (tripStartTimeMs == null) {
            tripStartTimeMs = System.currentTimeMillis()
        }
    }

    fun onTripCompleted(price: String?) {
        val endTime = System.currentTimeMillis()
        val durationMs = endTime - (tripStartTimeMs ?: endTime)
        val mins = (durationMs / 60000).toInt()
        val secs = ((durationMs % 60000) / 1000).toInt()
        val durationStr = if (mins > 0) "$mins мин $secs сек" else "$secs сек"

        val finalPrice = when {
            price?.isNotBlank() == true -> price
            else -> "Цена не указана"   // или "По тарифу" если хочешь
        }

        _completionState.value = TripCompletionState(
            price = finalPrice,
            durationStr = durationStr
            // startAddress и endAddress можно оставить пустыми, раз не нужны
        )
    }

    fun resetOrderState() {
        _currentStatus.value = "В обработке"
        _driverName.value = null
        _driverPhone.value = null
        _showOrderDetails.value = false
        tripStartTimeMs = null
    }

    fun dismissCompletion() {
        _completionState.value = null
        resetOrderState()
    }

    fun onOrderCancelled() {
        _showCancelled.value = true
    }

    fun dismissCancelled() {
        _showCancelled.value = false
        resetOrderState()
    }

    fun onOrderRejected() {
        _showRejected.value = true
    }

    fun dismissRejected() {
        _showRejected.value = false
        resetOrderState()
    }
}