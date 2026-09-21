package com.ritmo.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.ritmo.app.MainActivity
import com.ritmo.app.Reminder
import com.ritmo.app.Store
import com.ritmo.app.TimeText

private val KeyReminderId = ActionParameters.Key<String>("reminderId")

private val BgColor = ColorProvider(day = Color(0xFFFFFFFF), night = Color(0xFF1C1C1A))
private val TextColor = ColorProvider(day = Color(0xFF1A1917), night = Color(0xFFF0EFEC))
private val DimColor = ColorProvider(day = Color(0xFF6F6C66), night = Color(0xFF9C9891))
private val LateColor = ColorProvider(day = Color(0xFF0044FF), night = Color(0xFF4D9CFF))

/** Tamaño de cada celda (anillo + dos líneas de texto). */
private const val TILE_W = 76
private const val TILE_H = 88
private const val RING_DP = 52

class RitmoWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val items = Store.byUrgency(Store.load(context))
        provideContent { Body(context, items) }
    }

    @Composable
    private fun Body(context: Context, items: List<Reminder>) {
        val size = LocalSize.current
        val columns = ((size.width.value - 12) / TILE_W).toInt().coerceIn(1, 6)
        val rows = ((size.height.value - 12) / TILE_H).toInt().coerceIn(1, 5)
        val visible = items.take(columns * rows)

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .appWidgetBackground()
                .background(BgColor)
                .cornerRadius(18.dp)
                .padding(6.dp)
                .clickable(actionStartActivity<MainActivity>()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (visible.isEmpty()) {
                Box(
                    modifier = GlanceModifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Toca para añadir tu primer recordatorio",
                        style = TextStyle(
                            color = DimColor,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    )
                }
            } else {
                visible.chunked(columns).forEach { rowItems ->
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        rowItems.forEach { item -> Tile(context, item) }
                        // Rellena los huecos de la última fila para que no se estire.
                        repeat(columns - rowItems.size) {
                            Box(modifier = GlanceModifier.size(TILE_W.dp, TILE_H.dp)) {}
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun Tile(context: Context, item: Reminder) {
        val fraction = item.fraction()
        val bmp = RingBitmap.render(context, RING_DP, fraction, item.emoji)
        Column(
            modifier = GlanceModifier
                .size(TILE_W.dp, TILE_H.dp)
                .padding(2.dp)
                .clickable(
                    actionRunCallback<CompleteAction>(
                        actionParametersOf(KeyReminderId to item.id)
                    )
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.Top
        ) {
            Image(
                provider = ImageProvider(bmp),
                contentDescription = "${item.title}: ${TimeText.status(item)}",
                modifier = GlanceModifier.size(RING_DP.dp)
            )
            Text(
                text = item.title,
                maxLines = 1,
                style = TextStyle(color = TextColor, fontSize = 10.sp, textAlign = TextAlign.Center)
            )
            Text(
                text = TimeText.statusShort(item),
                maxLines = 1,
                style = TextStyle(
                    color = if (item.isLate()) LateColor else DimColor,
                    fontSize = 9.sp,
                    textAlign = TextAlign.Center
                )
            )
        }
    }
}

/** Al tocar un anillo en el widget, se marca como hecho y se reinicia el ciclo. */
class CompleteAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val id = parameters[KeyReminderId] ?: return
        Store.complete(context, id)
        RitmoWidget().updateAll(context)
    }
}

class RitmoWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RitmoWidget()
}
