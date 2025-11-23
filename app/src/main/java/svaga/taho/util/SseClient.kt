package svaga.taho.util

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.*
import okio.ByteString
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class SseClient(
    private val baseUrl: String,
    private val token: String
) {
    private var client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)     // бесконечный read timeout
        .writeTimeout(0, TimeUnit.MILLISECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    fun subscribe(orderId: Long): Flow<JSONObject> = callbackFlow {
        val request = Request.Builder()
            .url("$baseUrl/api/sse/subscribe/$orderId")
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Accept", "text/event-stream")
            .addHeader("Cache-Control", "no-cache")
            .build()

        val call = client.newCall(request)

        val response = call.execute()
        if (!response.isSuccessful) {
            trySend(JSONObject().apply { put("error", "HTTP ${response.code}") })
            close()
            return@callbackFlow
        }

        val body = response.body ?: run {
            close()
            return@callbackFlow
        }

        val source = body.source()

        var buffer = ""

        while (!isClosedForSend) {
            try {
                val line = source.readUtf8Line() ?: break
                buffer += line + "\n"

                if (line.isBlank()) {
                    // Конец события
                    if (buffer.contains("data:")) {
                        val dataLines = buffer.split("\n")
                            .filter { it.startsWith("data:") }
                            .map { it.removePrefix("data:").trim() }

                        val jsonString = dataLines.joinToString("")
                        if (jsonString.isNotEmpty()) {
                            try {
                                val json = JSONObject(jsonString)
                                trySend(json)
                            } catch (e: Exception) {
                                Log.e("SSE", "Ошибка парсинга JSON: $jsonString", e)
                            }
                        }
                    }
                    buffer = ""
                }
            } catch (e: Exception) {
                Log.e("SSE", "Ошибка чтения SSE", e)
                break
            }
        }

        awaitClose {
            response.close()
        }
    }
}