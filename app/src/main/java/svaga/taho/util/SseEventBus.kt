package svaga.taho.util

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Шина событий SSE — сервис пишет сюда, UI читает отсюда.
 * Singleton — один экземпляр на всё приложение.
 */
@Singleton
class SseEventBus @Inject constructor() {

    private val _events = MutableSharedFlow<JSONObject>(
        extraBufferCapacity = 10  // буфер на случай если UI не успевает читать
    )
    val events: SharedFlow<JSONObject> = _events.asSharedFlow()

    fun emit(json: JSONObject) {
        _events.tryEmit(json)
    }
}