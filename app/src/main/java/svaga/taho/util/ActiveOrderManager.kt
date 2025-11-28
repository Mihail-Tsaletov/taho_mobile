package svaga.taho.utils

import ActiveOrderResponse
import android.util.Log
import kotlinx.coroutines.flow.*
import svaga.taho.data.local.TokenManager
import svaga.taho.data.remote.ApiService
import svaga.taho.data.remote.DriverOrder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActiveOrderManager @Inject constructor(
    private val apiService: ApiService,
    private val tokenManager: TokenManager
) {
    private val _activeOrder = MutableStateFlow<ActiveOrderResponse?>(null)
    private val _activeOrderDriver = MutableStateFlow<DriverOrder?>(null)
    val activeOrder: StateFlow<ActiveOrderResponse?> = _activeOrder.asStateFlow()
    val activeOrderDriver: StateFlow<DriverOrder?> = _activeOrderDriver.asStateFlow()

    suspend fun loadActiveOrderForClient() {
        try {
            val token = tokenManager.tokenFlow.first() ?: return
            val response = apiService.getActiveOrders("Bearer $token")
            if (response.isSuccessful) {
                val orders = response.body() ?: emptyList()
                _activeOrder.value = orders.firstOrNull()
                Log.d("ActiveOrder", "Загружен активный заказ: ${_activeOrder.value}")
            }
        } catch (e: Exception) {
            Log.e("ActiveOrder", "Ошибка загрузки активного заказа", e)
        }
    }

    suspend fun loadActiveOrderForDriver() {
        try {
            val token = tokenManager.tokenFlow.first() ?: return
            val response = apiService.getActiveOrdersForDriver("Bearer $token")
            if (response.isSuccessful) {
                val orders = response.body() ?: emptyList()
                _activeOrderDriver.value = orders.firstOrNull()
                Log.d("ActiveOrder", "Загружен активный заказ: ${_activeOrder.value}")
            }
        } catch (e: Exception) {
            Log.e("ActiveOrder", "Ошибка загрузки активного заказа", e)
        }
    }

    fun clear() {
        _activeOrder.value = null
    }
}