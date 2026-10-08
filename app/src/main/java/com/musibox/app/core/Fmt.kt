package com.musibox.app.core

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Formatação de textos exibidos ao usuário (pt-BR). */
object Fmt {
    val ptBR: Locale = Locale.forLanguageTag("pt-BR")

    fun duration(ms: Long): String {
        val total = (ms / 1000).coerceAtLeast(0)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.ROOT, "%02d:%02d", m, s)
    }

    fun durationSec(sec: Long): String = duration(sec * 1000)

    fun size(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var i = 0
        while (value >= 1024 && i < units.lastIndex) {
            value /= 1024
            i++
        }
        return if (i == 0) "$bytes B" else String.format(ptBR, "%.1f %s", value, units[i])
    }

    fun speed(bytesPerSecond: Long): String = size(bytesPerSecond) + "/s"

    fun dateTime(millis: Long): String {
        if (millis <= 0) return "—"
        val now = Calendar.getInstance()
        val then = Calendar.getInstance().apply { timeInMillis = millis }
        val time = SimpleDateFormat("HH:mm", ptBR).format(Date(millis))
        return when {
            sameDay(now, then) -> "Hoje, $time"
            isYesterday(now, then) -> "Ontem, $time"
            else -> SimpleDateFormat("dd/MM/yyyy HH:mm", ptBR).format(Date(millis))
        }
    }

    fun dayHeader(millis: Long): String {
        val now = Calendar.getInstance()
        val then = Calendar.getInstance().apply { timeInMillis = millis }
        return when {
            sameDay(now, then) -> "Hoje"
            isYesterday(now, then) -> "Ontem"
            else -> SimpleDateFormat("dd 'de' MMMM 'de' yyyy", ptBR).format(Date(millis))
        }
    }

    private fun sameDay(a: Calendar, b: Calendar) =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

    private fun isYesterday(now: Calendar, then: Calendar): Boolean {
        val y = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
        return sameDay(y, then)
    }

    fun plural(count: Int, singular: String, plural: String) = "$count ${if (count == 1) singular else plural}"
}
