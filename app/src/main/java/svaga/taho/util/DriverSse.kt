import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import svaga.taho.data.remote.DriverOrder

object DriverSse {
    private var sseJob: Job? = null

    fun subscribe(onNewOrder: (DriverOrder) -> Unit) {
        sseJob?.cancel()
        sseJob = CoroutineScope(Dispatchers.IO).launch {
            // такой же sseSubscribe, как у клиента, но на /driver/orders/stream
            // и с твоим driverId или токеном
        }
    }

    fun acceptOrder(orderId: String) = sendStatus(orderId, "ACCEPTED")
    fun arrived(orderId: String) = sendStatus(orderId, "ARRIVED")
    private fun sendStatus(orderId: String, status: String) {
        // POST /api/driver/orders/{id}/status с новым статусом
    }


}