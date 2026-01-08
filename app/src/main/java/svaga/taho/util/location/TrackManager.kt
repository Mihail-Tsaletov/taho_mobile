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

object TrackManager {
    private var trackingJob: Job? = null
    private var trackPoints = mutableListOf<Point>()

    private const val TRACKING_INTERVAL_MS = 7000L // 7 секунд

    fun startTracking(
        context: Context,
        scope: CoroutineScope,
        onPointAdded: (Point) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (trackingJob?.isActive == true) return // уже запущен



        trackPoints.clear()
        trackingJob = scope.launch {
            while (isActive) {
                try {
                    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

                    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                        ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
                    ) {
                        onError("Нет разрешения на геолокацию")
                        return@launch
                    }

                    val location = fusedLocationClient.getCurrentLocation(
                        Priority.PRIORITY_HIGH_ACCURACY,
                        null
                    ).await()

                    if (location != null) {
                        val point = Point(location.latitude, location.longitude)
                        trackPoints.add(point)
                        onPointAdded(point)
                    } else {
                        onError("Локация не получена (null)")
                    }
                } catch (e: Exception) {
                    onError("Ошибка получения точки: ${e.message}")
                }

                delay(TRACKING_INTERVAL_MS)
            }
        }
    }

    fun stopTrackingAndGetJson(): String {
        trackingJob?.cancel()
        trackingJob = null

        if (trackPoints.isEmpty()) return "[]"

        val json = buildString {
            append("[")
            trackPoints.forEachIndexed { index, point ->
                append("""{"lat":${point.latitude},"lon":${point.longitude}}""")
                if (index < trackPoints.size - 1) append(",")
            }
            append("]")
        }

        trackPoints.clear() // очищаем после получения
        return json
    }


    }
