package com.luxmap.core.sync

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "sync_queue")
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val clientOpId: String,
    val opType: String,
    val payloadJson: String,
    val dependsOnClientOpId: String?,
    val status: String,
    val attemptCount: Int = 0,
    val lastError: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
)
