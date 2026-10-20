package com.innexar.meuganho.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.innexar.meuganho.data.local.entity.ShiftEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ShiftDao {
    @Query("SELECT * FROM shifts WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    fun observeOpen(): Flow<ShiftEntity?>

    @Query("SELECT * FROM shifts WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun open(): ShiftEntity?

    /** Turnos que se sobrepõem ao intervalo (inclusive o aberto). */
    @Query("SELECT * FROM shifts WHERE startedAt < :end AND (endedAt IS NULL OR endedAt > :start) ORDER BY startedAt")
    fun observeOverlapping(start: Long, end: Long): Flow<List<ShiftEntity>>

    @Insert
    suspend fun insert(shift: ShiftEntity): Long

    @Update
    suspend fun update(shift: ShiftEntity)

    @Query("SELECT * FROM shifts WHERE id = :id")
    suspend fun byId(id: Long): ShiftEntity?

    // Incrementos atômicos: o GPS acumula em memória e só grava a cada ~300 m.
    @Query("UPDATE shifts SET emptyMeters = emptyMeters + :meters WHERE id = :id")
    suspend fun addEmptyMeters(id: Long, meters: Long)

    @Query("UPDATE shifts SET pickupMeters = pickupMeters + :meters WHERE id = :id")
    suspend fun addPickupMeters(id: Long, meters: Long)

    @Query("UPDATE shifts SET paidMeters = paidMeters + :meters WHERE id = :id")
    suspend fun addPaidMeters(id: Long, meters: Long)

    @Query("SELECT * FROM shifts")
    suspend fun all(): List<ShiftEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ShiftEntity>)
}
