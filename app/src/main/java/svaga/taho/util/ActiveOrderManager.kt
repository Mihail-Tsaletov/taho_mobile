package svaga.taho.utils

import ActiveOrderResponse
import android.util.Log
import kotlinx.coroutines.flow.*
import svaga.taho.data.local.TokenManager
import svaga.taho.data.remote.ApiService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActiveOrderManager @Inject constructor(
    private val apiService: ApiService,
    private val tokenManager: TokenManager
) {
    private val _activeOrder = MutableStateFlow<ActiveOrderResponse?>(null)
    val activeOrder: StateFlow<ActiveOrderResponse?> = _activeOrder.asStateFlow()

    suspend fun loadActiveOrder() {
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

    fun clear() {
        _activeOrder.value = null
    }
}