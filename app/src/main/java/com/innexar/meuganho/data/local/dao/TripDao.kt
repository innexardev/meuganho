package com.innexar.meuganho.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.innexar.meuganho.data.local.entity.TripEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {
    @Insert
    suspend fun insert(trip: TripEntity): Long

    @Update
    suspend fun update(trip: TripEntity)

    @Query("SELECT * FROM trips WHERE id = :id")
    suspend fun byId(id: Long): TripEntity?

    @Query("SELECT * FROM trips WHERE id = :id")
    fun observe(id: Long): Flow<TripEntity?>

    @Query("SELECT * FROM trips WHERE platform = :platform AND acceptedAt BETWEEN :start AND :end")
    suspend fun platformTripsBetween(platform: String, start: Long, end: Long): List<TripEntity>

    /** Última corrida concluída da plataforma desde [since] (para receber o valor oficial). */
    @Query("SELECT * FROM trips WHERE platform = :platform AND state = 'COMPLETED' AND endedAt >= :since ORDER BY endedAt DESC LIMIT 1")
    suspend fun lastCompletedSince(platform: String, since: Long): TripEntity?

    @Query("SELECT * FROM trips WHERE offerId = :offerId LIMIT 1")
    suspend fun byOfferId(offerId: Long): TripEntity?

    @Query("SELECT * FROM trips WHERE acceptedAt >= :start AND acceptedAt < :end ORDER BY acceptedAt DESC")
    fun observeBetween(start: Long, end: Long): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE status = 'NEEDS_REVIEW' ORDER BY acceptedAt DESC")
    fun observeNeedingReview(): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE state IN ('TO_PICKUP','WAITING_PASSENGER','IN_TRIP') ORDER BY acceptedAt DESC LIMIT 1")
    suspend fun active(): TripEntity?

    @Query("SELECT * FROM trips WHERE state IN ('TO_PICKUP','WAITING_PASSENGER','IN_TRIP') ORDER BY acceptedAt DESC")
    suspend fun activeAll(): List<TripEntity>

    /** Última corrida encerrada (concluída OU cancelada) da plataforma desde [since]. */
    @Query("SELECT * FROM trips WHERE platform = :platform AND state IN ('COMPLETED','CANCELLED') AND endedAt >= :since ORDER BY endedAt DESC LIMIT 1")
    suspend fun lastEndedSince(platform: String, since: Long): TripEntity?

    /** Já existe corrida com esse valor vinda do histórico oficial? (evita contar duas vezes) */
    @Query("SELECT COUNT(*) FROM trips WHERE platform = :platform AND valueSource = 'PLATFORM_HISTORY' AND finalCents = :cents AND acceptedAt >= :since")
    suspend fun historyValueSince(platform: String, cents: Long, since: Long): Int

    @Query("UPDATE trips SET gpsPickupMeters = gpsPickupMeters + :meters WHERE id = :id")
    suspend fun addPickupMeters(id: Long, meters: Long)

    @Query("UPDATE trips SET gpsPaidMeters = gpsPaidMeters + :meters WHERE id = :id")
    suspend fun addPaidMeters(id: Long, meters: Long)

    /** Corridas marcadas como canceladas desde [since] (recuperação da 0.3.1). */
    @Query("SELECT * FROM trips WHERE state = 'CANCELLED' AND acceptedAt >= :since")
    suspend fun cancelledSince(since: Long): List<TripEntity>

    @Query("DELETE FROM trips WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM trips")
    suspend fun all(): List<TripEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<TripEntity>)

    @Query("SELECT MAX(COALESCE(endedAt, acceptedAt)) FROM trips WHERE acceptedAt BETWEEN :from AND :to")
    suspend fun lastActivityBetween(from: Long, to: Long): Long?

    @Query("SELECT * FROM trips WHERE offerId IS NULL AND acceptedAt >= :since ORDER BY acceptedAt")
    suspend fun withoutOfferSince(since: Long): List<TripEntity>

    @Query("SELECT * FROM trips WHERE platform = :platform AND endedAt >= :since ORDER BY endedAt")
    suspend fun endedSince(platform: String, since: Long): List<TripEntity>

    @Query("SELECT * FROM trips WHERE offerId IS NOT NULL AND state = 'COMPLETED' AND valueSource IN ('PLATFORM_HISTORY','PLATFORM_TOTAL') AND acceptedAt >= :since")
    suspend fun officialWithOfferSince(since: Long): List<TripEntity>
}
