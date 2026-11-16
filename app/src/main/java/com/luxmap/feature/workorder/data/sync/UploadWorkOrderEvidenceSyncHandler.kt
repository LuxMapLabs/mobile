package com.luxmap.feature.workorder.data.sync

import com.luxmap.core.network.WorkOrdersApi
import com.luxmap.core.network.errorCodeOrNull
import com.luxmap.core.sync.SyncOpHandler
import com.luxmap.core.sync.SyncOpResult
import com.luxmap.feature.workorder.data.dao.WorkOrderCompletionDao
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import java.io.File
import java.io.IOException
import javax.inject.Inject

internal const val EVIDENCE_REPLACEMENT_REQUIRED = "Evidence replacement required"

class UploadWorkOrderEvidenceSyncHandler
    @Inject
    constructor(
        private val api: WorkOrdersApi,
        private val dao: WorkOrderCompletionDao,
    ) : SyncOpHandler {
        override val opType = "upload_work_order_evidence"

        override suspend fun handle(
            payloadJson: String,
            onProgress: suspend (Long, Long) -> Unit,
        ): SyncOpResult {
            val payload = Json.decodeFromString<EvidenceSyncPayload>(payloadJson)
            val evidence =
                dao.evidenceByClientOpId(payload.clientOpId) ?: return SyncOpResult.Failed("Local evidence row missing")
            if (evidence.uploadStatus == "synced") return SyncOpResult.Done
            val file = File(evidence.filePath)
            if (!file.isFile) return SyncOpResult.Failed(EVIDENCE_REPLACEMENT_REQUIRED)

            return try {
                val filePart =
                    MultipartBody.Part.createFormData(
                        "file",
                        file.name,
                        file.asRequestBody("image/jpeg".toMediaType()),
                    )
                api.uploadEvidence(
                    id = evidence.workOrderId,
                    file = filePart,
                    kind = evidence.kind.toRequestBody(),
                    capturedAt = evidence.capturedAt.toString().toRequestBody(),
                    lat = evidence.lat.toString().toRequestBody(),
                    lng = evidence.lng.toString().toRequestBody(),
                    clientOpId = evidence.clientOpId.toRequestBody(),
                    poleId = evidence.poleId?.toRequestBody(),
                )
                dao.updateEvidenceUploadStatus(evidence.clientOpId, "synced")
                SyncOpResult.Done
            } catch (e: HttpException) {
                when (e.code()) {
                    409 -> SyncOpResult.Conflict(e.errorCodeOrNull() ?: "conflict")
                    400, 413, 415 -> SyncOpResult.Failed(EVIDENCE_REPLACEMENT_REQUIRED)
                    403, 404 -> SyncOpResult.Failed(e.errorCodeOrNull() ?: "HTTP ${e.code()}")
                    else -> SyncOpResult.RetryLater
                }
            } catch (e: IOException) {
                SyncOpResult.RetryLater
            }
        }
    }
