package com.luxmap.feature.workorder.data.sync

import com.luxmap.core.network.WorkOrdersApi
import com.luxmap.core.network.errorCodeOrNull
import com.luxmap.core.sync.SyncOpHandler
import com.luxmap.core.sync.SyncOpResult
import com.luxmap.core.sync.SyncQueueManager
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

@Serializable
data class StartWorkOrderSyncPayload(
    @SerialName("work_order_id") val workOrderId: String,
)

// Sends a start the engineer made without signal (RealWorkOrderDetailRepository.start). Photos,
// readings and the completion for that order are queued behind it.
class StartWorkOrderSyncHandler
    @Inject
    constructor(
        private val api: WorkOrdersApi,
    ) : SyncOpHandler {
        override val opType = SyncQueueManager.START_WORK_ORDER_OP

        override suspend fun handle(
            payloadJson: String,
            onProgress: suspend (Long, Long) -> Unit,
        ): SyncOpResult {
            val payload = Json.decodeFromString<StartWorkOrderSyncPayload>(payloadJson)
            return try {
                api.start(payload.workOrderId)
                SyncOpResult.Done
            } catch (e: HttpException) {
                when {
                    // Already started (another device, or an earlier attempt that did reach the
                    // server): nothing left to do. Any other conflict means it can't be started.
                    e.code() == 409 && isInProgress(payload.workOrderId) -> SyncOpResult.Done
                    e.code() in 400..499 -> SyncOpResult.Failed(e.errorCodeOrNull() ?: "HTTP ${e.code()}")
                    else -> SyncOpResult.RetryLater
                }
            } catch (e: IOException) {
                SyncOpResult.RetryLater
            }
        }

        private suspend fun isInProgress(workOrderId: String): Boolean =
            runCatching { api.detail(workOrderId).woStatus == "in_progress" }.getOrDefault(false)
    }
