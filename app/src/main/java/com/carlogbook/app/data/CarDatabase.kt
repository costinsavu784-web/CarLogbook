package com.carlogbook.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Car::class, Service::class, ServicePart::class, MountedPart::class, ComponentPlacement::class],
    version = 5,
    exportSchema = false
)
abstract class CarDatabase : RoomDatabase() {
    abstract fun carDao(): CarDao
    abstract fun serviceDao(): ServiceDao
    abstract fun partDao(): PartDao
    abstract fun mountedDao(): MountedDao
    abstract fun placementDao(): PlacementDao

    /** Everything in the database, ready to be written to a backup file. */
    suspend fun exportGarage(): GarageBackup = GarageBackup(
        cars = carDao().getAll().map { car ->
            val partsByService = partDao().getForCar(car.id).groupBy { it.serviceId }
            CarBackup(
                car = car,
                services = serviceDao().getForCar(car.id).map { s -> ServiceDraft(s, partsByService[s.id] ?: emptyList()) },
                mounted = mountedDao().getForCar(car.id)
            )
        },
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
            for (draft in entry.services) {
                val serviceId = serviceDao().insert(draft.service.copy(id = 0, carId = carId))
                partDao().insertAll(draft.parts.map { it.copy(id = 0, serviceId = serviceId) })
            }
            mountedDao().insertAll(entry.mounted.map { it.copy(id = 0, carId = carId) })
        }
        placementDao().deleteAll()
        placementDao().upsertAll(backup.placements)
    }

    /** Replaces what the user says is mounted on a component. An empty list removes the override. */
    suspend fun setMounted(carId: Long, componentKey: String, parts: List<MountedPart>) = withTransaction {
        mountedDao().deleteFor(carId, componentKey)
        mountedDao().insertAll(
            parts.filter { it.name.isNotBlank() }.map { it.copy(id = 0, carId = carId, componentKey = componentKey) }
        )
    }

    /** Inserts or updates a service and replaces its parts list. Returns the service id. */
    suspend fun saveDraft(draft: ServiceDraft): Long = withTransaction {
        val id = if (draft.service.id == 0L) {
            serviceDao().insert(draft.service)
        } else {
            serviceDao().update(draft.service)
            partDao().deleteForService(draft.service.id)
            draft.service.id
        }
        partDao().insertAll(
            draft.parts
                .filter { it.name.isNotBlank() }
                .map { it.copy(id = 0, serviceId = id) }
        )
        id
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
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).build().also { instance = it }
            }

        /** Version 5 adds the user-set "currently mounted" parts per car and component. */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `mounted_parts` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`carId` INTEGER NOT NULL, " +
                        "`componentKey` TEXT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`quantity` TEXT NOT NULL, " +
                        "`oemNumber` TEXT NOT NULL, " +
                        "`supplierNumber` TEXT NOT NULL, " +
                        "FOREIGN KEY(`carId`) REFERENCES `cars`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_mounted_parts_carId` ON `mounted_parts` (`carId`)")
            }
        }

        /** Version 4 adds parts and materials used per service. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `service_parts` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`serviceId` INTEGER NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`quantity` TEXT NOT NULL, " +
                        "`oemNumber` TEXT NOT NULL, " +
                        "`supplierNumber` TEXT NOT NULL, " +
                        "FOREIGN KEY(`serviceId`) REFERENCES `services`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_service_parts_serviceId` ON `service_parts` (`serviceId`)")
            }
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
