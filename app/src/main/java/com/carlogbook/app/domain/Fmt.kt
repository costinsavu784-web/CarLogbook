package com.carlogbook.app.domain

import android.content.Context
import android.content.res.Resources
import com.carlogbook.app.R
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Small helpers to show dates, kilometres and reminders as readable text in the current language.
 * Call [attach] with a locale-aware context before use (AppLocale.wrap does this).
 */
object Fmt {
    private var res: Resources? = null

    fun attach(context: Context) {
        res = context.resources
    }

    private fun str(id: Int, vararg args: Any): String = res?.getString(id, *args) ?: ""
    private fun qty(id: Int, n: Int): String = res?.getQuantityString(id, n, n) ?: n.toString()

    fun date(epochDay: Long): String =
        LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))

    fun km(value: Int): String = NumberFormat.getIntegerInstance().format(value) + " km"

    /** A number of days as "5 days", "3 weeks" or "4 months". */
    fun span(days: Long): String = when {
        days < 14 -> qty(R.plurals.days, days.toInt())
        days < 60 -> qty(R.plurals.weeks, (days / 7).toInt())
        else -> qty(R.plurals.months, (days / 30).toInt())
    }

    fun months(n: Int): String = qty(R.plurals.months, n)

    /** For example: "in 1,200 km (at 61,200 km) · in 3 weeks (12 Mar 2027)". */
    fun describe(u: Upcoming): String {
        val parts = mutableListOf<String>()

        val remainingKm = u.remainingKm
        val dueKm = u.dueKm
        if (remainingKm != null && dueKm != null) {
            parts += when {
                remainingKm < 0 -> str(R.string.fmt_overdue_by, km(-remainingKm))
                remainingKm == 0 -> str(R.string.fmt_due_now_km)
                else -> str(R.string.fmt_in_km_at, km(remainingKm), km(dueKm))
            }
        }

        val remainingDays = u.remainingDays
        val dueDay = u.dueDay
        if (remainingDays != null && dueDay != null) {
            parts += when {
                remainingDays < 0 -> str(R.string.fmt_overdue_by, span(-remainingDays))
                remainingDays == 0L -> str(R.string.fmt_due_today)
                else -> str(R.string.fmt_in_time_on, span(remainingDays), date(dueDay))
            }
        }

        return parts.joinToString(" · ")
    }
}
