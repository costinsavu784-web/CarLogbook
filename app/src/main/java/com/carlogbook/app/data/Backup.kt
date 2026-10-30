package com.carlogbook.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** One car together with its services, as read from or written to a backup file. */
data class CarBackup(val car: Car, val services: List<Service>)

/** The whole garage: cars with their services, plus where the user filed components. */
data class GarageBackup(
    val cars: List<CarBackup>,
    val placements: List<ComponentPlacement> = emptyList()
) {
    val serviceCount: Int get() = cars.sumOf { it.services.size }
}

/**
 * Converts the whole garage to and from a JSON text file.
 *
 * Services are nested under their car, so database IDs are not stored and importing into
 * another phone (or after a reinstall) never collides with existing rows.
 *
 * Format history: 1 = cars and services. 2 = adds "placements".
 */
object BackupCodec {

    const val FORMAT_VERSION = 2
    private const val APP_TAG = "CarLogbook"

    fun suggestedFileName(today: LocalDate = LocalDate.now()): String =
        "CarLogbook-backup-$today.json"

    fun encode(backup: GarageBackup, exportedAt: LocalDate = LocalDate.now()): String {
        val root = JSONObject()
            .put("app", APP_TAG)
            .put("format", FORMAT_VERSION)
            .put("exportedAt", exportedAt.toString())

        val cars = JSONArray()
        for (entry in backup.cars) {
            val c = entry.car
            val services = JSONArray()
            for (s in entry.services) {
                services.put(
                    JSONObject()
                        .put("jobName", s.jobName)
                        .put("date", LocalDate.ofEpochDay(s.dateEpochDay).toString())
                        .put("odometerKm", s.odometerKm)
                        .put("notes", s.notes)
                        .putOpt("intervalKm", s.intervalKm)
                        .putOpt("intervalMonths", s.intervalMonths)
                )
            }
            cars.put(
                JSONObject()
                    .put("name", c.name)
                    .put("make", c.make)
                    .put("model", c.model)
                    .putOpt("year", c.year)
                    .put("currentOdometerKm", c.currentOdometerKm)
                    .put("lastOdometerUpdate", LocalDate.ofEpochDay(c.lastOdometerUpdateDay).toString())
                    .put("warnBeforeKm", c.warnBeforeKm)
                    .put("warnBeforeDays", c.warnBeforeDays)
                    .put("services", services)
            )
        }
        root.put("cars", cars)

        val placements = JSONArray()
        for (p in backup.placements) {
            placements.put(JSONObject().put("name", p.name).put("category", p.categoryId))
        }
        root.put("placements", placements)

        return root.toString(2)
    }

    /** Throws IllegalArgumentException with a readable message if the file is not a valid backup. */
    fun decode(json: String): GarageBackup {
        val root = try {
            JSONObject(json)
        } catch (e: Exception) {
            throw IllegalArgumentException("This file is not a Car Logbook backup.")
        }
        require(root.optString("app") == APP_TAG) { "This file is not a Car Logbook backup." }
        val format = root.optInt("format", -1)
        require(format in 1..FORMAT_VERSION) {
            "This backup was made by a newer version of the app. Update the app first."
        }

        val cars = root.optJSONArray("cars") ?: JSONArray()
        val carList = List(cars.length()) { i ->
            val c = cars.getJSONObject(i)
            val car = Car(
                name = c.getString("name"),
                make = c.optString("make"),
                model = c.optString("model"),
                year = c.optIntOrNull("year"),
                currentOdometerKm = c.optInt("currentOdometerKm"),
                lastOdometerUpdateDay = c.optDay("lastOdometerUpdate") ?: LocalDate.now().toEpochDay(),
                warnBeforeKm = c.optInt("warnBeforeKm", 1000),
                warnBeforeDays = c.optInt("warnBeforeDays", 14)
            )
            val services = c.optJSONArray("services") ?: JSONArray()
            val list = List(services.length()) { j ->
                val s = services.getJSONObject(j)
                Service(
                    carId = 0, // assigned on import
                    jobName = s.getString("jobName"),
                    dateEpochDay = s.optDay("date") ?: throw IllegalArgumentException("A service has no date."),
                    odometerKm = s.optInt("odometerKm"),
                    notes = s.optString("notes"),
                    intervalKm = s.optIntOrNull("intervalKm"),
                    intervalMonths = s.optIntOrNull("intervalMonths")
                )
            }
            CarBackup(car, list)
        }

        val placements = root.optJSONArray("placements") ?: JSONArray()
        val placementList = List(placements.length()) { i ->
            val p = placements.getJSONObject(i)
            val name = p.getString("name").trim()
            ComponentPlacement(key = name.lowercase(), name = name, categoryId = p.getString("category"))
        }.filter { it.name.isNotEmpty() }

        return GarageBackup(carList, placementList)
    }

    private fun JSONObject.optIntOrNull(key: String): Int? =
        if (has(key) && !isNull(key)) getInt(key) else null

    private fun JSONObject.optDay(key: String): Long? =
        if (has(key) && !isNull(key)) LocalDate.parse(getString(key)).toEpochDay() else null
}
