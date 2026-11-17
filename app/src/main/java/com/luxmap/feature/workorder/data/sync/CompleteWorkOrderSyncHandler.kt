package com.luxmap.feature.workorder.data.sync

import com.luxmap.core.network.WorkOrdersApi
import com.luxmap.core.network.errorCodeOrNull
import com.luxmap.core.sync.SyncOpHandler
import com.luxmap.core.sync.SyncOpResult
import com.luxmap.feature.workorder.data.dao.WorkOrderCompletionDao
import com.luxmap.feature.workorder.data.dto.CompleteWorkOrderRequestDto
import com.luxmap.feature.workorder.data.dto.FaultOutcomeRequestDto
import com.luxmap.feature.workorder.polegps.data.PoleGpsRepository
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException
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
            val payload = Json.decodeFromString<CompletionSyncPayload>(payloadJson)
            val completion =
                dao.completionByWorkOrderId(payload.workOrderId)
                    ?: return SyncOpResult.Failed("Local completion row missing")
            if (completion.submitStatus == "synced") return SyncOpResult.Done
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
                api.complete(
                    id = payload.workOrderId,
                    body =
                        CompleteWorkOrderRequestDto(
                            reportNote = completion.reportNote,
                            materialsUsed = completion.materialsUsed,
                            faultOutcomes = outcomes?.map { FaultOutcomeRequestDto(it.faultId, it.outcome) },
                            poleLocations = poleGpsRepository.completionPoleLocations(payload.workOrderId),
                        ),
                )
                dao.updateCompletionSubmitStatus(payload.workOrderId, "synced")
                SyncOpResult.Done
            } catch (e: HttpException) {
                when {
                    e.code() == 409 && e.errorCodeOrNull() == "AFTER_EVIDENCE_REQUIRED" -> SyncOpResult.RetryLater
                    e.code() == 409 -> {
                        dao.updateCompletionSubmitStatus(payload.workOrderId, "conflict")
                        SyncOpResult.Conflict(e.errorCodeOrNull() ?: "conflict")
                    }
                    e.code() == 400 || e.code() == 404 -> {
                        dao.updateCompletionSubmitStatus(payload.workOrderId, "failed")
                        SyncOpResult.Failed(e.errorCodeOrNull() ?: "HTTP ${e.code()}")
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
