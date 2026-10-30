package com.carlogbook.app.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.carlogbook.app.data.CarDatabase
import com.carlogbook.app.domain.Fmt
import com.carlogbook.app.domain.ReminderCalculator
import com.carlogbook.app.domain.ReminderStatus
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/** Runs once a day in the background and sends a notification for everything that is due soon or overdue. */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val db = CarDatabase.get(applicationContext)
        val today = LocalDate.now()

        for (car in db.carDao().getAll()) {
            val services = db.serviceDao().getForCar(car.id)

            val due = ReminderCalculator.compute(car, services, today)
                .filter { it.status != ReminderStatus.OK }
            for (item in due) {
                Notifications.show(
                    context = applicationContext,
                    id = "${car.id}:${item.jobName.lowercase()}".hashCode(),
                    title = "${item.jobName} · ${car.name}",
                    text = Fmt.describe(item).replaceFirstChar { it.uppercase() }
                )
            }

            // The app cannot read the car's mileage by itself, so ask for it now and then.
            val usesKmReminders = services.any { it.intervalKm != null }
            val daysSinceUpdate = today.toEpochDay() - car.lastOdometerUpdateDay
            if (usesKmReminders && daysSinceUpdate >= ODOMETER_NUDGE_DAYS) {
                Notifications.show(
                    context = applicationContext,
                    id = "odo:${car.id}".hashCode(),
                    title = "Update the kilometres · ${car.name}",
                    text = "Your odometer was last updated $daysSinceUpdate days ago. " +
                        "Enter the current kilometres so the reminders stay accurate."
                )
            }
        }
        return Result.success()
    }

    companion object {
        const val ODOMETER_NUDGE_DAYS = 30
    }
}

object ReminderScheduler {
    private const val WORK_NAME = "daily_service_reminders"

    /** Schedules the daily check, first run at the next 09:00. Safe to call on every app start. */
    fun schedule(context: Context) {
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(9, 0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val delayMinutes = Duration.between(now, next).toMinutes()

        val request = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    /** Runs the check right now (used by the "Check now" button to test notifications). */
    fun runNow(context: Context) {
        WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<ReminderWorker>().build())
    }
}
