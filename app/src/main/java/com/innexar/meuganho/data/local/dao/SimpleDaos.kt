package com.innexar.meuganho.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.innexar.meuganho.data.local.entity.ExpenseEntity
import com.innexar.meuganho.data.local.entity.FuelLogEntity
import com.innexar.meuganho.data.local.entity.GoalEntity
import com.innexar.meuganho.data.local.entity.PlatformTotalEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FuelLogDao {
    @Query("SELECT * FROM fuel_logs ORDER BY odometerKm DESC")
    fun observeAll(): Flow<List<FuelLogEntity>>

    @Query("SELECT * FROM fuel_logs WHERE at >= :start AND at < :end")
    fun observeBetween(start: Long, end: Long): Flow<List<FuelLogEntity>>

    @Insert
    suspend fun insert(log: FuelLogEntity): Long

    @Query("DELETE FROM fuel_logs WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM fuel_logs")
    suspend fun all(): List<FuelLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<FuelLogEntity>)
}

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses WHERE at >= :start AND at < :end ORDER BY at DESC")
    fun observeBetween(start: Long, end: Long): Flow<List<ExpenseEntity>>

    @Insert
    suspend fun insert(expense: ExpenseEntity): Long

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM expenses")
    suspend fun all(): List<ExpenseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ExpenseEntity>)
}

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals WHERE isActive = 1")
    fun observeActive(): Flow<List<GoalEntity>>

    @Upsert
    suspend fun upsert(goal: GoalEntity): Long

    @Query("DELETE FROM goals WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM goals")
    suspend fun all(): List<GoalEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<GoalEntity>)
}

@Dao
interface PlatformTotalDao {
    @Insert
    suspend fun insert(total: PlatformTotalEntity): Long

    @Query("SELECT * FROM platform_totals WHERE epochDay = :epochDay ORDER BY capturedAt DESC")
    fun observeDay(epochDay: Long): Flow<List<PlatformTotalEntity>>

    @Query("SELECT * FROM platform_totals WHERE platform = :platform AND epochDay = :epochDay ORDER BY capturedAt DESC LIMIT 1")
    suspend fun latest(platform: String, epochDay: Long): PlatformTotalEntity?

    @Query("SELECT * FROM platform_totals")
    suspend fun all(): List<PlatformTotalEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<PlatformTotalEntity>)
}
