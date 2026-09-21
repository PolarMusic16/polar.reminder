package com.ritmo.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/** Crea el canal de notificaciones y muestra el aviso cuando toca un recordatorio. */
object NotificationHelper {

    const val CHANNEL_ID = "ritmo_reminders"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Recordatorios",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Avisa con sonido cuando toca hacer algo"
            enableVibration(true)
            setSound(soundUri, attrs)
        }
        nm.createNotificationChannel(channel)
    }

    fun show(context: Context, reminder: Reminder) {
        ensureChannel(context)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPi = PendingIntent.getActivity(
            context, reminder.id.hashCode(), openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val completeIntent = Intent(context, CompleteReceiver::class.java).apply {
            putExtra(AlarmScheduler.EXTRA_ID, reminder.id)
        }
        val completePi = PendingIntent.getBroadcast(
            context, reminder.id.hashCode() xor 0x5A5A, completeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("${reminder.emoji}  ${reminder.title}")
            .setContentText("Ya toca hacerlo")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(contentPi)
            .addAction(0, "Marcar como hecho", completePi)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(reminder.id.hashCode(), notif)
        } catch (e: SecurityException) {
            // El usuario no concedió el permiso de notificaciones (Android 13+); no pasa nada.
        }
    }

    fun cancel(context: Context, reminderId: String) {
        NotificationManagerCompat.from(context).cancel(reminderId.hashCode())
    }
}
