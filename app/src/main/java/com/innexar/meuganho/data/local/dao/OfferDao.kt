package com.innexar.meuganho.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.innexar.meuganho.data.local.entity.OfferEntity
import com.innexar.meuganho.domain.model.DataStatus
import com.innexar.meuganho.domain.model.OfferOutcome
import kotlinx.coroutines.flow.Flow

@Dao
interface OfferDao {
    @Insert
    suspend fun insert(offer: OfferEntity): Long

    @Update
    suspend fun update(offer: OfferEntity)

    @Query("SELECT * FROM offers WHERE id = :id")
    suspend fun byId(id: Long): OfferEntity?

    @Query("SELECT * FROM offers ORDER BY detectedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<OfferEntity>>

    @Query("SELECT * FROM offers WHERE status = 'NEEDS_REVIEW' ORDER BY detectedAt DESC")
    fun observeNeedingReview(): Flow<List<OfferEntity>>

    @Query("UPDATE offers SET outcome = :outcome WHERE id = :id")
    suspend fun setOutcome(id: Long, outcome: OfferOutcome)

    @Query("UPDATE offers SET status = :status, correctedCents = :correctedCents, reviewedAt = :at, rawSnapshot = NULL WHERE id = :id")
    suspend fun review(id: Long, status: DataStatus, correctedCents: Long?, at: Long)

    /** Amostras confiáveis para a faixa pessoal de R$/km (validação por percentis). */
    @Query(
        """SELECT (COALESCE(correctedCents, offeredCents) * 1000) / (tripMeters + COALESCE(pickupMeters, 0))
           FROM offers WHERE status IN ('AUTO_ACCEPTED','CONFIRMED') AND tripMeters > 0
           AND COALESCE(correctedCents, offeredCents) > 0 ORDER BY detectedAt DESC LIMIT 300"""
    )
    suspend fun recentCentsPerKm(): List<Long>

    /** Retenção: snapshots brutos só existem para diagnóstico e por pouco tempo. */
    @Query("UPDATE offers SET rawSnapshot = NULL WHERE rawSnapshot IS NOT NULL AND detectedAt < :before")
    suspend fun clearSnapshotsBefore(before: Long): Int

    @Query("SELECT * FROM offers")
    suspend fun all(): List<OfferEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<OfferEntity>)

    /** Última oferta vista no intervalo (para reparar turnos esquecidos abertos). */
    @Query("SELECT MAX(detectedAt) FROM offers WHERE detectedAt BETWEEN :from AND :to")
    suspend fun lastDetectedBetween(from: Long, to: Long): Long?

    /** Ofertas da plataforma no intervalo que ainda não viraram corrida (nem inválidas). */
    @Query("SELECT * FROM offers WHERE platform = :platform AND detectedAt BETWEEN :from AND :to AND status != 'INVALID' AND id NOT IN (SELECT offerId FROM trips WHERE offerId IS NOT NULL)")
    suspend fun unlinkedBetween(platform: String, from: Long, to: Long): List<com.innexar.meuganho.data.local.entity.OfferEntity>

    /** Ofertas ainda "pendentes" há mais de [before] que não viraram corrida: não foram aceitas. */
    @Query("UPDATE offers SET outcome = 'NOT_ACCEPTED' WHERE outcome = 'PENDING' AND detectedAt < :before AND id NOT IN (SELECT offerId FROM trips WHERE offerId IS NOT NULL)")
    suspend fun resolveStalePending(before: Long): Int
}
