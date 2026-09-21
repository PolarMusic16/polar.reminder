package com.ritmo.app

import kotlin.math.abs
import kotlin.math.roundToInt

/** Textos en castellano para intervalos de tiempo. */
object TimeText {

    private const val MIN = 60_000L
    private const val HOUR = 3_600_000L
    private const val DAY = 86_400_000L
    private const val WEEK = 7L * DAY

    fun humanize(msIn: Long): String {
        val ms = abs(msIn)
        if (ms < MIN) return "menos de 1 min"
        if (ms < HOUR) return "${(ms / MIN.toDouble()).roundToInt()} min"
        if (ms < DAY) {
            val h = (ms / HOUR).toInt()
            val m = ((ms % HOUR) / MIN.toDouble()).roundToInt()
            return if (m > 0 && h < 6) "$h h $m min" else "$h h"
        }
        if (ms < WEEK) {
            val d = (ms / DAY).toInt()
            val h = ((ms % DAY) / HOUR.toDouble()).roundToInt()
            val base = if (d == 1) "1 día" else "$d días"
            return if (h > 0 && d < 3) "$base $h h" else base
        }
        if (ms < 60 * DAY) {
            val w = (ms / WEEK).toInt()
            val d = ((ms % WEEK) / DAY.toDouble()).roundToInt()
            return if (d > 0) "$w sem $d d" else "$w sem"
        }
        val mo = (ms / (30.0 * DAY)).roundToInt()
        return if (mo == 1) "1 mes" else "$mo meses"
    }

    fun status(r: Reminder, now: Long = System.currentTimeMillis()): String {
        val left = r.dueAt() - now
        return if (left <= 0) "toca desde hace ${humanize(left)}" else "en ${humanize(left)}"
    }

    fun statusShort(r: Reminder, now: Long = System.currentTimeMillis()): String {
        val left = r.dueAt() - now
        return if (left <= 0) "¡ya!" else humanize(left)
    }

    fun unitLabel(unit: String, plural: Boolean): String = when (unit) {
        Reminder.UNIT_MIN -> if (plural) "minutos" else "minuto"
        Reminder.UNIT_HOUR -> if (plural) "horas" else "hora"
        Reminder.UNIT_WEEK -> if (plural) "semanas" else "semana"
        Reminder.UNIT_MONTH -> if (plural) "meses" else "mes"
        else -> if (plural) "días" else "día"
    }

    fun every(r: Reminder): String =
        "cada ${r.every} ${unitLabel(r.unit, r.every != 1)}"
}
