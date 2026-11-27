package com.luxmap.feature.workorder.data

import com.luxmap.core.network.WorkOrdersApi
import com.luxmap.core.offline.LocalOfflineSnapshotEntity
import com.luxmap.core.offline.OfflineSnapshotDao
import com.luxmap.core.sync.SyncQueueManager
import com.luxmap.feature.workorder.data.dto.RequestCancellationRequestDto
import com.luxmap.feature.workorder.data.dto.WorkOrderDetailDto
import com.luxmap.feature.workorder.data.sync.StartWorkOrderSyncPayload
import com.luxmap.feature.workorder.polegps.data.PoleGpsDetailSync
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RealWorkOrderDetailRepository
    @Inject
    constructor(
        private val workOrdersApi: WorkOrdersApi,
        private val poleGpsDetailSync: PoleGpsDetailSync,
        private val snapshots: OfflineSnapshotDao,
        private val syncQueueManager: SyncQueueManager,
    ) : WorkOrderDetailRepository {
        override fun observeWorkOrderDetail(workOrderId: String): Flow<WorkOrderDetail?> =
            flow {
                val detail =
                    try {
                        val dto = workOrdersApi.detail(workOrderId)
                        poleGpsDetailSync.store(dto)
                        save(dto)
                        withPendingStart(dto)
                    } catch (e: HttpException) {
                        if (e.code() == 404) null else throw e
                    } catch (e: IOException) {
                        // Offline-first: the last copy seen on this device, so a weak signal does not
                        // stop the engineer from opening an order they already looked at.
                        cached(workOrderId)?.let { withPendingStart(it) } ?: throw e
                    }
                emit(detail)
            }

        override suspend fun start(workOrderId: String): Result<WorkOrderDetail> =
            runCatching {
                val dto = workOrdersApi.start(workOrderId)
                save(dto)
                dto.toWorkOrderDetail()
            }.recoverCatching { e ->
                // No signal: start on this device and send the start when the network returns.
                // Only possible for an order already opened once (its detail is on the device).
                if (e !is IOException) throw e
                val cached = cached(workOrderId) ?: throw e
                if (syncQueueManager.pendingStartOf(workOrderId) == null) {
                    syncQueueManager.enqueue(
                        opType = SyncQueueManager.START_WORK_ORDER_OP,
                        payloadJson = json.encodeToString(StartWorkOrderSyncPayload(workOrderId)),
                        clientOpId = SyncQueueManager.startOpId(workOrderId),
                    )
                }
                cached.startedOffline().toWorkOrderDetail().copy(startPendingSync = true)
            }

        override suspend fun requestCancellation(
            workOrderId: String,
            reason: String,
        ): Result<WorkOrderDetail> =
            runCatching {
                val body = RequestCancellationRequestDto(reason)
                workOrdersApi.requestCancellation(workOrderId, body).toWorkOrderDetail()
            }

        private suspend fun save(dto: WorkOrderDetailDto) =
            snapshots.put(
                LocalOfflineSnapshotEntity(snapshotKey(dto.workOrderId), json.encodeToString(dto), Instant.now()),
            )

        private suspend fun cached(workOrderId: String): WorkOrderDetailDto? =
            poleGpsDetailSync.cached(workOrderId)
                ?: snapshots.get(snapshotKey(workOrderId))?.let { json.decodeFromString<WorkOrderDetailDto>(it.json) }

        // Until the queued start reaches the server, the server (or the saved copy) still says
        // "assigned": show the order as started so the Start button does not come back.
        private suspend fun withPendingStart(dto: WorkOrderDetailDto): WorkOrderDetail =
            if (dto.woStatus == "assigned" && syncQueueManager.pendingStartOf(dto.workOrderId) != null) {
                dto.startedOffline().toWorkOrderDetail().copy(startPendingSync = true)
            } else {
                dto.toWorkOrderDetail()
            }

        private fun WorkOrderDetailDto.startedOffline() =
            copy(
                woStatus = "in_progress",
                allowedActions = (allowedActions - setOf("start", "request_cancellation") + "complete").distinct(),
            )

        private companion object {
            val json =
                Json {
                    ignoreUnknownKeys = true
                    encodeDefaults = true
                }

            fun snapshotKey(workOrderId: String) = "work_order:$workOrderId"
        }
    }
