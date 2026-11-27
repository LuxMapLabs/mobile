package com.luxmap.core.sync

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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

        // A work order started without signal has a queued start op (see StartWorkOrderSyncHandler).
        // Everything else sent for that order - photos, lux readings, evidence, the completion -
        // must wait for it, or the server refuses it as not in progress. Returns that op's
        // clientOpId to pass as dependsOnClientOpId, or null once the start has gone through.
        suspend fun pendingStartOf(workOrderId: String): String? {
            val opId = startOpId(workOrderId)
            return opId.takeIf { dao.statusOf(it).let { status -> status != null && status != "done" } }
        }

        // Suspends until the offline start of this work order has reached the server.
        suspend fun awaitStartSent(workOrderId: String) {
            dao.observeStatusesFor(listOf(startOpId(workOrderId))).first { rows -> rows.all { it.status == "done" } }
        }

        suspend fun triggerSync() {
            syncTrigger.triggerNow()
        }

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
            triggerSync()
        }

        companion object {
            const val START_WORK_ORDER_OP = "start_work_order"

            fun startOpId(workOrderId: String) = "start:$workOrderId"
        }
    }
