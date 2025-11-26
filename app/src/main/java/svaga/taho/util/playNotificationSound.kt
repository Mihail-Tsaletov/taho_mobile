package svaga.taho.util

import android.content.Context
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator

fun playNotificationSound(context: Context) {
    val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    RingtoneManager.getRingtone(context, notification)?.play()
}