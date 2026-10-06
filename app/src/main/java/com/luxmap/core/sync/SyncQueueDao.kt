package com.luxmap.core.sync

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import java.time.Instant

@Dao
interface SyncQueueDao {
    @Insert
    suspend fun insert(row: SyncQueueEntity): Long

    @Query("SELECT * FROM sync_queue WHERE status = 'queued' ORDER BY createdAt ASC")
    suspend fun queuedRows(): List<SyncQueueEntity>

    @Query("SELECT status FROM sync_queue WHERE clientOpId = :clientOpId LIMIT 1")
    suspend fun statusOf(clientOpId: String): String?

    @Query(
        "UPDATE sync_queue SET status = :status, attemptCount = :attemptCount, lastError = :lastError, " +
            "updatedAt = :updatedAt WHERE id = :id",
    )
    suspend fun updateStatus(
        id: Long,
        status: String,
        attemptCount: Int,
        lastError: String?,
        updatedAt: Instant,
    )
}
