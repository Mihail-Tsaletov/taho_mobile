// TrackManager.kt
package svaga.taho.util.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.yandex.mapkit.geometry.Point
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import java.util.concurrent.atomic.AtomicBoolean

object TrackManager {

    private val trackingScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var trackingJob: Job? = null
    private val trackPoints = mutableListOf<Point>()

    private val isTrackingActive = AtomicBoolean(false)

    private const val TRACKING_INTERVAL_MS = 7000L

    /** Запуск трекинга (можно вызывать多次 — не запустит второй раз) */
    fun startTracking(
        context: Context,
        onPointAdded: (Point) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (isTrackingActive.getAndSet(true)) return

        trackingJob = trackingScope.launch {
            while (isActive) {
                try {
                    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

                    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                        ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
                    ) {
                        withContext(Dispatchers.Main) { onError("Нет разрешения на геолокацию") }
                        return@launch
                    }

                    val location = fusedLocationClient.getCurrentLocation(
                        Priority.PRIORITY_HIGH_ACCURACY,
                        null
                    ).await()

                    if (location != null) {
                        val point = Point(location.latitude, location.longitude)
                        trackPoints.add(point)

                        // Вызываем колбэк на главном потоке (для UI)
                        withContext(Dispatchers.Main) {
                            onPointAdded(point)
                        }
                    } else {
                        withContext(Dispatchers.Main) { onError("Локация не получена (null)") }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { onError("Ошибка получения точки: ${e.message}") }
                }

                delay(TRACKING_INTERVAL_MS)
            }
        }
    }

    /** Остановить + вернуть JSON (для отправки на сервер) */
    fun stopTrackingAndGetJson(): String {
        trackingJob?.cancel()
        trackingJob = null
        isTrackingActive.set(false)

        if (trackPoints.isEmpty()) return "[]"

        val json = buildString {
            append("[")
            trackPoints.forEachIndexed { index, point ->
                append("""{"lat":${point.latitude},"lon":${point.longitude}}""")
                if (index < trackPoints.size - 1) append(",")
            }
            append("]")
        }

        // trackPoints.clear() ← закомментировано намеренно, чтобы можно было продолжить трек после отправки
        return json
    }

    /** Получить текущий JSON БЕЗ остановки трекинга */
    fun getCurrentTrackJson(): String {
        if (trackPoints.isEmpty()) return "[]"

        return buildString {
            append("[")
            trackPoints.forEachIndexed { index, point ->
                append("""{"lat":${point.latitude},"lon":${point.longitude}}""")
                if (index < trackPoints.size - 1) append(",")
            }
            append("]")
        }
    }

    fun clearTrack() {
        trackPoints.clear()
    }

    fun isTracking(): Boolean = isTrackingActive.get()
}