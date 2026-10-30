package com.carlogbook.app.data

import com.carlogbook.app.R
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** One car with its services (and their parts) and the user-set mounted parts, for the backup file. */
data class CarBackup(
    val car: Car,
    val services: List<ServiceDraft>,
    val mounted: List<MountedPart> = emptyList()
)

/** The whole garage: cars with their services, plus where the user filed components. */
data class GarageBackup(
    val cars: List<CarBackup>,
    val placements: List<ComponentPlacement> = emptyList()
) {
    val serviceCount: Int get() = cars.sumOf { it.services.size }
    val partCount: Int get() = cars.sumOf { c -> c.services.sumOf { it.parts.size } }
}

/**
 * Converts the whole garage to and from a JSON text file.
 *
 * Services are nested under their car, so database IDs are not stored and importing into
 * another phone (or after a reinstall) never collides with existing rows.
 *
 * Format history: 1 = cars and services. 2 = adds "placements". 3 = adds "parts" per service.
 * 4 = adds "mounted" per car.
 */
object BackupCodec {

    const val FORMAT_VERSION = 4
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
            for (draft in entry.services) {
                val s = draft.service
                val parts = JSONArray()
                for (p in draft.parts) {
                    parts.put(
                        JSONObject()
                            .put("name", p.name)
                            .put("quantity", p.quantity)
                            .put("oem", p.oemNumber)
                            .put("supplier", p.supplierNumber)
                    )
                }
                services.put(
                    JSONObject()
                        .put("jobName", s.jobName)
                        .put("date", LocalDate.ofEpochDay(s.dateEpochDay).toString())
                        .put("odometerKm", s.odometerKm)
                        .put("notes", s.notes)
                        .putOpt("intervalKm", s.intervalKm)
                        .putOpt("intervalMonths", s.intervalMonths)
                        .put("parts", parts)
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
                    .put("mounted", JSONArray().also { arr ->
                        for (m in entry.mounted) {
                            arr.put(
                                JSONObject()
                                    .put("component", m.componentKey)
                                    .put("name", m.name)
                                    .put("quantity", m.quantity)
                                    .put("oem", m.oemNumber)
                                    .put("supplier", m.supplierNumber)
                            )
                        }
                    })
            )
        }
        root.put("cars", cars)

        val placements = JSONArray()
        for (p in backup.placements) {
            placements.put(
                JSONObject()
                    .put("name", p.name)
                    .put("category", p.categoryId)
                    .putOpt("wears", p.wears)
            )
        }
        root.put("placements", placements)

        return root.toString(2)
    }

    /** Thrown when a file cannot be used as a backup; [messageRes] is the string to show the user. */
    class InvalidBackup(val messageRes: Int) : IllegalArgumentException()

    /** Throws [InvalidBackup] if the file is not a valid backup. */
    fun decode(json: String): GarageBackup {
        val root = try {
            JSONObject(json)
        } catch (e: Exception) {
            throw InvalidBackup(R.string.backup_not_valid)
        }
        if (root.optString("app") != APP_TAG) throw InvalidBackup(R.string.backup_not_valid)
        val format = root.optInt("format", -1)
        if (format !in 1..FORMAT_VERSION) throw InvalidBackup(R.string.backup_too_new)

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
                val service = Service(
                    carId = 0, // assigned on import
                    jobName = s.getString("jobName"),
                    dateEpochDay = s.optDay("date") ?: throw IllegalArgumentException("A service has no date."),
                    odometerKm = s.optInt("odometerKm"),
                    notes = s.optString("notes"),
                    intervalKm = s.optIntOrNull("intervalKm"),
                    intervalMonths = s.optIntOrNull("intervalMonths")
                )
                val parts = s.optJSONArray("parts") ?: JSONArray()
                val partList = List(parts.length()) { k ->
                    val p = parts.getJSONObject(k)
                    ServicePart(
                        serviceId = 0, // assigned on import
                        name = p.optString("name"),
                        quantity = p.optString("quantity"),
                        oemNumber = p.optString("oem"),
                        supplierNumber = p.optString("supplier")
                    )
                }.filter { it.name.isNotBlank() }
                ServiceDraft(service, partList)
            }
            val mounted = c.optJSONArray("mounted") ?: JSONArray()
            val mountedList = List(mounted.length()) { k ->
                val m = mounted.getJSONObject(k)
                MountedPart(
                    carId = 0, // assigned on import
                    componentKey = m.optString("component").lowercase(),
                    name = m.optString("name"),
                    quantity = m.optString("quantity"),
                    oemNumber = m.optString("oem"),
                    supplierNumber = m.optString("supplier")
                )
            }.filter { it.name.isNotBlank() && it.componentKey.isNotBlank() }
            CarBackup(car, list, mountedList)
        }

        val placements = root.optJSONArray("placements") ?: JSONArray()
        val placementList = List(placements.length()) { i ->
            val p = placements.getJSONObject(i)
            val name = p.getString("name").trim()
            ComponentPlacement(
                key = name.lowercase(),
                name = name,
                categoryId = p.optString("category"),
                wears = if (p.has("wears") && !p.isNull("wears")) p.getBoolean("wears") else null
            )
        }.filter { it.name.isNotEmpty() }

        return GarageBackup(carList, placementList)
    }

    private fun JSONObject.optIntOrNull(key: String): Int? =
        if (has(key) && !isNull(key)) getInt(key) else null

    private fun JSONObject.optDay(key: String): Long? =
        if (has(key) && !isNull(key)) LocalDate.parse(getString(key)).toEpochDay() else null
}
