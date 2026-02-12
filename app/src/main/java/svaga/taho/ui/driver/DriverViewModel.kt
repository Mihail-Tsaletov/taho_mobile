package svaga.taho.ui.driver

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DriverViewModel @Inject constructor() : ViewModel() {

    private val _isArrived = MutableStateFlow(false)
    val isArrived = _isArrived.asStateFlow()

    private val _isPickedUp = MutableStateFlow(false)
    val isPickedUp = _isPickedUp.asStateFlow()

    fun setArrived(arrived: Boolean) {
        viewModelScope.launch {
            _isArrived.value = arrived
        }
    }

    fun setPickedUp(pickedUp: Boolean) {
        viewModelScope.launch {
            _isPickedUp.value = pickedUp
        }
    }

    fun resetOrderStates() {
        setArrived(false)
        setPickedUp(false)
    }
}