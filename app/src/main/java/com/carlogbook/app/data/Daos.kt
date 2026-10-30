package com.carlogbook.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CarDao {
    @Query("SELECT * FROM cars ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Car>>

    @Query("SELECT * FROM cars ORDER BY name COLLATE NOCASE")
    suspend fun getAll(): List<Car>

    @Query("SELECT * FROM cars WHERE id = :id")
    fun observe(id: Long): Flow<Car?>

    @Query("SELECT * FROM cars WHERE id = :id")
    suspend fun get(id: Long): Car?

    @Insert
    suspend fun insert(car: Car): Long

    @Update
    suspend fun update(car: Car)

    @Query("DELETE FROM cars WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Removes every car; services go with them through the foreign key cascade. */
    @Query("DELETE FROM cars")
    suspend fun deleteAll()
}

@Dao
interface ServiceDao {
    @Query("SELECT * FROM services WHERE carId = :carId ORDER BY dateEpochDay DESC, odometerKm DESC, id DESC")
    fun observeForCar(carId: Long): Flow<List<Service>>

    @Query("SELECT * FROM services WHERE carId = :carId")
    suspend fun getForCar(carId: Long): List<Service>

    @Query("SELECT * FROM services WHERE id = :id")
    suspend fun get(id: Long): Service?

    @Query("SELECT DISTINCT jobName FROM services ORDER BY jobName COLLATE NOCASE")
    fun observeJobNames(): Flow<List<String>>

    @Insert
    suspend fun insert(service: Service): Long

    @Insert
    suspend fun insertAll(services: List<Service>)

    @Update
    suspend fun update(service: Service)

    @Query("DELETE FROM services WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface PartDao {
    @Query("SELECT p.* FROM service_parts p JOIN services s ON s.id = p.serviceId WHERE s.carId = :carId ORDER BY p.id")
    fun observeForCar(carId: Long): Flow<List<ServicePart>>

    @Query("SELECT * FROM service_parts WHERE serviceId = :serviceId ORDER BY id")
    suspend fun getForService(serviceId: Long): List<ServicePart>

    @Query("SELECT p.* FROM service_parts p JOIN services s ON s.id = p.serviceId WHERE s.carId = :carId ORDER BY p.id")
    suspend fun getForCar(carId: Long): List<ServicePart>

    @Insert
    suspend fun insertAll(parts: List<ServicePart>)

    @Query("DELETE FROM service_parts WHERE serviceId = :serviceId")
    suspend fun deleteForService(serviceId: Long)
}

@Dao
interface PlacementDao {
    @Query("SELECT * FROM component_placements")
    fun observeAll(): Flow<List<ComponentPlacement>>

    @Query("SELECT * FROM component_placements")
    suspend fun getAll(): List<ComponentPlacement>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(placement: ComponentPlacement)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(placements: List<ComponentPlacement>)

    @Query("SELECT * FROM component_placements WHERE `key` = :key")
    suspend fun get(key: String): ComponentPlacement?

    @Query("DELETE FROM component_placements WHERE `key` = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM component_placements")
    suspend fun deleteAll()
}
