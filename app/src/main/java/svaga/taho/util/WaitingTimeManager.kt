package svaga.taho.util

import androidx.compose.remote.creation.first
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import svaga.taho.data.local.TokenManager
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

private const val FREE_WAIT_MS = 1 * 60 * 1000L   // 2 минуты бесплатно
private const val PAID_WAIT_MS = 15 * 60 * 1000L  // 15 минут платно

sealed class WaitingState {
    object Idle : WaitingState()
    data class FreeWaiting(val secondsLeft: Int) : WaitingState()   // обратный отсчёт 2 мин
    data class PaidWaiting(val secondsElapsed: Int) : WaitingState() // счётчик вверх до 15 мин
    object Expired : WaitingState()                                  // 15 минут истекли
}

@Singleton
class WaitingTimerManager @Inject constructor(
    private val tokenManager: TokenManager
) {
    private val _state = MutableStateFlow<WaitingState>(WaitingState.Idle)
    val state: StateFlow<WaitingState> = _state.asStateFlow()

    // Время окончания платного ожидания в виде строки для отправки на сервер
    private val _paidEndTime = MutableStateFlow<String?>(null)
    val paidEndTime: StateFlow<String?> = _paidEndTime.asStateFlow()

    private var timerJob: Job? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())

    /**
     * Вызывай когда водитель нажал "Я на месте" (статус ARRIVED).
     * Восстанавливает таймер из DataStore если приложение было закрыто.
     */
    suspend fun onArrived(scope: CoroutineScope) {
        val savedArrivedAt = tokenManager.arrivedAtFlow.value()

        val arrivedAt = if (savedArrivedAt > 0L) {
            // Восстанавливаем после перезапуска
            savedArrivedAt
        } else {
            // Первый старт — сохраняем текущее время
            val now = System.currentTimeMillis()
            tokenManager.saveArrivedAt(now)
            now
        }

        startTimer(scope, arrivedAt)
    }

    private fun startTimer(scope: CoroutineScope, arrivedAt: Long) {
        timerJob?.cancel()
        timerJob = scope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                val elapsed = now - arrivedAt

                when {
                    elapsed < FREE_WAIT_MS -> {
                        // Бесплатное ожидание — обратный отсчёт
                        val secondsLeft = ((FREE_WAIT_MS - elapsed) / 1000).toInt()
                        _state.value = WaitingState.FreeWaiting(secondsLeft)
                    }

                    elapsed < FREE_WAIT_MS + PAID_WAIT_MS -> {
                        // Платное ожидание — считаем вверх
                        val paidElapsed = elapsed - FREE_WAIT_MS
                        val secondsElapsed = (paidElapsed / 1000).toInt()
                        _state.value = WaitingState.PaidWaiting(secondsElapsed)

                        val minutesElapsed = paidElapsed / 1000.0 / 60.0
                        _paidEndTime.value = String.format("%.1f", minutesElapsed).replace(",", ".")

                        // Сохраняем время начала платного ожидания если ещё не сохранено
                        val savedPaidStart = tokenManager.paidWaitStartFlow.value()
                        if (savedPaidStart == 0L) {
                            val paidStartMs = arrivedAt + FREE_WAIT_MS
                            tokenManager.savePaidWaitStart(paidStartMs)
                        }
                    }

                    else -> {
                        // 15 минут истекли
                        val paidElapsed = elapsed - FREE_WAIT_MS
                        val minutesElapsed = paidElapsed / 1000.0 / 60.0
                        // Форматируем как строку с одним знаком после запятой
                        _paidEndTime.value = String.format("%.1f", minutesElapsed).replace(",", ".")
                        _state.value = WaitingState.Expired
                        timerJob?.cancel()
                        break
                    }
                }

                delay(1000)
            }
        }
    }
    fun startPaidWaiting(scope: CoroutineScope) {
        val paidStartMs = System.currentTimeMillis()
        timerJob?.cancel()
        timerJob = scope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                val paidElapsed = now - paidStartMs
                val secondsElapsed = (paidElapsed / 1000).toInt()

                if (paidElapsed < PAID_WAIT_MS) {
                    _state.value = WaitingState.PaidWaiting(secondsElapsed)
                    val minutesElapsed = paidElapsed / 1000.0 / 60.0
                    _paidEndTime.value = String.format("%.1f", minutesElapsed).replace(",", ".")
                } else {
                    val minutesElapsed = paidElapsed / 1000.0 / 60.0
                    _paidEndTime.value = String.format("%.1f", minutesElapsed).replace(",", ".")
                    _state.value = WaitingState.Expired
                    timerJob?.cancel()
                    break
                }

                delay(1000)
            }
        }
    }

    /**
     * Вызывай когда пассажир забран (PICKED_UP) или заказ завершён/отменён
     */
    suspend fun reset() {
        timerJob?.cancel()
        timerJob = null
        _state.value = WaitingState.Idle
        _paidEndTime.value = null
        tokenManager.clearTimers()
    }

    // Хелпер для чтения первого значения Flow
    private suspend fun <T> Flow<T>.value(): T = first()
}
