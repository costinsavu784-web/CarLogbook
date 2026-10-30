package com.carlogbook.app

import android.app.Application
import com.carlogbook.app.notify.Notifications
import com.carlogbook.app.notify.ReminderScheduler

class CarLogbookApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifications.createChannel(this)
        ReminderScheduler.schedule(this)
    }
}
