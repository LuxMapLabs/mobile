package com.luxmap.feature.workorder.data

import com.luxmap.core.sync.SyncQueueDao
import com.luxmap.core.sync.SyncQueueManager
import com.luxmap.feature.workorder.data.dao.WorkOrderCompletionDao
import com.luxmap.feature.workorder.data.entity.LocalWorkOrderCompletionEntity
import com.luxmap.feature.workorder.data.entity.LocalWorkOrderEvidenceEntity
import com.luxmap.feature.workorder.data.sync.CompletionSyncPayload
import com.luxmap.feature.workorder.data.sync.EvidenceSyncPayload
import com.luxmap.feature.workorder.data.sync.FaultOutcome
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RealWorkOrderCompletionRepository
    @Inject
    constructor(
        private val dao: WorkOrderCompletionDao,
        private val syncQueueManager: SyncQueueManager,
        private val syncQueueDao: SyncQueueDao,
    ) : WorkOrderCompletionRepository {
        override fun observeEvidence(workOrderId: String): Flow<LocalWorkOrderEvidenceEntity?> =
            dao.observeLatestEvidence(workOrderId)

        override fun observeCompletion(workOrderId: String): Flow<LocalWorkOrderCompletionEntity?> =
            dao.observeCompletion(workOrderId)

        override fun observeCompletedWorkOrderIds(): Flow<Set<String>> =
            dao.observeAllCompletions().map { completions -> completions.map { it.workOrderId }.toSet() }

        @OptIn(ExperimentalCoroutinesApi::class)
        override fun observeCompletionSyncStates(): Flow<Map<String, CompletionSyncState>> =
            dao.observeAllCompletions().flatMapLatest { completions ->
                val unsynced = completions.filter { it.submitStatus != "synced" }
                if (unsynced.isEmpty()) {
                    flowOf(emptyMap())
                } else {
                    syncQueueDao.observeStatusesFor(unsynced.map { it.clientOpId }).map { ops ->
                        val statusByOp = ops.associate { it.clientOpId to it.status }
                        unsynced.associate { it.workOrderId to completionSyncState(statusByOp[it.clientOpId]) }
                    }
                }
            }

        private fun completionSyncState(opStatus: String?): CompletionSyncState =
            when (opStatus) {
                "failed" -> CompletionSyncState.FAILED
                "conflict" -> CompletionSyncState.CONFLICT
                else -> CompletionSyncState.PENDING
            }

        override suspend fun captureAfterEvidence(
            workOrderId: String,
            clientOpId: String,
            filePath: String,
            lat: Double,
            lng: Double,
            capturedAt: Instant,
        ) {
            dao.insertEvidence(
                LocalWorkOrderEvidenceEntity(
                    clientOpId = clientOpId,
                    workOrderId = workOrderId,
                    kind = "after",
                    filePath = filePath,
                    capturedAt = capturedAt,
                    lat = lat,
                    lng = lng,
                    uploadStatus = "pending",
                ),
            )
            syncQueueManager.enqueue(
                opType = "upload_work_order_evidence",
                payloadJson = json.encodeToString(EvidenceSyncPayload(clientOpId)),
                clientOpId = clientOpId,
            )
        }

        override suspend fun submitCompletion(
            workOrderId: String,
            taskKind: String,
            reportNote: String,
            materialsUsed: String?,
            faultOutcomes: List<FaultOutcome>?,
        ) {
            val clientOpId = UUID.randomUUID().toString()
            val dependsOn = if (taskKind == "repair") dao.latestEvidenceClientOpId(workOrderId) else null
            dao.insertOrReplaceCompletion(
                LocalWorkOrderCompletionEntity(
                    workOrderId = workOrderId,
                    reportNote = reportNote,
                    materialsUsed = materialsUsed,
                    faultOutcomesJson = faultOutcomes?.let { json.encodeToString(it) },
                    clientOpId = clientOpId,
                    submitStatus = "pending",
                ),
            )
            syncQueueManager.enqueue(
                opType = "complete_work_order",
                payloadJson = json.encodeToString(CompletionSyncPayload(workOrderId)),
                clientOpId = clientOpId,
                dependsOnClientOpId = dependsOn,
            )
        }

        override suspend fun retakeEvidence(workOrderId: String) {
            val clientOpId = dao.latestEvidenceClientOpId(workOrderId) ?: return
            val evidence = dao.evidenceByClientOpId(clientOpId) ?: return
            if (evidence.uploadStatus == "pending") {
                File(evidence.filePath).delete()
                dao.deleteEvidence(clientOpId)
            }
        }

        private companion object {
            val json = Json { ignoreUnknownKeys = true }
        }
    }
