package com.luxmap.feature.workorder.data

import com.luxmap.core.sync.SyncQueueManager
import com.luxmap.feature.workorder.data.dao.WorkOrderCompletionDao
import com.luxmap.feature.workorder.data.entity.LocalWorkOrderCompletionEntity
import com.luxmap.feature.workorder.data.entity.LocalWorkOrderEvidenceEntity
import com.luxmap.feature.workorder.data.sync.CompletionSyncPayload
import com.luxmap.feature.workorder.data.sync.EvidenceSyncPayload
import com.luxmap.feature.workorder.data.sync.FaultOutcome
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
    ) : WorkOrderCompletionRepository {
        override fun observeEvidence(workOrderId: String): Flow<LocalWorkOrderEvidenceEntity?> =
            dao.observeLatestEvidence(workOrderId)

        override fun observeCompletion(workOrderId: String): Flow<LocalWorkOrderCompletionEntity?> =
            dao.observeCompletion(workOrderId)

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

        private companion object {
            val json = Json { ignoreUnknownKeys = true }
        }
    }
