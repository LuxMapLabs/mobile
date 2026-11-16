package com.luxmap.feature.workorder.data

import com.luxmap.core.sync.SyncQueueManager
import com.luxmap.feature.workorder.data.dao.WorkOrderCompletionDao
import com.luxmap.feature.workorder.data.entity.LocalWorkOrderCompletionEntity
import com.luxmap.feature.workorder.data.entity.LocalWorkOrderEvidenceEntity
import com.luxmap.feature.workorder.data.sync.CompletionSyncPayload
import com.luxmap.feature.workorder.data.sync.EvidenceSyncPayload
import com.luxmap.feature.workorder.data.sync.FaultOutcome
import kotlinx.coroutines.flow.Flow
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
    ) : WorkOrderCompletionRepository {
        override fun observeEvidence(workOrderId: String): Flow<LocalWorkOrderEvidenceEntity?> =
            dao.observeLatestEvidence(workOrderId)

        override fun observeEvidenceList(workOrderId: String): Flow<List<LocalWorkOrderEvidenceEntity>> =
            dao.observeEvidenceList(workOrderId)

        override fun observeCompletion(workOrderId: String): Flow<LocalWorkOrderCompletionEntity?> =
            dao.observeCompletion(workOrderId)

        override fun observeCompletedWorkOrderIds(): Flow<Set<String>> =
            dao.observeAllCompletions().map { completions -> completions.map { it.workOrderId }.toSet() }

        override suspend fun captureEvidence(
            workOrderId: String,
            kind: EvidenceKind,
            clientOpId: String,
            filePath: String,
            lat: Double,
            lng: Double,
            capturedAt: Instant,
        ) {
            dao.insertDraftEvidence(
                LocalWorkOrderEvidenceEntity(
                    clientOpId = clientOpId,
                    workOrderId = workOrderId,
                    kind = kind.wireValue,
                    filePath = filePath,
                    capturedAt = capturedAt,
                    lat = lat,
                    lng = lng,
                    uploadStatus = "draft",
                ),
            )
        }

        override suspend fun removeDraftEvidence(
            workOrderId: String,
            clientOpId: String,
        ) {
            val evidence = dao.evidenceByClientOpId(clientOpId) ?: return
            if (dao.deleteDraftEvidence(workOrderId, clientOpId) == 1) {
                File(evidence.filePath).delete()
            }
        }

        override suspend fun prepareEvidenceRework(
            workOrderId: String,
            previousClientOpId: String,
        ) {
            dao.deleteSyncedCompletion(workOrderId, previousClientOpId)
        }

        override suspend fun retryEvidenceSubmission(workOrderId: String): Boolean {
            if (!dao.retryEvidenceSubmission(workOrderId)) return false
            syncQueueManager.triggerSync()
            return true
        }

        override suspend fun reopenEvidenceSubmission(workOrderId: String): Boolean {
            val discardedFiles = dao.reopenEvidenceSubmission(workOrderId) ?: return false
            discardedFiles.forEach { File(it).delete() }
            return true
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
            val completion =
                LocalWorkOrderCompletionEntity(
                    workOrderId = workOrderId,
                    reportNote = reportNote,
                    materialsUsed = materialsUsed,
                    faultOutcomesJson = faultOutcomes?.let { json.encodeToString(it) },
                    clientOpId = clientOpId,
                    submitStatus = "pending",
                )
            val evidenceKind =
                when (taskKind) {
                    "repair" -> EvidenceKind.AFTER
                    "inspection" -> EvidenceKind.OBSERVATION
                    else -> null
                }
            if (evidenceKind != null) {
                if (dao.stageEvidenceCompletion(completion, evidenceKind.wireValue)) {
                    syncQueueManager.triggerSync()
                }
                return
            }
            dao.insertOrReplaceCompletion(completion)
            syncQueueManager.enqueue(
                opType = "complete_work_order",
                payloadJson = json.encodeToString(CompletionSyncPayload(workOrderId)),
                clientOpId = clientOpId,
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
