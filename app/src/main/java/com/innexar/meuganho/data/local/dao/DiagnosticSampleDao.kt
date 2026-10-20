package com.innexar.meuganho.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.innexar.meuganho.data.local.entity.DiagnosticSampleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DiagnosticSampleDao {
    @Insert
    suspend fun insert(sample: DiagnosticSampleEntity): Long

    @Query("SELECT * FROM diagnostic_samples WHERE uploaded = 0 ORDER BY capturedAt LIMIT :limit")
    suspend fun pending(limit: Int): List<DiagnosticSampleEntity>

    @Query("SELECT COUNT(*) FROM diagnostic_samples WHERE uploaded = 0")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM diagnostic_samples WHERE uploaded = 0")
    suspend fun pendingCount(): Int

    @Query("SELECT MIN(capturedAt) FROM diagnostic_samples WHERE uploaded = 0")
    suspend fun oldestPendingAt(): Long?

    @Query("UPDATE diagnostic_samples SET uploaded = 1 WHERE id IN (:ids)")
    suspend fun markUploaded(ids: List<Long>)

    /** Última amostra de cada (plataforma, tipo de tela) para a tela de Diagnóstico. */
    @Query(
        """SELECT * FROM diagnostic_samples WHERE id IN
           (SELECT MAX(id) FROM diagnostic_samples GROUP BY platform, screenType, reason)
           ORDER BY platform, screenType"""
    )
    fun observeLatestPerType(): Flow<List<DiagnosticSampleEntity>>

    // Teto de armazenamento: enviadas somem após 3 dias; a fila nunca passa de 1000.
    @Query("DELETE FROM diagnostic_samples WHERE uploaded = 1 AND capturedAt < :before")
    suspend fun deleteUploadedBefore(before: Long)

    @Query(
        """DELETE FROM diagnostic_samples WHERE uploaded = 0 AND id NOT IN
           (SELECT id FROM diagnostic_samples WHERE uploaded = 0 ORDER BY capturedAt DESC LIMIT :keep)"""
    )
    suspend fun trimPending(keep: Int)
}
