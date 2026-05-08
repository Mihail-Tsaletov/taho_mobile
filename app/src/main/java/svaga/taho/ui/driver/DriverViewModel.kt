package svaga.taho.ui.driver

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import svaga.taho.data.remote.DriverOrder
import javax.inject.Inject

@HiltViewModel
class DriverViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle  // если хочешь пережить пересоздание
) : ViewModel() {

    // Текущий активный заказ (null — заказа нет)
    private val _currentOrder = MutableStateFlow<DriverOrder?>(null)
    val currentOrder = _currentOrder.asStateFlow()

    // Состояние "Я на месте" (ARRIVED)
    private val _isArrived = MutableStateFlow(false)
    val isArrived = _isArrived.asStateFlow()

    // Состояние "Забрал пассажира" (PICKED_UP)
    private val _isPickedUp = MutableStateFlow(false)
    val isPickedUp = _isPickedUp.asStateFlow()

    // Трекинг запущен?
    private val _isTracking = MutableStateFlow(false)
    val isTracking = _isTracking.asStateFlow()

    // Нужно ли вести трек (не в городе)
    private val _shouldTrack = MutableStateFlow(false)
    val shouldTrack = _shouldTrack.asStateFlow()

    private val _showRejected = MutableStateFlow(false)

    val showRejected: StateFlow<Boolean> = _showRejected.asStateFlow()

    private val _savedPaidWaitingMinutes = MutableStateFlow<String?>(null)
    val savedPaidWaitingMinutes: StateFlow<String?> = _savedPaidWaitingMinutes.asStateFlow()

    fun savePaidWaitingMinutes(minutes: String?) {
        _savedPaidWaitingMinutes.value = minutes
    }

    fun clearPaidWaitingMinutes() {
        _savedPaidWaitingMinutes.value = null
    }

    // Установка текущего заказа
    fun setCurrentOrder(order: DriverOrder?) {
        viewModelScope.launch {
            _currentOrder.value = order
        }
    }

    fun setArrived(value: Boolean) {
        viewModelScope.launch {
            _isArrived.value = value
        }
    }

    fun setPickedUp(value: Boolean) {
        viewModelScope.launch {
            _isPickedUp.value = value
        }
    }

    fun setTracking(value: Boolean) {
        viewModelScope.launch {
            _isTracking.value = value
        }
    }

    fun setShouldTrack(value: Boolean) {
        viewModelScope.launch {
            _shouldTrack.value = value
        }
    }

    // Полный сброс всех состояний заказа (при завершении/отмене)
    fun resetOrderStates() {
        viewModelScope.launch {
            _currentOrder.value = null
            _isArrived.value = false
            _isPickedUp.value = false
            _isTracking.value = false
            _shouldTrack.value = false
        }
    }

    fun onOrderRejected() {
        _showRejected.value = true
    }

    fun dismissRejected() {
        _showRejected.value = false
    }
}