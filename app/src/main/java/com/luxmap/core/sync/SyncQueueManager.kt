package com.luxmap.core.sync

import kotlinx.coroutines.flow.Flow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncQueueManager
    @Inject
    constructor(
        private val dao: SyncQueueDao,
        private val syncTrigger: SyncTrigger,
    ) {
        val pendingCount: Flow<Int> = dao.observePendingCount()
        val errorCount: Flow<Int> = dao.observeErrorCount()
        val lastSyncedAt: Flow<Instant?> = dao.observeLastSyncedAt()

        suspend fun enqueue(
            opType: String,
            payloadJson: String,
            clientOpId: String,
            dependsOnClientOpId: String? = null,
        ) {
            val now = Instant.now()
            dao.insert(
                SyncQueueEntity(
                    clientOpId = clientOpId,
                    opType = opType,
                    payloadJson = payloadJson,
                    dependsOnClientOpId = dependsOnClientOpId,
                    status = "queued",
                    attemptCount = 0,
                    lastError = null,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            syncTrigger.triggerNow()
        }
    }
