package svaga.taho.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import svaga.taho.data.local.TokenManager
import svaga.taho.di.AppModule
import svaga.taho.util.notifications.TahoNotificationHelper
import dagger.hilt.android.EntryPointAccessors
import svaga.taho.util.SseEventBus
import javax.inject.Inject

private const val TAG = "TahoSseService"

// Ключи для Intent extras
const val EXTRA_ORDER_ID  = "extra_order_id"
const val EXTRA_ROLE      = "extra_role"   // "DRIVER" или "PASSENGER"

private var isShuttingDown = false

/**
 * Foreground Service — держит SSE соединение живым когда приложение в фоне.
 *
 * Запускай его сразу после создания заказа (для пассажира)
 * или при выходе на линию (для водителя).
 * Останавливай при завершении / отмене заказа.
 *
 * Запуск:
 *   TahoSseService.start(context, orderId = "xxx", role = "PASSENGER")
 *
 * Остановка:
 *   TahoSseService.stop(context)
 */
@AndroidEntryPoint
class TahoSseService : Service() {

    @Inject lateinit var tokenManager: TokenManager
    @Inject lateinit var sseEventBus: SseEventBus  // ← добавь это


    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var sseJob: Job? = null

    // ── Companion — удобные методы запуска/остановки ──────────────
    companion object {
        fun start(context: Context, orderId: String, role: String) {
            val intent = Intent(context, TahoSseService::class.java).apply {
                putExtra(EXTRA_ORDER_ID, orderId)
                putExtra(EXTRA_ROLE, role)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TahoSseService::class.java))
        }
    }

    @SuppressLint("ForegroundServiceType")
    override fun onCreate() {
        super.onCreate()
        // Сразу переводим в foreground — иначе Android убьёт сервис через 5 секунд
        startForeground(
            TahoNotificationHelper.NOTIF_FOREGROUND,
            TahoNotificationHelper.buildForegroundNotification(this)
        )
        Log.d(TAG, "Сервис создан, foreground запущен")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val orderId = intent?.getStringExtra(EXTRA_ORDER_ID) ?: run {
            Log.w(TAG, "orderId не передан — сервис останавливается")
            stopSelf()
            return START_NOT_STICKY
        }
        val role = intent.getStringExtra(EXTRA_ROLE) ?: "PASSENGER"

        Log.d(TAG, "onStartCommand: orderId=$orderId, role=$role")

        // Перезапускаем подписку (на случай если сервис переиспользуется)
        sseJob?.cancel()
        sseJob = serviceScope.launch {
            subscribeToSse(orderId, role)
        }

        // START_STICKY — Android перезапустит сервис если убьёт его
        return START_STICKY
    }

    override fun onDestroy() {
        isShuttingDown = false
        super.onDestroy()
        sseJob?.cancel()
        serviceScope.cancel()
        Log.d(TAG, "Сервис уничтожен")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ─────────────────────────────────────────────────────────────
    //  SSE ПОДПИСКА
    // ─────────────────────────────────────────────────────────────

    private suspend fun subscribeToSse(orderId: String, role: String) {
        val token = tokenManager.tokenFlow.first() ?: run {
            Log.w(TAG, "Токен пустой — подписка отменена")
            stopSelf()
            return
        }

        val sseClient = EntryPointAccessors
            .fromApplication(applicationContext, AppModule.ApiProvider::class.java)
            .sseClient()

        Log.d(TAG, "Подписываемся на SSE: orderId=$orderId")

        try {
            sseClient.subscribe(
                orderId = orderId,
                token   = token,
                scope   = serviceScope,
                onUpdate = { json ->
                    val status      = json.optString("status").takeIf { it.isNotBlank() } ?: return@subscribe
                    val driverName  = json.optString("driverName").takeIf { it.isNotBlank() }
                    val timeToArrive= json.optString("timeToArrive").takeIf { it.isNotBlank() }
                    val price       = json.optString("price").takeIf { it.isNotBlank() }
                    val fromAddress = json.optString("startAddress")
                    val toAddress   = json.optString("endAddress")

                    Log.d(TAG, "SSE получен: role=$role, status=$status")

                    sseEventBus.emit(json)

                    when (role) {
                        "DRIVER"    -> handleDriverEvent(status, fromAddress, toAddress, price)
                        "PASSENGER" -> handlePassengerEvent(status, driverName, timeToArrive, price)
                    }

                    // Останавливаем сервис когда заказ завершён
                    if (status in listOf("COMPLETED", "CANCELLED", "REJECTED")) {
                        isShuttingDown = true
                        Log.d(TAG, "Заказ завершён — останавливаем сервис с задержкой")
                        serviceScope.launch {
                            delay(500) // даём время UI получить событие через шину
                            if (role == "DRIVER") {
                                Log.d(TAG, "Водитель на линии — перезапускаем подписку на новые заказы")
                                TahoSseService.start(
                                    context = applicationContext,           // ← исправлено
                                    orderId = "driver",
                                    role = "DRIVER"
                                )
                            } else {
                                Log.d(TAG, "Пассажир — полностью останавливаем сервис")
                                stopSelf()
                            }
                        }
                    }
                },
                onError = { e ->
                    Log.e(TAG, "SSE ошибка в сервисе", e)
                    // Можно добавить retry-логику здесь
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Критическая ошибка SSE в сервисе", e)
            stopSelf()
        }
    }

    // ── Обработчики событий ───────────────────────────────────────

    private fun handleDriverEvent(
        status: String,
        fromAddress: String,
        toAddress: String,
        price: String?
    ) {
        when (status) {
            "ASSIGNED"  -> TahoNotificationHelper.notifyDriverNewOrder(applicationContext, fromAddress, toAddress, price)
            "COMPLETED" -> TahoNotificationHelper.notifyDriverCompleted(applicationContext, price)
        }
    }

    private fun handlePassengerEvent(
        status: String,
        driverName: String?,
        timeToArrive: String?,
        price: String?
    ) {
        when (status) {
            "ACCEPTED", "PICKED_UP" -> TahoNotificationHelper.notifyPassengerAccepted(applicationContext, driverName, timeToArrive)
            "ARRIVED"               -> TahoNotificationHelper.notifyPassengerArrived(applicationContext, driverName)
            "IN_PROGRESS"           -> TahoNotificationHelper.notifyPassengerTripStarted(applicationContext)
            "COMPLETED"             -> TahoNotificationHelper.notifyPassengerCompleted(applicationContext, price)
        }
    }
}