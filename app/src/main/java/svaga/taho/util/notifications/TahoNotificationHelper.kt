package svaga.taho.util.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import svaga.taho.MainActivity // ← замени на свой пакет если нужно

object TahoNotificationHelper {

    // ── Каналы ────────────────────────────────────────────────────
    const val CHANNEL_NEW_ORDER     = "taho_new_order"      // новый заказ (водитель)
    const val CHANNEL_ORDER_STATUS  = "taho_order_status"   // статусы (пассажир)
    const val CHANNEL_DRIVER_STATUS = "taho_driver_status"  // статусы (водитель)
    const val CHANNEL_FOREGROUND    = "taho_foreground"     // для foreground service

    // ── ID уведомлений ────────────────────────────────────────────
    const val NOTIF_FOREGROUND      = 1   // foreground service — всегда занят этот слот
    private const val NOTIF_NEW_ORDER     = 100
    private const val NOTIF_ORDER_STATUS  = 101
    private const val NOTIF_DRIVER_STATUS = 102

    /**
     * Вызвать один раз при старте приложения — в Application.onCreate()
     */
    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(NotificationManager::class.java)
        val defaultSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val audioAttr = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        // Новый заказ — максимальный приоритет, агрессивная вибрация
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_NEW_ORDER, "Новый заказ", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Входящий заказ для водителя"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 400, 200, 400, 200, 400)
                setSound(defaultSound, audioAttr)
                enableLights(true)
            }
        )

        // Статусы для пассажира — высокий приоритет, мягкая вибрация
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ORDER_STATUS, "Статус поездки", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Обновления статуса заказа для пассажира"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 150, 250)
                setSound(defaultSound, audioAttr)
            }
        )

        // Статусы для водителя — обычный приоритет
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_DRIVER_STATUS, "Статус поездки (водитель)", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Завершение и прочие статусы для водителя"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 200, 100, 200)
            }
        )

        // Foreground service — минимально заметный, без звука
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_FOREGROUND, "Фоновая работа", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Поддержание соединения с сервером"
                setSound(null, null)
                enableVibration(false)
            }
        )
    }

    // ─────────────────────────────────────────────────────────────
    //  FOREGROUND SERVICE — постоянное уведомление (почти невидимое)
    // ─────────────────────────────────────────────────────────────

    fun buildForegroundNotification(context: Context) =
        NotificationCompat.Builder(context, CHANNEL_FOREGROUND)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation) // ← замени на свою иконку
            .setContentTitle("Taho")
            .setContentText("Ожидание обновлений заказа...")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .setOngoing(true)   // нельзя смахнуть
            .build()

    // ─────────────────────────────────────────────────────────────
    //  ВОДИТЕЛЬ
    // ─────────────────────────────────────────────────────────────

    /** Водитель: пришёл новый заказ */
    fun notifyDriverNewOrder(context: Context, fromAddress: String, toAddress: String, price: String?) {
        val priceText = if (!price.isNullOrBlank() && price != "null") " • $price ₽" else " • по таксометру"
        show(
            context   = context,
            channelId = CHANNEL_NEW_ORDER,
            notifId   = NOTIF_NEW_ORDER,
            title     = "🚖 Новый заказ!",
            body      = "Откуда: $fromAddress\nКуда: $toAddress$priceText",
            priority  = NotificationCompat.PRIORITY_MAX
        )
        vibrate(context, longArrayOf(0, 400, 200, 400, 200, 400))
    }

    /** Водитель: заказ завершён */
    fun notifyDriverCompleted(context: Context, price: String?) {
        val body = if (!price.isNullOrBlank() && price != "null") "Сумма: $price ₽" else "Поездка завершена"
        show(
            context   = context,
            channelId = CHANNEL_DRIVER_STATUS,
            notifId   = NOTIF_DRIVER_STATUS,
            title     = "✅ Заказ завершён",
            body      = body,
            priority  = NotificationCompat.PRIORITY_DEFAULT
        )
    }

    // ─────────────────────────────────────────────────────────────
    //  ПАССАЖИР
    // ─────────────────────────────────────────────────────────────

    /** Пассажир: водитель принял заказ */
    fun notifyPassengerAccepted(context: Context, driverName: String?, timeToArrive: String?) {
        val driver = driverName?.takeIf { it.isNotBlank() } ?: "Водитель"
        val eta = timeToArrive?.takeIf { it.isNotBlank() }?.let { " • будет через $it" } ?: ""
        show(
            context   = context,
            channelId = CHANNEL_ORDER_STATUS,
            notifId   = NOTIF_ORDER_STATUS,
            title     = "🚗 Заказ принят",
            body      = "$driver едет к вам$eta",
            priority  = NotificationCompat.PRIORITY_HIGH
        )
        vibrate(context, longArrayOf(0, 250, 150, 250))
    }

    /** Пассажир: водитель приехал на место */
    fun notifyPassengerArrived(context: Context, driverName: String?) {
        val driver = driverName?.takeIf { it.isNotBlank() } ?: "Водитель"
        show(
            context   = context,
            channelId = CHANNEL_ORDER_STATUS,
            notifId   = NOTIF_ORDER_STATUS,
            title     = "📍 Водитель на месте",
            body      = "$driver ждёт вас",
            priority  = NotificationCompat.PRIORITY_HIGH
        )
        vibrate(context, longArrayOf(0, 300, 100, 300))
    }

    /** Пассажир: поездка началась */
    fun notifyPassengerTripStarted(context: Context) {
        show(
            context   = context,
            channelId = CHANNEL_ORDER_STATUS,
            notifId   = NOTIF_ORDER_STATUS,
            title     = "🛣️ Поездка началась",
            body      = "Вы в пути. Хорошей дороги!",
            priority  = NotificationCompat.PRIORITY_DEFAULT
        )
    }

    /** Пассажир: поездка завершена */
    fun notifyPassengerCompleted(context: Context, price: String?) {
        val body = if (!price.isNullOrBlank() && price != "null")
            "Стоимость поездки: $price ₽" else "Спасибо, что воспользовались Taho!"
        show(
            context   = context,
            channelId = CHANNEL_ORDER_STATUS,
            notifId   = NOTIF_ORDER_STATUS,
            title     = "🏁 Поездка завершена",
            body      = body,
            priority  = NotificationCompat.PRIORITY_DEFAULT
        )
    }

    // ─────────────────────────────────────────────────────────────
    //  ВНУТРЕННИЕ УТИЛИТЫ
    // ─────────────────────────────────────────────────────────────

    private fun show(
        context: Context,
        channelId: String,
        notifId: Int,
        title: String,
        body: String,
        priority: Int
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, notifId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(priority)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setSound(soundUri)
            .build()

        // ── Проверка разрешения перед показом ────────────────────────
        val notifManager = NotificationManagerCompat.from(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ — проверяем разрешение POST_NOTIFICATIONS
            if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED
            ) {
                notifManager.notify(notifId, notification)
            } else {
                Log.w("TahoNotification", "Нет разрешения на уведомления — пропускаем")
            }
        } else {
            // Android 12 и ниже — разрешение не нужно
            notifManager.notify(notifId, notification)
        }
    }

    /** Вибрация с паттерном — отдельно от канала, для надёжности */
    private fun vibrate(context: Context, pattern: LongArray) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, -1)
            }
        }
    }
}