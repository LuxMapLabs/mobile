package com.luxmap.feature.survey.data

import com.luxmap.core.network.WorkOrdersApi
import com.luxmap.core.sync.SyncQueueDao
import com.luxmap.core.sync.SyncQueueManager
import com.luxmap.feature.survey.data.dao.PolePhotoDao
import com.luxmap.feature.survey.data.entity.LocalPolePhotoEntity
import com.luxmap.feature.survey.data.sync.PolePhotoSyncPayload
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RealPolePhotoRepository
    @Inject
    constructor(
        private val api: WorkOrdersApi,
        private val dao: PolePhotoDao,
        private val syncQueueManager: SyncQueueManager,
        private val syncQueueDao: SyncQueueDao,
    ) : PolePhotoRepository {
        override fun observePhotos(workOrderId: String): Flow<List<LocalPolePhotoEntity>> =
            dao.observeByWorkOrder(workOrderId)

        override fun observeUploadStatuses(photoIds: List<String>): Flow<Map<String, String>> =
            if (photoIds.isEmpty()) {
                flowOf(emptyMap())
            } else {
                syncQueueDao.observeStatusesFor(photoIds).map { rows -> rows.associate { it.clientOpId to it.status } }
            }

        override suspend fun capture(
            workOrderId: String,
            poleId: String,
            filePath: String,
            lat: Double,
            lng: Double,
        ): LocalPolePhotoEntity {
            val entity =
                LocalPolePhotoEntity(
                    photoId = UUID.randomUUID().toString(),
                    workOrderId = workOrderId,
                    poleId = poleId,
                    filePath = filePath,
                    capturedAt = Instant.now(),
                    lat = lat,
                    lng = lng,
                )
            dao.insert(entity)
            syncQueueManager.enqueue(
                opType = "upload_pole_photo",
                payloadJson = json.encodeToString(PolePhotoSyncPayload(entity.photoId)),
                clientOpId = entity.photoId,
            )
            return entity
        }

        override suspend fun fetchResult(
            workOrderId: String,
            serverPhotoId: String,
        ): Result<LocalPolePhotoEntity> =
            runCatching {
                val item = api.polePhotoDetail(workOrderId, serverPhotoId)
                val localPhotoId =
                    dao.byServerPhotoId(serverPhotoId)?.photoId
                        ?: error("Local pole photo row missing for server photo $serverPhotoId")
                dao.updateAiResult(
                    photoId = localPhotoId,
                    aiStatus = item.aiStatus,
                    cvState = item.cvState,
                    confidence = item.confidence,
                    modelVersion = item.modelVersion,
                    analyzedAt = item.analyzedAt?.let(Instant::parse),
                    decision = item.decision,
                    decisionNote = item.decisionNote,
                    decidedBy = item.decidedBy,
                    decidedAt = item.decidedAt?.let(Instant::parse),
                )
                dao.byId(localPhotoId) ?: error("Local pole photo row missing after AI result update")
            }

        override suspend fun cachedResult(serverPhotoId: String): LocalPolePhotoEntity? =
            dao.byServerPhotoId(serverPhotoId)

        private companion object {
            val json = Json { ignoreUnknownKeys = true }
        }
    }
