package com.carlogbook.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/** One vehicle. Each car has its own odometer and its own warning thresholds. */
@Entity(tableName = "cars")
data class Car(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val make: String = "",
    val model: String = "",
    val year: Int? = null,
    val currentOdometerKm: Int = 0,
    val lastOdometerUpdateDay: Long = LocalDate.now().toEpochDay(),
    val warnBeforeKm: Int = 1000,
    val warnBeforeDays: Int = 14
)

/**
 * One job done on a car (for example "Brake pads").
 * intervalKm / intervalMonths are optional: "repeat this job every X km / Y months".
 * Dates are stored as an epoch day (days since 1970-01-01) to avoid time-zone problems.
 */
@Entity(
    tableName = "services",
    foreignKeys = [
        ForeignKey(
            entity = Car::class,
            parentColumns = ["id"],
            childColumns = ["carId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("carId")]
)
data class Service(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val carId: Long,
    val jobName: String,
    val dateEpochDay: Long,
    val odometerKm: Int,
    val notes: String = "",
    val intervalKm: Int? = null,
    val intervalMonths: Int? = null
)

/**
 * Where the user wants a component to appear in the overview. Overrides the built-in catalogue
 * and also lets the user add components the catalogue does not know. Shared by all cars.
 * `key` is the name in lower case; `name` keeps the user's spelling.
 */
@Entity(tableName = "component_placements")
data class ComponentPlacement(
    @PrimaryKey val key: String,
    val name: String,
    /** Empty string means "keep the built-in category" (only the wear flag is overridden). */
    val categoryId: String,
    /** null = use the catalogue default; true = wears out over km/time; false = works until it fails. */
    val wears: Boolean? = null
)
