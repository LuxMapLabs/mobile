package com.luxmap.feature.survey.data

import com.luxmap.core.network.WorkOrdersApi
import com.luxmap.core.sync.SyncQueueDao
import com.luxmap.core.sync.SyncQueueManager
import com.luxmap.feature.survey.data.dao.PolePhotoDao
import com.luxmap.feature.survey.data.dto.PolePhotoItemDto
import com.luxmap.feature.survey.data.entity.LocalPolePhotoEntity
import com.luxmap.feature.survey.data.entity.LocalSurveyPoleSkipEntity
import com.luxmap.feature.survey.data.sync.PolePhotoSyncPayload
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.withLock
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
        private val withdrawal: PolePhotoWithdrawal = PolePhotoWithdrawal(api, dao),
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
            observation: String?,
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
                    engineerObservation = observation,
                    draftFinalCvState = observation?.takeIf { it == "on" || it == "off" },
                )
            dao.insert(entity)
            dao.deleteSkip(workOrderId, poleId)
            syncQueueManager.enqueue(
                opType = "upload_pole_photo",
                payloadJson = json.encodeToString(PolePhotoSyncPayload(entity.photoId)),
                clientOpId = entity.photoId,
            )
            return entity
        }

        override suspend fun captureVideo(
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
                    mediaType = "video",
                )
            dao.insert(entity)
            syncQueueManager.enqueue(
                opType = "upload_pole_video",
                payloadJson = json.encodeToString(PolePhotoSyncPayload(entity.photoId)),
                clientOpId = entity.photoId,
            )
            return entity
        }

        override suspend fun saveDraftReview(
            serverPhotoId: String,
            finalCvState: String,
            reviewNote: String?,
        ) {
            val localPhotoId = dao.byServerPhotoId(serverPhotoId)?.photoId ?: return
            dao.updateDraftReview(localPhotoId, finalCvState, reviewNote)
        }

        override suspend fun enqueuePendingReviews(workOrderId: String): Int {
            val pending =
                dao.observeByWorkOrder(workOrderId).first().distinctBy { it.poleId }
                    .filter { it.draftFinalCvState != null }
            pending.forEach { photo ->
                syncQueueManager.enqueue(
                    opType = "submit_pole_photo_review",
                    payloadJson = json.encodeToString(PolePhotoSyncPayload(photo.photoId)),
                    clientOpId = "${photo.photoId}:review",
                    // An observation picked at capture time can be staged before the photo has
                    // uploaded; the review needs the server photo id, so wait for that upload.
                    dependsOnClientOpId = photo.photoId.takeIf { photo.serverPhotoId == null },
                )
            }
            return pending.size
        }

        override fun observeSkips(workOrderId: String): Flow<List<LocalSurveyPoleSkipEntity>> =
            dao.observeSkips(workOrderId)

        override suspend fun skipPole(
            workOrderId: String,
            poleId: String,
            reason: String,
        ) = dao.upsertSkip(LocalSurveyPoleSkipEntity(workOrderId, poleId, reason, Instant.now()))

        override suspend fun fetchResult(
            workOrderId: String,
            serverPhotoId: String,
        ): Result<LocalPolePhotoEntity> =
            runCatching {
                val item = api.polePhotoDetail(workOrderId, serverPhotoId)
                val localPhotoId =
                    dao.byServerPhotoId(serverPhotoId)?.photoId
                        ?: error("Local pole photo row missing for server photo $serverPhotoId")
                persist(localPhotoId, item)
                dao.byId(localPhotoId) ?: error("Local pole photo row missing after AI result update")
            }

        override suspend fun cachedResult(serverPhotoId: String): LocalPolePhotoEntity? =
            dao.byServerPhotoId(serverPhotoId)

        override suspend fun withdrawResult(
            workOrderId: String,
            serverPhotoId: String,
        ) = withdrawal.mediaMutex.withLock {
            val photo = checkNotNull(dao.byServerPhotoId(serverPhotoId))
            check(photo.workOrderId == workOrderId)
            withdrawal.withdrawPole(workOrderId, photo.poleId, serverPhotoId)
        }

        // Writes a server PolePhotoItemDto (from either the GET detail or PATCH review response -
        // both return the same full item shape) into Room.
        private suspend fun persist(
            localPhotoId: String,
            item: PolePhotoItemDto,
        ) {
            dao.updateAiResult(
                photoId = localPhotoId,
                aiStatus = item.aiStatus,
                cvState = item.cvState,
                confidence = item.confidence,
                modelVersion = item.modelVersion,
                analyzedAt = item.analyzedAt?.let(Instant::parse),
                finalCvState = item.finalCvState,
                reviewNote = item.reviewNote,
                reviewedBy = item.reviewedBy,
                reviewedAt = item.reviewedAt?.let(Instant::parse),
                decision = item.decision,
                decisionNote = item.decisionNote,
                decidedBy = item.decidedBy,
                decidedAt = item.decidedAt?.let(Instant::parse),
                videoUrl = item.video?.videoUrl,
            )
        }

        private companion object {
            val json = Json { ignoreUnknownKeys = true }
        }
    }
