// svaga.taho.util/SseClient.kt
package svaga.taho.util

import android.util.Log
import androidx.compose.runtime.Composable
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.logging.HttpLoggingInterceptor
import okio.ByteString
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SseClient"
private const val BASE_URL = BuildConfig.BASE_URL

@Singleton
class SseClient @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
/*    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(0, TimeUnit.MILLISECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .build()*/

    private var currentJob: Job? = null

    fun subscribe(
        orderId: String,
        token: String,
        scope: CoroutineScope,
        onUpdate: (JSONObject) -> Unit,
        onError: (Throwable) -> Unit = {}
    ) {
        Log.d(TAG, "Подписываемся на SSE: $orderId")
        // Отменяем предыдущее соединение
        currentJob?.cancel()

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

        currentJob = scope.launch(Dispatchers.IO) {
            try {
                val response = okHttpClient.newCall(request).execute()
                Log.d(TAG, "SSE подключился: ${response.code}")

                if (!response.isSuccessful) {
                    withContext(Dispatchers.Main) { onError(Exception("HTTP ${response.code}")) }
                    return@launch
                }

                val body = response.body ?: return@launch
                val source = body.source()
                var buffer = StringBuilder()

                while (isActive && !source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    Log.d(TAG, "SSE ← $line")

                    buffer.append(line).append("\n")

                    if (line.isBlank()) {
                        val eventData = buffer.toString()
                        if (eventData.contains("data:")) {
                            val jsonString = eventData.lines()
                                .filter { it.startsWith("data:") }
                                .joinToString("") { it.removePrefix("data:").trim() }

                            if (jsonString.isNotEmpty()) {
                                try {
                                    val json = JSONObject(jsonString)
                                    withContext(Dispatchers.Main) {
                                        onUpdate(json)
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "JSON parse error: $jsonString", e)
                                }
                            }
                        }
                        buffer = StringBuilder()
                    }
                }
            } catch (e: Exception) {
                if (isActive) {
                    withContext(Dispatchers.Main) {
                        onError(e)
                    }
                }
            }
        }
    }

    fun disconnect() {
        currentJob?.cancel()
        currentJob = null
        Log.d(TAG, "SSE отключён")
    }
}

