package svaga.taho.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

private const val REPAIR_DURATION_MS = 2 * 60 * 60 * 1000L // 2 часа
private const val WARN_BEFORE_MS = 5 * 60 * 1000L // 5 минут до конца
private const val CHANNEL_ID = "repair_timer_channel"

sealed class RepairState {
    object Idle : RepairState()
    data class OnRepair(val secondsLeft: Int) : RepairState()
    object Expired : RepairState()
}

@Singleton
class RepairTimerManager @Inject constructor() {

    private val _state = MutableStateFlow<RepairState>(RepairState.Idle)
    val state: StateFlow<RepairState> = _state.asStateFlow()

    private var timerJob: Job? = null

    fun startRepair(scope: CoroutineScope, context: Context, onExpired: () -> Unit) {
        timerJob?.cancel()
        val startMs = System.currentTimeMillis()
        createNotificationChannel(context)

        timerJob = scope.launch {
            var warningSent = false
            while (isActive) {
                val elapsed = System.currentTimeMillis() - startMs
                val remaining = REPAIR_DURATION_MS - elapsed

                if (remaining <= 0) {
                    _state.value = RepairState.Expired
                    sendNotification(
                        context,
                        title = "Время отдыха истекло",
                        message = "Вы не вышли на линию. Статус изменён на офлайн.",
                        id = 2
                    )
                    onExpired()
                    timerJob?.cancel()
                    break
                }

                if (remaining <= WARN_BEFORE_MS && !warningSent) {
                    warningSent = true
                    sendNotification(
                        context,
                        title = "Скоро конец отдыха",
                        message = "Через 5 минут время отдыха истечёт. Не забудьте выйти на линию.",
                        id = 1
                    )
                }

                _state.value = RepairState.OnRepair((remaining / 1000).toInt())
                delay(1000)
            }
        }
    }

    fun reset() {
        timerJob?.cancel()
        timerJob = null
        _state.value = RepairState.Idle
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Таймер отдыха",
                NotificationManager.IMPORTANCE_HIGH
            )
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun sendNotification(context: Context, title: String, message: String, id: Int) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(id, notification)
    }
}