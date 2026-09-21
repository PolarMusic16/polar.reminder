package com.ritmo.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Se dispara justo cuando un recordatorio empieza a tocar. */
class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(AlarmScheduler.EXTRA_ID) ?: return
        val reminder = Store.load(context).find { it.id == id } ?: return
        // Comprueba que de verdad ya toca (por si se editó/pospuso mientras tanto).
        if (reminder.isLate()) {
            NotificationHelper.show(context, reminder)
        }
    }
}

/** Botón "Marcar como hecho" dentro de la propia notificación. */
class CompleteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(AlarmScheduler.EXTRA_ID) ?: return
        Store.complete(context, id)
        NotificationHelper.cancel(context, id)
    }
}

/** Las alarmas de AlarmManager se borran al reiniciar el teléfono; las recreamos. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            AlarmScheduler.rescheduleAll(context)
        }
    }
}
