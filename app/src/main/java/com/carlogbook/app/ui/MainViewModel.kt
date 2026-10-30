package com.carlogbook.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.carlogbook.app.data.Car
import com.carlogbook.app.data.CarDatabase
import com.carlogbook.app.data.Service
import com.carlogbook.app.domain.ReminderCalculator
import com.carlogbook.app.domain.Upcoming
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val carDao = CarDatabase.get(app).carDao()
    private val serviceDao = CarDatabase.get(app).serviceDao()

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

    /** Saves a service. If its kilometres are higher than the car's odometer, the odometer moves up too. */
    fun saveService(service: Service) {
        viewModelScope.launch {
            if (service.id == 0L) serviceDao.insert(service) else serviceDao.update(service)
            val car = carDao.get(service.carId)
            if (car != null && service.odometerKm > car.currentOdometerKm) {
                carDao.update(
                    car.copy(
                        currentOdometerKm = service.odometerKm,
                        lastOdometerUpdateDay = LocalDate.now().toEpochDay()
                    )
                )
            }
        }
    }

    fun deleteService(id: Long) {
        viewModelScope.launch { serviceDao.deleteById(id) }
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
