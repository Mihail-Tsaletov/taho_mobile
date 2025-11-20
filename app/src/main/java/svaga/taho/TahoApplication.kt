package svaga.taho

import android.app.Application
import com.yandex.mapkit.MapKitFactory
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class TahoApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // ←←←← ЭТО САМОЕ ВАЖНОЕ! Без этой строки ничего из com.yandex.* не импортируется
        MapKitFactory.setApiKey("3e3bc109-bcb9-4398-bf6e-f7bf0e760960")

        MapKitFactory.initialize(this)
    }
}