package com.luxmap.feature.workorder.data.sync

import com.luxmap.core.network.ApiErrorBody
import com.luxmap.core.network.WorkOrdersApi
import com.luxmap.core.network.errorBodyOrNull
import com.luxmap.core.sync.SyncOpHandler
import com.luxmap.core.sync.SyncOpResult
import com.luxmap.feature.workorder.data.dao.WorkOrderCompletionDao
import com.luxmap.feature.workorder.data.dto.CompleteWorkOrderRequestDto
import com.luxmap.feature.workorder.data.dto.FaultOutcomeRequestDto
import com.luxmap.feature.workorder.data.dto.PoleLocationRequestDto
import com.luxmap.feature.workorder.data.dto.WorkOrderDetailDto
import com.luxmap.feature.workorder.data.entity.LocalWorkOrderCompletionEntity
import com.luxmap.feature.workorder.polegps.data.PoleGpsRepository
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import retrofit2.HttpException
import java.io.IOException
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject

class CompleteWorkOrderSyncHandler
    @Inject
    constructor(
        private val api: WorkOrdersApi,
        private val dao: WorkOrderCompletionDao,
        private val poleGpsRepository: PoleGpsRepository,
    ) : SyncOpHandler {
        override val opType = "complete_work_order"

        override suspend fun handle(
            payloadJson: String,
            onProgress: suspend (Long, Long) -> Unit,
        ): SyncOpResult {
            var payload = Json.decodeFromString<CompletionSyncPayload>(payloadJson)
            val completion =
                dao.completionByWorkOrderId(payload.workOrderId)
                    ?: return SyncOpResult.Failed("Local completion row missing")
            if (completion.submitStatus == "synced") return SyncOpResult.Done
            if (payload.poleLocations == null) {
                try {
                    payload = poleGpsRepository.legacyCompletionPayload(completion) ?: payload
                } catch (e: IllegalStateException) {
                    dao.updateCompletionSubmitStatus(payload.workOrderId, "failed")
                    return SyncOpResult.Failed(e.message ?: "Không phục hồi được báo cáo GPS. Hãy đo lại.")
                }
            }
            if (payload.poleLocations != null &&
                (payload.poleLocations!!.isEmpty() || payload.evidenceClientOpIds.isNullOrEmpty())
            ) {
                dao.updateCompletionSubmitStatus(payload.workOrderId, "failed")
                return SyncOpResult.Failed("Báo cáo GPS thiếu tọa độ hoặc liên kết ảnh. Hãy đo lại.")
            }
            val evidenceStates =
                payload.evidenceClientOpIds.orEmpty().associateWith {
                    dao.completionEvidenceStatus(payload.workOrderId, it)
                }
            val conflict = evidenceStates.entries.firstOrNull { it.value == "conflict" }
            if (conflict != null) {
                dao.updateCompletionSubmitStatus(payload.workOrderId, "conflict")
                return SyncOpResult.Conflict("Evidence upload conflict: ${conflict.key}")
            }
            val failed = evidenceStates.entries.firstOrNull { it.value == null || it.value == "failed" }
            if (failed != null) {
                dao.updateCompletionSubmitStatus(payload.workOrderId, "failed")
                return SyncOpResult.Failed("Evidence upload failed: ${failed.key}")
            }
            if (evidenceStates.values.any { it != "done" }) return SyncOpResult.WaitingForDependency
            val reviewStates =
                payload.poleReviewClientOpIds.orEmpty().associateWith {
                    dao.submissionOperation(it, "submit_pole_photo_review")?.status
                }
            val blockedReview =
                reviewStates.entries.firstOrNull { it.value == null || it.value in terminalReviewErrors }
            if (blockedReview != null) {
                dao.updateCompletionSubmitStatus(payload.workOrderId, "failed")
                return SyncOpResult.Failed("Pole review failed: ${blockedReview.key}")
            }
            if (reviewStates.values.any { it != "done" }) return SyncOpResult.WaitingForDependency
            val outcomes = completion.faultOutcomesJson?.let { Json.decodeFromString<List<FaultOutcome>>(it) }

            return try {
                if (payload.poleLocations != null) {
                    // GET also resolves an interrupted POST whose server transaction already committed.
                    val remote = api.detail(payload.workOrderId)
                    if (remote.taskKind != "pole_gps_update") {
                        dao.updateCompletionSubmitStatus(payload.workOrderId, "conflict")
                        return SyncOpResult.Conflict("Loại công việc trên máy chủ đã thay đổi.")
                    }
                    val accepted = remote.woStatus in setOf("done", "verified")
                    val returned = remote.woStatus == "in_progress" && !remote.reviewNote.isNullOrBlank()
                    val mayHaveBeenSent = accepted || (returned && payload.postStarted)
                    if (mayHaveBeenSent && remote.matchesGpsReport(completion, payload.poleLocations!!)) {
                        // A manager may have returned an accepted report while the app was interrupted.
                        // Do not undo that decision by automatically POSTing the old report again.
                        dao.updateCompletionSubmitStatus(payload.workOrderId, "synced")
                        return SyncOpResult.Done
                    }
                    if (accepted) {
                        dao.updateCompletionSubmitStatus(payload.workOrderId, "conflict")
                        return SyncOpResult.Conflict("Máy chủ đã nhận một báo cáo GPS khác. Cần đối chiếu.")
                    }
                }
                if (payload.poleLocations != null && !payload.postStarted) {
                    payload = payload.copy(postStarted = true)
                    dao.updateCompletionPayload(completion.clientOpId, Json.encodeToString(payload))
                }
                api.complete(
                    id = payload.workOrderId,
                    body =
                        CompleteWorkOrderRequestDto(
                            reportNote = completion.reportNote,
                            materialsUsed = completion.materialsUsed,
                            faultOutcomes = outcomes?.map { FaultOutcomeRequestDto(it.faultId, it.outcome) },
                            poleLocations = payload.poleLocations,
                        ),
                )
                dao.updateCompletionSubmitStatus(payload.workOrderId, "synced")
                SyncOpResult.Done
            } catch (e: HttpException) {
                val error = e.errorBodyOrNull()
                when {
                    e.code() == 409 && error?.code == "AFTER_EVIDENCE_REQUIRED" -> SyncOpResult.RetryLater
                    e.code() == 409 && error?.code == POLE_EVIDENCE_REQUIRED -> {
                        poleGpsRepository.markPhotosMissingOnServer(payload.workOrderId, error.poleIds())
                        dao.updateCompletionSubmitStatus(payload.workOrderId, "failed")
                        SyncOpResult.Failed(POLE_EVIDENCE_REQUIRED)
                    }
                    e.code() == 409 -> {
                        dao.updateCompletionSubmitStatus(payload.workOrderId, "conflict")
                        SyncOpResult.Conflict(error?.code ?: "conflict")
                    }
                    e.code() == 400 || e.code() == 404 -> {
                        dao.updateCompletionSubmitStatus(payload.workOrderId, "failed")
                        SyncOpResult.Failed(error?.code ?: "HTTP ${e.code()}")
                    }
                    else -> SyncOpResult.RetryLater
                }
            } catch (e: IOException) {
                SyncOpResult.RetryLater
            }
        }

        private companion object {
            val terminalReviewErrors = setOf("failed", "conflict")
        }
    }

private const val POLE_EVIDENCE_REQUIRED = "POLE_EVIDENCE_REQUIRED"

private fun ApiErrorBody.poleIds(): List<String> =
    ((details as? JsonObject)?.get("pole_ids") as? JsonArray)
        ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        .orEmpty()

private fun WorkOrderDetailDto.matchesGpsReport(
    completion: LocalWorkOrderCompletionEntity,
    expected: List<PoleLocationRequestDto>,
): Boolean {
    if (reportNote?.trim() != completion.reportNote.trim() ||
        materialsUsed.orEmpty().trim() != completion.materialsUsed.orEmpty().trim() ||
        poleLocations.size != expected.size
    ) {
        return false
    }
    val actual = poleLocations.associateBy { it.poleId }
    return actual.size == expected.size &&
        expected.all { sent ->
            val received = actual[sent.poleId]
            val sentAt = normalizedGpsTime(sent.capturedAt)
            received != null && sentAt != null && received.lat == sent.lat && received.lng == sent.lng &&
                received.accuracyM == sent.accuracyM && normalizedGpsTime(received.capturedAt) == sentAt
        }
}

private fun normalizedGpsTime(value: String) =
    runCatching { OffsetDateTime.parse(value).toInstant().truncatedTo(ChronoUnit.MICROS) }.getOrNull()
