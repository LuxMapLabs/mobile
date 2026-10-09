package com.luxmap.core.sync

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

// Singleton so every caller (the background SyncWorker and RealUploadRepository's foreground
// retry) shares the same Mutex - an unscoped instance per injection site would give each caller
// its own lock, which would not stop two callers from draining sync_queue at the same time.
@Singleton
class SyncQueueProcessor
    @Inject
    constructor(
        private val dao: SyncQueueDao,
        private val handlers: Set<@JvmSuppressWildcards SyncOpHandler>,
    ) {
        private val mutex = Mutex()

        suspend fun processQueuedOps(
            onRowProgress: suspend (clientOpId: String, bytesSent: Long, totalBytes: Long) -> Unit = { _, _, _ -> },
        ): Boolean =
            mutex.withLock {
                var stillPending = false
                for (row in dao.queuedRows()) {
                    if (row.dependsOnClientOpId != null) {
                        when (dao.statusOf(row.dependsOnClientOpId)) {
                            "done" -> Unit
                            "failed", "conflict" ->
                                dao.updateStatus(
                                    row.id,
                                    "failed",
                                    row.attemptCount,
                                    "Dependency op failed",
                                    Instant.now(),
                                )
                            else -> stillPending = true
                        }
                        if (dao.statusOf(row.dependsOnClientOpId) != "done") continue
                    }

                    if (row.attemptCount >= MAX_ATTEMPTS) {
                        dao.updateStatus(row.id, "failed", row.attemptCount, "Exceeded retry attempts", Instant.now())
                        continue
                    }

                    val handler = handlers.firstOrNull { it.opType == row.opType }
                    if (handler == null) {
                        dao.updateStatus(
                            row.id,
                            "failed",
                            row.attemptCount,
                            "No handler for ${row.opType}",
                            Instant.now(),
                        )
                        continue
                    }

                    when (
                        val result =
                            handler.handle(
                                row.payloadJson,
                            ) { sent, total -> onRowProgress(row.clientOpId, sent, total) }
                    ) {
                        is SyncOpResult.Done -> dao.updateStatus(row.id, "done", row.attemptCount, null, Instant.now())
                        is SyncOpResult.RetryLater -> {
                            dao.updateStatus(row.id, "queued", row.attemptCount + 1, null, Instant.now())
                            stillPending = true
                        }
                        is SyncOpResult.Failed ->
                            dao.updateStatus(row.id, "failed", row.attemptCount, result.message, Instant.now())
                        is SyncOpResult.Conflict ->
                            dao.updateStatus(row.id, "conflict", row.attemptCount, result.message, Instant.now())
                    }
                }
                stillPending
            }

        companion object {
            const val MAX_ATTEMPTS = 8
        }
    }
