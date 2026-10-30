package com.carlogbook.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Car::class, Service::class], version = 1, exportSchema = false)
abstract class CarDatabase : RoomDatabase() {
    abstract fun carDao(): CarDao
    abstract fun serviceDao(): ServiceDao

    companion object {
        @Volatile
        private var instance: CarDatabase? = null

        fun get(context: Context): CarDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    CarDatabase::class.java,
                    "carlogbook.db"
                ).build().also { instance = it }
            }
    }
}

/** Remembers which car you looked at last, so the app opens straight on it. */
object LastCar {
    private const val PREFS = "carlogbook"
    private const val KEY = "last_car_id"

    fun get(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY, 0L)

    fun set(context: Context, carId: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY, carId)
            .apply()
    }
}
