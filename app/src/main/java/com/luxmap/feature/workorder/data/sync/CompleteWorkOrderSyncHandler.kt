package com.luxmap.feature.workorder.data.sync

import com.luxmap.core.network.WorkOrdersApi
import com.luxmap.core.network.errorCodeOrNull
import com.luxmap.core.sync.SyncOpHandler
import com.luxmap.core.sync.SyncOpResult
import com.luxmap.feature.workorder.data.dao.WorkOrderCompletionDao
import com.luxmap.feature.workorder.data.dto.CompleteWorkOrderRequestDto
import com.luxmap.feature.workorder.data.dto.FaultOutcomeRequestDto
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
    ) : SyncOpHandler {
        override val opType = "complete_work_order"

        override suspend fun handle(payloadJson: String): SyncOpResult {
            val payload = Json.decodeFromString<CompletionSyncPayload>(payloadJson)
            val completion =
                dao.completionByWorkOrderId(payload.workOrderId)
                    ?: return SyncOpResult.Failed("Local completion row missing")
            val outcomes = completion.faultOutcomesJson?.let { Json.decodeFromString<List<FaultOutcome>>(it) }

            return try {
                api.complete(
                    id = payload.workOrderId,
                    body =
                        CompleteWorkOrderRequestDto(
                            reportNote = completion.reportNote,
                            materialsUsed = completion.materialsUsed,
                            faultOutcomes = outcomes?.map { FaultOutcomeRequestDto(it.faultId, it.outcome) },
                        ),
                )
                dao.updateCompletionSubmitStatus(payload.workOrderId, "synced")
                SyncOpResult.Done
            } catch (e: HttpException) {
                when {
                    e.code() == 409 && e.errorCodeOrNull() == "AFTER_EVIDENCE_REQUIRED" -> SyncOpResult.RetryLater
                    e.code() == 409 -> SyncOpResult.Conflict(e.errorCodeOrNull() ?: "conflict")
                    e.code() == 400 || e.code() == 404 -> SyncOpResult.Failed(e.errorCodeOrNull() ?: "HTTP ${e.code()}")
                    else -> SyncOpResult.RetryLater
                }
            } catch (e: IOException) {
                SyncOpResult.RetryLater
            }
        }
    }
