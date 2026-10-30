package com.carlogbook.app.domain

import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Small helpers to show dates, kilometres and reminders as readable text. */
object Fmt {
    private val dateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

    fun date(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(dateFormatter)

    fun km(value: Int): String = NumberFormat.getIntegerInstance().format(value) + " km"

    private fun plural(n: Long, unit: String): String = if (n == 1L) "1 $unit" else "$n ${unit}s"

    private fun span(days: Long): String = when {
        days < 14 -> plural(days, "day")
        days < 60 -> plural(days / 7, "week")
        else -> plural(days / 30, "month")
    }

    /** For example: "in 1,200 km (at 61,200 km) · in 3 weeks (12 Mar 2027)". */
    fun describe(u: Upcoming): String {
        val parts = mutableListOf<String>()

        val remainingKm = u.remainingKm
        val dueKm = u.dueKm
        if (remainingKm != null && dueKm != null) {
            parts += when {
                remainingKm < 0 -> "overdue by ${km(-remainingKm)}"
                remainingKm == 0 -> "due now by kilometres"
                else -> "in ${km(remainingKm)} (at ${km(dueKm)})"
            }
        }

        val remainingDays = u.remainingDays
        val dueDay = u.dueDay
        if (remainingDays != null && dueDay != null) {
            parts += when {
                remainingDays < 0 -> "overdue by ${span(-remainingDays)}"
                remainingDays == 0L -> "due today"
                else -> "in ${span(remainingDays)} (${date(dueDay)})"
            }
        }

        return parts.joinToString(" · ")
    }
}
