package com.luxmap.feature.workorder.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.luxmap.core.sync.SYNC_ATTEMPTS_EXHAUSTED
import com.luxmap.core.sync.SyncQueueEntity
import com.luxmap.feature.workorder.data.entity.LocalWorkOrderCompletionEntity
import com.luxmap.feature.workorder.data.entity.LocalWorkOrderEvidenceEntity
import com.luxmap.feature.workorder.data.sync.CompletionSyncPayload
import com.luxmap.feature.workorder.data.sync.EVIDENCE_REPLACEMENT_REQUIRED
import com.luxmap.feature.workorder.data.sync.EvidenceSyncPayload
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant

@Dao
interface WorkOrderCompletionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEvidence(evidence: LocalWorkOrderEvidenceEntity)

    @Transaction
    suspend fun insertDraftEvidence(evidence: LocalWorkOrderEvidenceEntity) {
        val completion = completionByWorkOrderId(evidence.workOrderId)
        check(completion == null || completion.submitStatus == "draft") { "Work order has already been submitted" }
        insertEvidence(evidence)
    }

    @Query(
        """
        SELECT e.clientOpId, e.workOrderId, e.kind, e.filePath, e.capturedAt, e.lat, e.lng, e.poleId,
          CASE WHEN e.uploadStatus = 'synced' THEN 'synced'
            ELSE COALESCE((
              SELECT CASE
                WHEN status = 'failed' AND lastError = '$SYNC_ATTEMPTS_EXHAUSTED' THEN 'retryable_error'
                WHEN status = 'failed' AND lastError = '$EVIDENCE_REPLACEMENT_REQUIRED' THEN 'replaceable_error'
                WHEN status IN ('failed', 'conflict', 'syncing') THEN status
                WHEN status = 'done' THEN 'synced'
              END FROM sync_queue WHERE clientOpId = e.clientOpId AND opType = 'upload_work_order_evidence'
              ORDER BY id DESC LIMIT 1
            ), e.uploadStatus)
          END AS uploadStatus
        FROM local_work_order_evidence e WHERE e.workOrderId = :workOrderId
        ORDER BY e.capturedAt DESC, e.clientOpId DESC
        """,
    )
    fun observeEvidenceList(workOrderId: String): Flow<List<LocalWorkOrderEvidenceEntity>>

    @Query(
        "SELECT * FROM local_work_order_evidence WHERE workOrderId = :workOrderId AND kind = :kind " +
            "ORDER BY capturedAt ASC, clientOpId ASC",
    )
    suspend fun evidenceForCompletion(
        workOrderId: String,
        kind: String,
    ): List<LocalWorkOrderEvidenceEntity>

    @Query(
        "SELECT CASE WHEN evidence.uploadStatus = 'synced' THEN 'done' ELSE (" +
            "SELECT status FROM sync_queue WHERE clientOpId = evidence.clientOpId " +
            "AND opType = 'upload_work_order_evidence' ORDER BY id DESC LIMIT 1) END " +
            "FROM local_work_order_evidence AS evidence WHERE workOrderId = :workOrderId AND clientOpId = :clientOpId",
    )
    suspend fun completionEvidenceStatus(
        workOrderId: String,
        clientOpId: String,
    ): String?

    @Insert
    suspend fun insertSubmissionQueueRows(rows: List<SyncQueueEntity>)

    @Transaction
    suspend fun stageEvidenceCompletion(
        completion: LocalWorkOrderCompletionEntity,
        kind: String,
    ): Boolean {
        val workOrderId = completion.workOrderId
        val previous = completionByWorkOrderId(workOrderId)
        if (previous != null && previous.submitStatus != "draft") return false

        val evidence = evidenceForCompletion(workOrderId, kind)
        check(kind != "after" || evidence.isNotEmpty()) { "An after photo is required for repair completion" }
        val now = Instant.now()
        val operations =
            buildList {
                for (photo in evidence) {
                    if (previous != null) resetRetryablePhoto(photo.clientOpId, now)
                    if (completionEvidenceStatus(workOrderId, photo.clientOpId) != null) continue
                    updateEvidenceUploadStatus(photo.clientOpId, "pending")
                    add(
                        submissionQueueRow(
                            photo.clientOpId,
                            "upload_work_order_evidence",
                            Json.encodeToString(EvidenceSyncPayload(photo.clientOpId)),
                            now,
                        ),
                    )
                }
                val payload = CompletionSyncPayload(workOrderId, evidence.map { it.clientOpId })
                add(submissionQueueRow(completion.clientOpId, "complete_work_order", Json.encodeToString(payload), now))
            }
        insertOrReplaceCompletion(completion)
        insertSubmissionQueueRows(operations)
        return true
    }

    @Query(
        "UPDATE sync_queue SET payloadJson = :payloadJson WHERE clientOpId = :clientOpId " +
            "AND opType = 'complete_work_order'",
    )
    suspend fun updateCompletionPayload(
        clientOpId: String,
        payloadJson: String,
    )

    @Query("SELECT * FROM sync_queue WHERE clientOpId = :clientOpId AND opType = :opType ORDER BY id DESC LIMIT 1")
    suspend fun submissionOperation(
        clientOpId: String,
        opType: String,
    ): SyncQueueEntity?

    @Query(
        """
        SELECT photo.photoId || ':review'
        FROM local_pole_photo AS photo
        WHERE photo.workOrderId = :workOrderId AND photo.draftFinalCvState IS NOT NULL
            AND photo.photoId = (
                SELECT latest.photoId FROM local_pole_photo AS latest
                WHERE latest.workOrderId = photo.workOrderId AND latest.poleId = photo.poleId
                ORDER BY latest.capturedAt DESC, latest.rowid DESC LIMIT 1
            )
        """,
    )
    suspend fun pendingLatestPoleReviewIds(workOrderId: String): List<String>

    // The upload op of a pole photo/video uses the local photoId as its clientOpId.
    @Query(
        """
        SELECT photo.photoId FROM local_pole_photo AS photo
        WHERE photo.workOrderId = :workOrderId
            AND photo.photoId = (
                SELECT latest.photoId FROM local_pole_photo AS latest
                WHERE latest.workOrderId = photo.workOrderId AND latest.poleId = photo.poleId
                ORDER BY latest.capturedAt DESC, latest.rowid DESC LIMIT 1
            )
        """,
    )
    suspend fun latestPoleMediaIds(workOrderId: String): List<String>

    @Query("SELECT status FROM sync_queue WHERE clientOpId = :clientOpId ORDER BY id DESC LIMIT 1")
    suspend fun operationStatus(clientOpId: String): String?

    @Query("DELETE FROM sync_queue WHERE id = :id AND status = 'failed'")
    suspend fun deleteFailedOperation(id: Long)

    @Query(
        "UPDATE sync_queue SET status = 'queued', attemptCount = 0, lastError = NULL, updatedAt = :now " +
            "WHERE opType = 'upload_work_order_evidence' AND clientOpId = :clientOpId " +
            "AND status = 'failed' AND lastError = '$SYNC_ATTEMPTS_EXHAUSTED'",
    )
    suspend fun resetRetryablePhoto(
        clientOpId: String,
        now: Instant,
    )

    @Query(
        "UPDATE sync_queue SET status = 'queued', attemptCount = 0, lastError = NULL, updatedAt = :now " +
            "WHERE id = :id AND status = 'failed'",
    )
    suspend fun resetFailedCompletion(
        id: Long,
        now: Instant,
    )

    @Transaction
    suspend fun retryEvidenceSubmission(workOrderId: String): Boolean {
        val operation = failedEvidenceCompletion(workOrderId) ?: return false
        val failed = failedPhotoOperations(operation)
        if (failed.isEmpty() || failed.any { it.lastError != SYNC_ATTEMPTS_EXHAUSTED }) return false
        val now = Instant.now()
        for (photo in failed) resetRetryablePhoto(photo.clientOpId, now)
        resetFailedCompletion(operation.id, now)
        updateCompletionSubmitStatus(workOrderId, "pending")
        return true
    }

    @Transaction
    suspend fun reopenEvidenceSubmission(workOrderId: String): List<String>? {
        val operation = failedEvidenceCompletion(workOrderId) ?: return null
        val rejected = failedPhotoOperations(operation).filter { it.lastError == EVIDENCE_REPLACEMENT_REQUIRED }
        if (rejected.isEmpty()) return null
        val files = rejected.mapNotNull { evidenceByClientOpId(it.clientOpId)?.filePath }
        for (photo in rejected) {
            deleteFailedOperation(photo.id)
            deleteEvidence(photo.clientOpId)
        }
        deleteFailedOperation(operation.id)
        updateCompletionSubmitStatus(workOrderId, "draft")
        return files
    }

    private suspend fun failedEvidenceCompletion(workOrderId: String): SyncQueueEntity? {
        val report = completionByWorkOrderId(workOrderId)?.takeIf { it.submitStatus == "failed" } ?: return null
        return submissionOperation(report.clientOpId, "complete_work_order")?.takeIf { it.status == "failed" }
    }

    private suspend fun failedPhotoOperations(report: SyncQueueEntity): List<SyncQueueEntity> {
        val payload = Json.decodeFromString<CompletionSyncPayload>(report.payloadJson)
        return payload.evidenceClientOpIds.orEmpty().mapNotNull { clientOpId ->
            submissionOperation(clientOpId, "upload_work_order_evidence")?.takeIf { it.status == "failed" }
        }
    }

    @Query(
        "DELETE FROM local_work_order_evidence WHERE workOrderId = :workOrderId AND clientOpId = :clientOpId " +
            "AND uploadStatus = 'draft' AND NOT EXISTS " +
            "(SELECT 1 FROM local_work_order_completion WHERE workOrderId = :workOrderId AND submitStatus != 'draft')",
    )
    suspend fun deleteDraftEvidence(
        workOrderId: String,
        clientOpId: String,
    ): Int

    @Query("SELECT * FROM local_work_order_evidence WHERE workOrderId = :workOrderId ORDER BY capturedAt DESC LIMIT 1")
    fun observeLatestEvidence(workOrderId: String): Flow<LocalWorkOrderEvidenceEntity?>

    @Query("SELECT * FROM local_work_order_evidence WHERE clientOpId = :clientOpId LIMIT 1")
    suspend fun evidenceByClientOpId(clientOpId: String): LocalWorkOrderEvidenceEntity?

    @Query(
        "SELECT clientOpId FROM local_work_order_evidence WHERE workOrderId = :workOrderId " +
            "ORDER BY capturedAt DESC LIMIT 1",
    )
    suspend fun latestEvidenceClientOpId(workOrderId: String): String?

    @Query("UPDATE local_work_order_evidence SET uploadStatus = :status WHERE clientOpId = :clientOpId")
    suspend fun updateEvidenceUploadStatus(
        clientOpId: String,
        status: String,
    )

    @Query("DELETE FROM local_work_order_evidence WHERE clientOpId = :clientOpId")
    suspend fun deleteEvidence(clientOpId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplaceCompletion(completion: LocalWorkOrderCompletionEntity)

    @Query("SELECT * FROM local_work_order_completion WHERE workOrderId = :workOrderId LIMIT 1")
    fun observeCompletion(workOrderId: String): Flow<LocalWorkOrderCompletionEntity?>

    @Query("SELECT * FROM local_work_order_completion WHERE submitStatus != 'draft'")
    fun observeAllCompletions(): Flow<List<LocalWorkOrderCompletionEntity>>

    @Query("SELECT * FROM local_work_order_completion WHERE workOrderId = :workOrderId LIMIT 1")
    suspend fun completionByWorkOrderId(workOrderId: String): LocalWorkOrderCompletionEntity?

    @Query(
        "DELETE FROM local_work_order_completion WHERE workOrderId = :workOrderId " +
            "AND clientOpId = :previousClientOpId AND submitStatus = 'synced'",
    )
    suspend fun deleteSyncedCompletion(
        workOrderId: String,
        previousClientOpId: String,
    )

    @Query("UPDATE local_work_order_completion SET submitStatus = :status WHERE workOrderId = :workOrderId")
    suspend fun updateCompletionSubmitStatus(
        workOrderId: String,
        status: String,
    )
}

private fun submissionQueueRow(
    clientOpId: String,
    opType: String,
    payloadJson: String,
    now: Instant,
) = SyncQueueEntity(
    clientOpId = clientOpId,
    opType = opType,
    payloadJson = payloadJson,
    dependsOnClientOpId = null,
    status = "queued",
    createdAt = now,
    updatedAt = now,
)
