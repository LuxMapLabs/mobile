package com.luxmap.feature.survey.data

import com.luxmap.feature.survey.data.dao.PolePhotoDao
import com.luxmap.feature.survey.data.entity.LocalPolePhotoEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakePolePhotoRepository
    @Inject
    constructor(
        private val dao: PolePhotoDao,
    ) : PolePhotoRepository {
        override fun observePhotos(workOrderId: String): Flow<List<LocalPolePhotoEntity>> =
            dao.observeByWorkOrder(workOrderId)

        override fun observeUploadStatuses(photoIds: List<String>): Flow<Map<String, String>> = flowOf(emptyMap())

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
            return entity
        }

        override suspend fun fetchResult(
            workOrderId: String,
            serverPhotoId: String,
        ): Result<LocalPolePhotoEntity> =
            runCatching {
                dao.updateAiResult(
                    photoId = serverPhotoId,
                    aiStatus = "available",
                    cvState = "on",
                    confidence = 0.92,
                    modelVersion = "fake-model-v0",
                    analyzedAt = Instant.now(),
                    decision = null,
                    decisionNote = null,
                    decidedBy = null,
                    decidedAt = null,
                )
                dao.byId(serverPhotoId) ?: error("Local pole photo row missing after AI result update")
            }
    }
