package com.carlogbook.app.domain

import com.carlogbook.app.data.Car
import com.carlogbook.app.data.Service
import java.time.LocalDate

enum class ReminderStatus { OVERDUE, DUE_SOON, OK }

/** The next due point of one job on one car, worked out from its history. */
data class Upcoming(
    val car: Car,
    val jobName: String,
    val lastDoneKm: Int,
    val lastDoneDay: Long,
    val intervalKm: Int?,
    val intervalMonths: Int?,
    val dueKm: Int?,
    val dueDay: Long?,
    val remainingKm: Int?,
    val remainingDays: Long?,
    val status: ReminderStatus
) {
    /** Share of the km interval still unused, 0..1. Null when the job has no km interval. */
    val kmLifeLeft: Float? get() = fraction(remainingKm?.toDouble(), intervalKm?.toDouble())

    /** Share of the time interval still unused, 0..1. Null when the job has no time interval. */
    val timeLifeLeft: Float? get() {
        val due = dueDay ?: return null
        val total = (due - lastDoneDay).toDouble()
        return fraction(remainingDays?.toDouble(), total)
    }

    /** The smaller of the two, since the job is due at whichever comes first. */
    val lifeLeft: Float get() = listOfNotNull(kmLifeLeft, timeLifeLeft).minOrNull() ?: 1f

    private fun fraction(remaining: Double?, total: Double?): Float? {
        if (remaining == null || total == null || total <= 0) return null
        return (remaining / total).coerceIn(0.0, 1.0).toFloat()
    }
}

object ReminderCalculator {

    /**
     * For every job done on this car, takes the most recent entry and works out when the
     * next one is due. The interval used is the most recent one you entered for that job,
     * so you do not have to retype it every time.
     */
    fun compute(car: Car, services: List<Service>, today: LocalDate = LocalDate.now()): List<Upcoming> {
        val todayDay = today.toEpochDay()

        return services
            .groupBy { it.jobName.trim().lowercase() }
            .values
            .mapNotNull { group ->
                val newestFirst = group.sortedWith(
                    compareByDescending<Service> { it.dateEpochDay }
                        .thenByDescending { it.odometerKm }
                        .thenByDescending { it.id }
                )
                val latest = newestFirst.first()
                val intervalKm = newestFirst.firstOrNull { it.intervalKm != null }?.intervalKm
                val intervalMonths = newestFirst.firstOrNull { it.intervalMonths != null }?.intervalMonths
                if (intervalKm == null && intervalMonths == null) return@mapNotNull null

                val dueKm = intervalKm?.let { latest.odometerKm + it }
                val dueDay = intervalMonths?.let {
                    LocalDate.ofEpochDay(latest.dateEpochDay).plusMonths(it.toLong()).toEpochDay()
                }
                val remainingKm = dueKm?.let { it - car.currentOdometerKm }
                val remainingDays = dueDay?.let { it - todayDay }

                val overdue = (remainingKm != null && remainingKm <= 0) ||
                    (remainingDays != null && remainingDays <= 0)
                val soon = (remainingKm != null && remainingKm <= car.warnBeforeKm) ||
                    (remainingDays != null && remainingDays <= car.warnBeforeDays)
                val status = when {
                    overdue -> ReminderStatus.OVERDUE
                    soon -> ReminderStatus.DUE_SOON
                    else -> ReminderStatus.OK
                }

                Upcoming(
                    car = car,
                    jobName = latest.jobName.trim(),
                    lastDoneKm = latest.odometerKm,
                    lastDoneDay = latest.dateEpochDay,
                    intervalKm = intervalKm,
                    intervalMonths = intervalMonths,
                    dueKm = dueKm,
                    dueDay = dueDay,
                    remainingKm = remainingKm,
                    remainingDays = remainingDays,
                    status = status
                )
            }
            .sortedWith(compareBy<Upcoming>({ it.status.ordinal }, { urgency(it) }))
    }

    /** Smaller = more urgent. Roughly treats 1,000 km as equal to 30 days. */
    private fun urgency(u: Upcoming): Double {
        val kmScore = u.remainingKm?.let { it / 1000.0 } ?: Double.MAX_VALUE
        val dayScore = u.remainingDays?.let { it / 30.0 } ?: Double.MAX_VALUE
        return minOf(kmScore, dayScore)
    }
}
