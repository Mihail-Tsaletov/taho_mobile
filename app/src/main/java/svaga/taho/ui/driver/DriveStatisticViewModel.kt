package svaga.taho.ui.driver

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import svaga.taho.data.local.TokenManager
import svaga.taho.data.remote.ApiService
import svaga.taho.data.remote.OrderWeb
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject



data class StatisticsState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val weekTrips: List<DriverTrip> = emptyList(),
    val monthTrips: List<DriverTrip> = emptyList()
)

@HiltViewModel
class DriverStatisticsViewModel @Inject constructor(
    private val apiService: ApiService,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _state = MutableStateFlow(StatisticsState())
    val state = _state.asStateFlow()

    init {
        loadStatistics()
    }

    fun loadStatistics() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)

            try {
                val now = LocalDateTime.now()
                val weekStart = now.minusDays(7)
                val monthStart = now.minusMonths(1)

                // За неделю
                val weekOrders = apiService.getOrdersByDriverId(
                    token = "Bearer ${tokenManager.currentTokenValue}",
                    driverId = tokenManager.driverIdFlow.first() ?: throw Exception("Нет driverId"),
                    from = weekStart.format(DateTimeFormatter.ISO_DATE_TIME),
                    to = now.format(DateTimeFormatter.ISO_DATE_TIME)
                )

                // За месяц
                val monthOrders = apiService.getOrdersByDriverId(
                    token = "Bearer ${tokenManager.currentTokenValue}",
                    driverId = tokenManager.driverIdFlow.first() ?: throw Exception("Нет driverId"),
                    from = monthStart.format(DateTimeFormatter.ISO_DATE_TIME),
                    to = now.format(DateTimeFormatter.ISO_DATE_TIME)
                )

                val weekTrips = weekOrders.map { it.toDriverTrip() }
                val monthTrips = monthOrders.map { it.toDriverTrip() }

                _state.value = StatisticsState(
                    isLoading = false,
                    weekTrips = weekTrips,
                    monthTrips = monthTrips
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: "Ошибка загрузки статистики"
                )
            }
        }
    }

    // Расширение для преобразования
    private fun OrderWeb.toDriverTrip() = DriverTrip(
        id = orderId,
        createdAt = LocalDateTime.parse(orderTime, DateTimeFormatter.ISO_DATE_TIME),
        price = price?.toInt() ?: 0,
        startAddress = startAddress,
        endAddress = endAddress
    )
}