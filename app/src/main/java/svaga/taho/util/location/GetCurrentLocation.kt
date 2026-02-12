package svaga.taho.util.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.yandex.mapkit.geometry.Point

/**
 */
fun getCurrentLocation(
    context: Context,
    onSuccess: (Point, String) -> Unit,
    onError: (String) -> Unit
) {
    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    // Проверка разрешений
    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
        ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
    ) {
        onError("Нет разрешения на геолокацию")
        return
    }

    fusedLocationClient.getCurrentLocation(
        Priority.PRIORITY_HIGH_ACCURACY,
        null
    )
        .addOnSuccessListener { location ->
            if (location != null) {
                val point = Point(location.latitude, location.longitude)
                val address = "Текущая локация (${"%.6f".format(location.latitude)}, ${"%.6f".format(location.longitude)})"
                onSuccess(point, address)
            } else {
                onError("Локация не получена")
            }
        }
        .addOnFailureListener { e ->
            onError(e.message ?: "Ошибка получения локации")
        }
}