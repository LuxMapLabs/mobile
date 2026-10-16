package com.luxmap.feature.survey.data

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

        private companion object {
            val json = Json { ignoreUnknownKeys = true }
        }
    }
