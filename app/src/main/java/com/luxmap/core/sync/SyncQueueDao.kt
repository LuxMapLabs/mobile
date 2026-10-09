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

    // Only resets 'failed' rows, never 'conflict' - a conflict must stay for a human to resolve,
    // not get silently retried.
    @Query(
        "UPDATE sync_queue SET status = 'queued', attemptCount = 0, lastError = null, updatedAt = :updatedAt " +
            "WHERE clientOpId IN (:clientOpIds) AND status = 'failed'",
    )
    suspend fun resetFailedOps(
        clientOpIds: List<String>,
        updatedAt: Instant,
    )

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
