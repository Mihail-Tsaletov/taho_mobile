package svaga.taho.util

import android.Manifest
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.net.toUri

private const val TAG = "NotificationSound"

private var mediaPlayer: MediaPlayer? = null
private var vibrator: Vibrator? = null

private val handler = Handler(Looper.getMainLooper())
private var stopRunnable: Runnable? = null

/**
 * Запускает громкий повторяющийся звук + вибрацию
 * Используется для важных уведомлений (новый заказ для водителя)
 */
@RequiresPermission(Manifest.permission.VIBRATE)
fun playRepeatingNotificationSound(context: Context) {
    stopNotificationSound() // останавливаем предыдущий звук, если был

    try {
        // === ЗВУК ===
        val alarmUri: Uri = Uri.parse("android.resource://${context.packageName}/raw/notification_sound")

        mediaPlayer = MediaPlayer().apply {
            setDataSource(context, alarmUri)

            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)           // максимально громко
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )

            isLooping = true
            prepare()
            start()
        }

        Log.d(TAG, "Повторяющийся звук запущен (ALARM)")

        // === ВИБРАЦИЯ ===
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val pattern = longArrayOf(0, 800, 400, 800, 400) // пауза - длинная вибрация - пауза - длинная вибрация...
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0)) // 0 = repeat indefinitely
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(longArrayOf(0, 800, 400, 800, 400), 0)
        }


        stopRunnable = Runnable {
            Log.d(TAG, "Автоостановка звука через 30 секунд")
            stopNotificationSound()
        }

        handler.postDelayed(stopRunnable!!, 30_000)

    } catch (e: Exception) {
        Log.e(TAG, "Ошибка запуска повторяющегося звука", e)
        // Fallback на старый короткий звук
        playShortNotificationSound(context)
    }
}

/** Короткий звук (на случай, если повторяющийся не нужен) */
fun playShortNotificationSound(context: Context) {
    try {
        val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        RingtoneManager.getRingtone(context, notification)?.play()
    } catch (e: Exception) {
        Log.e(TAG, "Ошибка короткого звука", e)
    }
}

/** Остановить звук и вибрацию (обязательно вызывать при принятии/отклонении заказа) */
@RequiresPermission(Manifest.permission.VIBRATE)
fun stopNotificationSound() {
    try {
        mediaPlayer?.apply {
            if (isPlaying) stop()
            release()
        }
        mediaPlayer = null

        vibrator?.cancel()
        vibrator = null

        Log.d(TAG, "Звук и вибрация остановлены")
    } catch (e: Exception) {
        Log.e(TAG, "Ошибка остановки звука/вибрации", e)
    }
}