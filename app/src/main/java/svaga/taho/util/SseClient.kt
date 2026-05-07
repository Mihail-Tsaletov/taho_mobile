package svaga.taho.util

import android.util.Log
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SseClient"

@Singleton
class SseClient @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    private var currentJob: Job? = null

    fun subscribe(
        orderId: String,
        token: String,
        scope: CoroutineScope,
        onUpdate: (JSONObject) -> Unit,
        onError: (Throwable) -> Unit = {}
    ) {
        Log.d(TAG, "Подписываемся на SSE: $orderId")
        currentJob?.cancel()

        currentJob = scope.launch(Dispatchers.IO) {
            var retryDelayMs = 2_000L      // начальная задержка 2 сек
            val maxDelayMs   = 30_000L     // максимум 30 сек
            var attempt      = 0

            // Крутимся пока корутина жива — при обрыве переподключаемся
            while (isActive) {
                attempt++
                Log.d(TAG, "SSE попытка подключения #$attempt (orderId=$orderId)")

                try {
                    connectAndListen(orderId, token, onUpdate)
                    // Если дошли сюда — соединение завершилось штатно (сервер закрыл)
                    // Всё равно переподключаемся — вдруг сервер просто перезагрузился
                    Log.d(TAG, "SSE соединение завершено штатно — переподключение через ${retryDelayMs}ms")
                } catch (e: CancellationException) {
                    // Корутина отменена снаружи — выходим без retry
                    Log.d(TAG, "SSE отменён (CancellationException) — выходим")
                    break
                } catch (e: Exception) {
                    if (!isActive) break  // корутина уже отменена
                    Log.e(TAG, "SSE ошибка (#$attempt): ${e.message}")
                    withContext(Dispatchers.Main) { onError(e) }
                }

                // Ждём перед следующей попыткой (только если корутина ещё жива)
                if (isActive) {
                    Log.d(TAG, "Следующая попытка через ${retryDelayMs / 1000}с...")
                    delay(retryDelayMs)
                    // Экспоненциальный рост задержки: 2s → 4s → 8s → ... → 30s
                    retryDelayMs = (retryDelayMs * 2).coerceAtMost(maxDelayMs)
                }
            }
        }
    }

    /**
     * Одна попытка подключения и чтения SSE потока.
     * Бросает исключение при любой ошибке сети — вызывающий код решает retry или нет.
     */
    private suspend fun connectAndListen(
        orderId: String,
        token: String,
        onUpdate: (JSONObject) -> Unit
    ) {
        val url = if (orderId == "driver") {
            "${BuildConfig.BASE_URL}api/sse/subscribe/driver"
        } else {
            "${BuildConfig.BASE_URL}api/sse/subscribe/$orderId"
        }

        val request = Request.Builder()
            .url(url)
            .addHeader("Accept", "text/event-stream")
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Cache-Control", "no-cache")
            .build()

        val response = okHttpClient.newCall(request).execute()
        Log.d(TAG, "SSE подключился: HTTP ${response.code}")

        if (!response.isSuccessful) {
            response.close()
            throw Exception("HTTP ${response.code}")
        }

        val source = response.body?.source()
            ?: throw Exception("SSE body is null")

        var buffer = StringBuilder()

        // Читаем построчно пока корутина активна и поток не исчерпан
        while (currentCoroutineContext().isActive && !source.exhausted()) {
            val line = source.readUtf8Line() ?: break
            Log.d(TAG, "SSE ← $line")

            buffer.append(line).append("\n")

            // Пустая строка = конец одного события
            if (line.isBlank()) {
                val eventData = buffer.toString()
                buffer = StringBuilder()

                if (!eventData.contains("data:")) continue

                val jsonString = eventData.lines()
                    .filter { it.startsWith("data:") }
                    .joinToString("") { it.removePrefix("data:").trim() }

                if (jsonString.isEmpty()) continue

                try {
                    val json = JSONObject(jsonString)
                    withContext(Dispatchers.Main) { onUpdate(json) }
                } catch (e: Exception) {
                    Log.e(TAG, "JSON parse error: $jsonString", e)
                }
            }
        }

        response.close()
    }

    fun disconnect() {
        currentJob?.cancel()
        currentJob = null
        Log.d(TAG, "SSE отключён")
    }
}