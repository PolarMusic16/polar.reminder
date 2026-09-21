package com.ritmo.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Un recordatorio recurrente.
 *
 * El formato JSON es intencionadamente idéntico al de la versión web de Ritmo,
 * así que puedes exportar de una e importar en la otra.
 */
data class Reminder(
    val id: String = UUID.randomUUID().toString().take(10),
    val title: String,
    val emoji: String = "⏱",
    val every: Int = 1,
    val unit: String = UNIT_DAY,
    val last: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
) {
    fun everyMs(): Long = unitMs(unit) * every.coerceAtLeast(1)

    fun dueAt(): Long = last + everyMs()

    /** 1f = recién completado, 0f = ya toca (o pasado). */
    fun fraction(now: Long = System.currentTimeMillis()): Float {
        val total = everyMs()
        if (total <= 0L) return 0f
        return ((dueAt() - now).toFloat() / total.toFloat()).coerceIn(0f, 1f)
    }

    fun isLate(now: Long = System.currentTimeMillis()): Boolean = dueAt() <= now

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("emoji", emoji)
        put("every", every)
        put("unit", unit)
        put("last", last)
        put("createdAt", createdAt)
    }

    companion object {
        const val UNIT_MIN = "min"
        const val UNIT_HOUR = "h"
        const val UNIT_DAY = "d"
        const val UNIT_WEEK = "w"
        const val UNIT_MONTH = "mo"

        val UNITS = listOf(UNIT_MIN, UNIT_HOUR, UNIT_DAY, UNIT_WEEK, UNIT_MONTH)

        fun unitMs(unit: String): Long = when (unit) {
            UNIT_MIN -> 60_000L
            UNIT_HOUR -> 3_600_000L
            UNIT_WEEK -> 7L * 86_400_000L
            UNIT_MONTH -> 30L * 86_400_000L
            else -> 86_400_000L
        }

        fun fromJson(o: JSONObject): Reminder? {
            val title = o.optString("title").trim()
            if (title.isEmpty()) return null
            val unit = o.optString("unit", UNIT_DAY).let { if (UNITS.contains(it)) it else UNIT_DAY }
            val now = System.currentTimeMillis()
            return Reminder(
                id = o.optString("id").ifEmpty { UUID.randomUUID().toString().take(10) },
                title = title.take(60),
                emoji = o.optString("emoji", "⏱").ifEmpty { "⏱" },
                every = o.optInt("every", 1).coerceIn(1, 999),
                unit = unit,
                last = if (o.has("last")) o.optLong("last", now) else now,
                createdAt = if (o.has("createdAt")) o.optLong("createdAt", now) else now
            )
        }
    }
}

/**
 * Almacenamiento sencillo en SharedPreferences (un JSON con todos los
 * recordatorios). Lo usan tanto la app como el widget, así que siempre ven
 * exactamente los mismos datos.
 */
object Store {

    private const val PREFS = "ritmo_store"
    private const val KEY_ITEMS = "items"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context): List<Reminder> {
        val raw = prefs(context).getString(KEY_ITEMS, null) ?: return emptyList()
        return parseItems(raw)
    }

    fun save(context: Context, items: List<Reminder>) {
        val previousIds = load(context).map { it.id }.toSet()

        val arr = JSONArray()
        items.forEach { arr.put(it.toJson()) }
        prefs(context).edit().putString(KEY_ITEMS, arr.toString()).apply()

        // Mantiene las alarmas al día: cancela las de lo que ya no existe y
        // (re)programa la de cada recordatorio actual para su próxima fecha.
        val newIds = items.map { it.id }.toSet()
        (previousIds - newIds).forEach { AlarmScheduler.cancel(context, it) }
        items.forEach { AlarmScheduler.schedule(context, it) }

        WidgetSync.refresh(context)
    }

    fun complete(context: Context, id: String) {
        val now = System.currentTimeMillis()
        save(context, load(context).map { if (it.id == id) it.copy(last = now) else it })
    }

    fun upsert(context: Context, reminder: Reminder) {
        val items = load(context).toMutableList()
        val i = items.indexOfFirst { it.id == reminder.id }
        if (i >= 0) items[i] = reminder else items.add(reminder)
        save(context, items)
    }

    fun delete(context: Context, id: String) {
        save(context, load(context).filterNot { it.id == id })
    }

    /** Ordenados por urgencia: primero lo que ya toca. */
    fun byUrgency(items: List<Reminder>): List<Reminder> {
        val now = System.currentTimeMillis()
        return items.sortedWith(compareBy({ it.fraction(now) }, { it.dueAt() }))
    }

    // ---------- copia de seguridad ----------

    fun exportJson(context: Context): String {
        val arr = JSONArray()
        load(context).forEach { arr.put(it.toJson()) }
        return JSONObject().apply {
            put("app", "ritmo")
            put("version", 1)
            put("items", arr)
        }.toString(2)
    }

    /** Devuelve cuántos recordatorios se importaron, o -1 si el texto no es válido. */
    fun importJson(context: Context, raw: String): Int {
        val incoming = try {
            parseItems(raw)
        } catch (e: Exception) {
            return -1
        }
        if (incoming.isEmpty()) return -1
        val current = load(context).toMutableList()
        val existing = current.map { it.id }.toMutableSet()
        incoming.forEach { item ->
            val safe = if (existing.contains(item.id)) {
                item.copy(id = UUID.randomUUID().toString().take(10))
            } else item
            existing.add(safe.id)
            current.add(safe)
        }
        save(context, current)
        return incoming.size
    }

    private fun parseItems(raw: String): List<Reminder> {
        val text = raw.trim()
        val arr: JSONArray = if (text.startsWith("[")) {
            JSONArray(text)
        } else {
            JSONObject(text).optJSONArray("items") ?: JSONArray()
        }
        val out = ArrayList<Reminder>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            Reminder.fromJson(o)?.let { out.add(it) }
        }
        return out
    }

    fun demoItems(): List<Reminder> {
        val now = System.currentTimeMillis()
        fun ago(every: Int, unit: String, part: Double) =
            now - (Reminder.unitMs(unit) * every * part).toLong()
        return listOf(
            Reminder(title = "Regar las plantas", emoji = "💧", every = 5, unit = Reminder.UNIT_DAY, last = ago(5, Reminder.UNIT_DAY, 0.8)),
            Reminder(title = "Cambiar las sábanas", emoji = "🛏️", every = 2, unit = Reminder.UNIT_WEEK, last = ago(2, Reminder.UNIT_WEEK, 0.4)),
            Reminder(title = "Sacar la basura", emoji = "🗑️", every = 3, unit = Reminder.UNIT_DAY, last = ago(3, Reminder.UNIT_DAY, 1.1)),
            Reminder(title = "Descalcificar la cafetera", emoji = "☕", every = 1, unit = Reminder.UNIT_MONTH, last = ago(1, Reminder.UNIT_MONTH, 0.2)),
            Reminder(title = "Llamar a mamá", emoji = "📞", every = 1, unit = Reminder.UNIT_WEEK, last = ago(1, Reminder.UNIT_WEEK, 0.6))
        )
    }
}
