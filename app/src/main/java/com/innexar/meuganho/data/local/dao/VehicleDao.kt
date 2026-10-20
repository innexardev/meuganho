package com.innexar.meuganho.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.innexar.meuganho.data.local.entity.VehicleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VehicleDao {
    @Query("SELECT * FROM vehicles WHERE isActive = 1 ORDER BY id DESC LIMIT 1")
    fun observeActive(): Flow<VehicleEntity?>

    @Query("SELECT * FROM vehicles WHERE isActive = 1 ORDER BY id DESC LIMIT 1")
    suspend fun active(): VehicleEntity?

    @Upsert
    suspend fun upsert(vehicle: VehicleEntity): Long

    @Query("UPDATE vehicles SET odometerKm = :km WHERE id = :id AND odometerKm < :km")
    suspend fun raiseOdometer(id: Long, km: Int)

    @Query("SELECT * FROM vehicles")
    suspend fun all(): List<VehicleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<VehicleEntity>)
}
