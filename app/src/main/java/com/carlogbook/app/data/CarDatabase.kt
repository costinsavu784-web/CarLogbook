package com.carlogbook.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Car::class, Service::class, ComponentPlacement::class], version = 3, exportSchema = false)
abstract class CarDatabase : RoomDatabase() {
    abstract fun carDao(): CarDao
    abstract fun serviceDao(): ServiceDao
    abstract fun placementDao(): PlacementDao

    /** Everything in the database, ready to be written to a backup file. */
    suspend fun exportGarage(): GarageBackup = GarageBackup(
        cars = carDao().getAll().map { car -> CarBackup(car, serviceDao().getForCar(car.id)) },
        placements = placementDao().getAll()
    )

    /**
     * Replaces the whole garage with the contents of a backup. Runs in one transaction:
     * if anything fails, the existing data is left untouched.
     */
    suspend fun importGarage(backup: GarageBackup) = withTransaction {
        carDao().deleteAll()
        for (entry in backup.cars) {
            val carId = carDao().insert(entry.car.copy(id = 0))
            serviceDao().insertAll(entry.services.map { it.copy(id = 0, carId = carId) })
        }
        placementDao().deleteAll()
        placementDao().upsertAll(backup.placements)
    }

    companion object {
        @Volatile
        private var instance: CarDatabase? = null

        fun get(context: Context): CarDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    CarDatabase::class.java,
                    "carlogbook.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
            }

        /** Version 3 lets a placement override whether a component wears out. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `component_placements` ADD COLUMN `wears` INTEGER")
            }
        }

        /** Version 2 adds the table where the user files components into categories. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `component_placements` (" +
                        "`key` TEXT NOT NULL, `name` TEXT NOT NULL, `categoryId` TEXT NOT NULL, " +
                        "PRIMARY KEY(`key`))"
                )
            }
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
