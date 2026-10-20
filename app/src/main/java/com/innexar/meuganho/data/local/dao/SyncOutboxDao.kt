package com.innexar.meuganho.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.innexar.meuganho.data.local.entity.SyncOutboxEntity

@Dao
interface SyncOutboxDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(item: SyncOutboxEntity)

    @Query("SELECT * FROM sync_outbox WHERE kind = :kind ORDER BY createdAt LIMIT :limit")
    suspend fun pending(kind: String, limit: Int): List<SyncOutboxEntity>

    @Query("SELECT COUNT(*) FROM sync_outbox")
    suspend fun count(): Int

    @Query("SELECT MIN(createdAt) FROM sync_outbox")
    suspend fun oldestAt(): Long?

    /** Só remove se não foi reenfileirado depois do envio (createdAt igual). */
    @Query("DELETE FROM sync_outbox WHERE kind = :kind AND localId = :localId AND createdAt = :createdAt")
    suspend fun remove(kind: String, localId: Long, createdAt: Long)
}
