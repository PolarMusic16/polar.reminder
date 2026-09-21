package com.ritmo.app.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF

/**
 * Los widgets de Android se dibujan con RemoteViews y no admiten Canvas de
 * Compose, así que el anillo se genera como un Bitmap normal y se muestra
 * con un Image.
 */
object RingBitmap {

    // Paleta de los anillos: de más "lleno" a más urgente.
    private const val OK = 0xFF00DAFF.toInt()
    private const val WARN = 0xFF008DFC.toInt()
    private const val LATE = 0xFF0044FF.toInt()
    private const val TRACK_LIGHT = 0xFFDFDDD6.toInt()
    private const val TRACK_DARK = 0xFF3A3A36.toInt()

    fun isDark(context: Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    fun colorFor(fraction: Float): Int = when {
        fraction <= 0f -> LATE
        fraction < 0.25f -> WARN
        else -> OK
    }

    fun render(
        context: Context,
        sizeDp: Int,
        fraction: Float,
        emoji: String
    ): Bitmap {
        val density = context.resources.displayMetrics.density
        val px = (sizeDp * density).toInt().coerceAtLeast(24)
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val stroke = px * 0.105f
        val pad = stroke / 2f + px * 0.02f
        val rect = RectF(pad, pad, px - pad, px - pad)

        val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke
            color = if (isDark(context)) TRACK_DARK else TRACK_LIGHT
        }
        canvas.drawArc(rect, 0f, 360f, false, trackPaint)

        val sweep = if (fraction <= 0f) 360f else 360f * fraction
        val progPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke
            strokeCap = Paint.Cap.ROUND
            color = colorFor(fraction)
        }
        canvas.drawArc(rect, -90f, sweep, false, progPaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = px * 0.36f
            color = Color.BLACK
        }
        val metrics = textPaint.fontMetrics
        val baseline = px / 2f - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(emoji.ifEmpty { "⏱" }, px / 2f, baseline, textPaint)

        return bmp
    }
}
