package dev.codingcoffee.bouncer.util

import android.content.Context
import android.text.format.DateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

object TimeFormat {
    private val weekday = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())
    private val monthDay = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
    private val fullDate = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())
    private val dayHeader = DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault())

    private val time24 = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
    private val time12 = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
    private val full24 = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm:ss", Locale.getDefault())
    private val full12 = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm:ss a", Locale.getDefault())

    private fun time(context: Context) = if (DateFormat.is24HourFormat(context)) time24 else time12

    private fun toLocal(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())

    fun conversation(context: Context, millis: Long): String {
        val dt = toLocal(millis)
        val today = LocalDate.now()
        val days = ChronoUnit.DAYS.between(dt.toLocalDate(), today)
        return when {
            days == 0L -> dt.format(time(context))
            days < 7 -> dt.format(weekday)
            dt.year == today.year -> dt.format(monthDay)
            else -> dt.format(fullDate)
        }
    }

    fun clock(context: Context, millis: Long): String = toLocal(millis).format(time(context))

    fun full(context: Context, millis: Long): String =
        toLocal(millis).format(if (DateFormat.is24HourFormat(context)) full24 else full12)

    fun dayHeader(millis: Long): String {
        val date = toLocal(millis).toLocalDate()
        val today = LocalDate.now()
        return when {
            date == today -> "Today"
            date == today.minusDays(1) -> "Yesterday"
            date.year == today.year -> date.format(dayHeader)
            else -> date.format(fullDate)
        }
    }

    fun sameDay(a: Long, b: Long): Boolean = toLocal(a).toLocalDate() == toLocal(b).toLocalDate()
}
