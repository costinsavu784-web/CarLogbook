package com.carlogbook.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.carlogbook.app.data.BackupCodec
import com.carlogbook.app.data.Car
import com.carlogbook.app.data.CarDatabase
import com.carlogbook.app.data.ComponentPlacement
import com.carlogbook.app.data.GarageBackup
import com.carlogbook.app.data.Service
import com.carlogbook.app.domain.ReminderCalculator
import com.carlogbook.app.domain.Upcoming
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** How many cars and services a backup contains. */
data class BackupSummary(val cars: Int, val services: Int)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val db = CarDatabase.get(app)
    private val carDao = db.carDao()
    private val serviceDao = db.serviceDao()
    private val placementDao = db.placementDao()

    val cars: StateFlow<List<Car>> = carDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Every job name you have ever used, for the quick-pick suggestions. */
    val jobNames: StateFlow<List<String>> = serviceDao.observeJobNames()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun car(id: Long): Flow<Car?> = carDao.observe(id)

    fun services(carId: Long): Flow<List<Service>> = serviceDao.observeForCar(carId)

    fun upcoming(carId: Long): Flow<List<Upcoming>> =
        combine(carDao.observe(carId), serviceDao.observeForCar(carId)) { car, services ->
            if (car == null) emptyList() else ReminderCalculator.compute(car, services)
        }

    suspend fun getCar(id: Long): Car? = carDao.get(id)

    suspend fun getService(id: Long): Service? = serviceDao.get(id)

    fun saveCar(car: Car) {
        viewModelScope.launch {
            if (car.id == 0L) carDao.insert(car) else carDao.update(car)
        }
    }

    fun deleteCar(id: Long) {
        viewModelScope.launch { carDao.deleteById(id) }
    }

    /**
     * Saves one or more services logged together (same visit, same date and kilometres).
     * If their kilometres are higher than the car's odometer, the odometer moves up too.
     */
    fun saveServices(services: List<Service>) {
        if (services.isEmpty()) return
        viewModelScope.launch {
            for (service in services) {
                if (service.id == 0L) serviceDao.insert(service) else serviceDao.update(service)
            }
            val carId = services.first().carId
            val maxKm = services.maxOf { it.odometerKm }
            val car = carDao.get(carId)
            if (car != null && maxKm > car.currentOdometerKm) {
                carDao.update(
                    car.copy(
                        currentOdometerKm = maxKm,
                        lastOdometerUpdateDay = LocalDate.now().toEpochDay()
                    )
                )
            }
        }
    }

    /** The reminder interval you last used for this job on this car, to prefill the form. */
    suspend fun lastInterval(carId: Long, jobName: String): Service? =
        serviceDao.getForCar(carId)
            .filter { it.jobName.equals(jobName.trim(), ignoreCase = true) }
            .filter { it.intervalKm != null || it.intervalMonths != null }
            .maxWithOrNull(compareBy<Service>({ it.dateEpochDay }, { it.odometerKm }, { it.id }))

    fun deleteService(id: Long) {
        viewModelScope.launch { serviceDao.deleteById(id) }
    }

    /** Writes the whole garage as JSON to the given file (chosen by the user through the system picker). */
    suspend fun exportBackup(uri: Uri): BackupSummary = withContext(Dispatchers.IO) {
        val backup = db.exportGarage()
        val json = BackupCodec.encode(backup)
        val resolver = getApplication<Application>().contentResolver
        // "wt" truncates: without it an older, longer file would keep its tail.
        val out = resolver.openOutputStream(uri, "wt") ?: error("Could not open the file for writing.")
        out.bufferedWriter().use { it.write(json) }
        BackupSummary(backup.cars.size, backup.serviceCount)
    }

    /** Reads a backup file without changing anything, so the user can confirm before importing. */
    suspend fun readBackup(uri: Uri): GarageBackup = withContext(Dispatchers.IO) {
        val resolver = getApplication<Application>().contentResolver
        val input = resolver.openInputStream(uri) ?: error("Could not open the file.")
        val json = input.bufferedReader().use { it.readText() }
        BackupCodec.decode(json)
    }

    /** Replaces the current garage with the backup contents. */
    suspend fun importBackup(backup: GarageBackup): BackupSummary = withContext(Dispatchers.IO) {
        db.importGarage(backup)
        BackupSummary(backup.cars.size, backup.serviceCount)
    }

    // --- Component placement -----------------------------------------------------------------

    /** Where the user has filed components, applied on top of the built-in catalogue. */
    val placements: StateFlow<List<ComponentPlacement>> = placementDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Files a component (existing or brand new) under the given category. */
    fun placeComponent(name: String, categoryId: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            placementDao.upsert(ComponentPlacement(key = clean.lowercase(), name = clean, categoryId = categoryId))
        }
    }

    /** Removes the user's placement so the component goes back to its built-in category (or Other). */
    fun resetPlacement(name: String) {
        viewModelScope.launch { placementDao.delete(name.trim().lowercase()) }
    }

    fun updateOdometer(carId: Long, km: Int) {
        viewModelScope.launch {
            val car = carDao.get(carId) ?: return@launch
            carDao.update(
                car.copy(
                    currentOdometerKm = km,
                    lastOdometerUpdateDay = LocalDate.now().toEpochDay()
                )
            )
        }
    }
}
