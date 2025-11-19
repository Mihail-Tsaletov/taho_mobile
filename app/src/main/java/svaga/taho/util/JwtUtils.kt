package svaga.taho.util

import android.util.Base64
import org.json.JSONObject

fun parseJwtRole(token: String): String? {
    return try {
        val payload = token.split(".")[1]
        val padded = payload.padEnd((payload.length + 3) / 4 * 4, '=')
        val decoded = String(Base64.decode(padded, Base64.URL_SAFE))
        JSONObject(decoded).optString("role") // ← если у тебя роль в другом поле — поменяй здесь
    } catch (e: Exception) {
        null
    }
}