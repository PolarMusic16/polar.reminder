package com.ritmo.app

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.ritmo.app.widget.RitmoWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Redibuja todos los widgets colocados en la pantalla de inicio. */
object WidgetSync {
    fun refresh(context: Context) {
        val app = context.applicationContext
        CoroutineScope(Dispatchers.Default).launch {
            try {
                RitmoWidget().updateAll(app)
            } catch (e: Exception) {
                // Si no hay widgets colocados no pasa nada.
            }
        }
    }
}
