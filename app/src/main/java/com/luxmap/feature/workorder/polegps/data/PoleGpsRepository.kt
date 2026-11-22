package com.luxmap.feature.workorder.polegps.data

import androidx.room.withTransaction
import com.luxmap.core.database.AppDatabase
import com.luxmap.core.network.AssetsApi
import com.luxmap.core.sync.SyncQueueEntity
import com.luxmap.core.sync.SyncQueueManager
import com.luxmap.feature.survey.data.dao.PoleListCacheDao
import com.luxmap.feature.survey.data.toDomain
import com.luxmap.feature.workorder.data.dao.WorkOrderCompletionDao
import com.luxmap.feature.workorder.data.dto.PoleLocationRequestDto
import com.luxmap.feature.workorder.data.entity.LocalWorkOrderCompletionEntity
import com.luxmap.feature.workorder.data.entity.LocalWorkOrderEvidenceEntity
import com.luxmap.feature.workorder.data.sync.CompletionSyncPayload
import com.luxmap.feature.workorder.data.sync.EvidenceSyncPayload
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

enum class MeasurementSource { DEVICE, SERVER }

enum class AttachmentStatus { PENDING, DOWNLOADING, READY, FAILED }

data class PoleMeasurement(
    val poleId: String,
    val lat: Double,
    val lng: Double,
    val accuracyM: Double,
    val capturedAt: Instant,
    val source: MeasurementSource,
    val photoFilePath: String? = null,
    val corridor: CorridorCheck = CorridorCheck.Unknown,
)

data class PoleGpsAttachment(
    val attachmentId: String,
    val fileName: String,
    val byteCount: Long,
    val status: AttachmentStatus,
    val localPath: String?,
)

data class PoleReference(
    val poleId: String,
    val position: Int?,
    val lat: Double?,
    val lng: Double?,
    val identification: com.luxmap.feature.survey.data.PoleListItem? = null,
)

private fun LocalPoleGpsMeasurementEntity.toDomain(geomWkt: String?) =
    PoleMeasurement(
        poleId = poleId,
        lat = lat,
        lng = lng,
        accuracyM = accuracyM,
        capturedAt = capturedAt,
        source = if (source == MeasurementSourceColumn.DEVICE) MeasurementSource.DEVICE else MeasurementSource.SERVER,
        photoFilePath = photoFilePath,
        corridor = corridorCheck(geomWkt, lat, lng),
    )

private fun LocalWorkOrderAttachmentEntity.toDomain() =
    PoleGpsAttachment(
        attachmentId = attachmentId,
        fileName = fileName,
        byteCount = byteCount,
        status =
            when (status) {
                AttachmentStatusColumn.READY -> AttachmentStatus.READY
                AttachmentStatusColumn.DOWNLOADING -> AttachmentStatus.DOWNLOADING
                AttachmentStatusColumn.FAILED -> AttachmentStatus.FAILED
                else -> AttachmentStatus.PENDING
            },
        localPath = localPath,
    )

@Singleton
class PoleGpsRepository
    @Inject
    constructor(
        private val database: AppDatabase,
        private val dao: PoleGpsDao,
        private val downloader: AttachmentDownloader,
        private val scheduler: AttachmentDownloadScheduler,
        private val detailSync: PoleGpsDetailSync,
        private val poleCache: PoleListCacheDao,
        private val completionDao: WorkOrderCompletionDao,
        private val syncQueueManager: SyncQueueManager,
        private val assetsApi: AssetsApi,
    ) {
        fun observeMeasurements(workOrderId: String): Flow<List<PoleMeasurement>> =
            combine(dao.observeMeasurements(workOrderId), dao.observeRoute(workOrderId)) { rows, route ->
                rows.map { it.toDomain(route?.geomWkt) }
            }

        suspend fun allMeasurements(workOrderId: String): List<PoleMeasurement> {
            val geomWkt = dao.route(workOrderId)?.geomWkt
            return dao.measurements(workOrderId).map { it.toDomain(geomWkt) }
        }

        suspend fun refreshRoute(workOrderId: String) {
            val segmentId = detailSync.cached(workOrderId)?.segmentId ?: return
            val segment =
                try {
                    assetsApi.segment(segmentId)
                } catch (_: IOException) {
                    return
                } catch (_: HttpException) {
                    return
                } catch (_: SerializationException) {
                    return
                }
            dao.upsertRoute(LocalPoleGpsRouteEntity(workOrderId, segmentId, segment.geomWkt, Instant.now()))
        }

        suspend fun routeGeometry(workOrderId: String): String? = dao.route(workOrderId)?.geomWkt

        suspend fun saveCapturedPhoto(
            workOrderId: String,
            poleId: String,
            filePath: String,
            lat: Double,
            lng: Double,
            accuracyM: Double,
            capturedAt: Instant,
        ) {
            dao.upsertDeviceMeasurement(
                LocalPoleGpsMeasurementEntity(
                    workOrderId = workOrderId,
                    poleId = poleId,
                    lat = lat,
                    lng = lng,
                    accuracyM = accuracyM,
                    capturedAt = capturedAt,
                    source = MeasurementSourceColumn.DEVICE,
                    photoFilePath = filePath,
                    evidenceUploadStatus = "pending",
                ),
            )
        }

        suspend fun stageCompletion(completion: LocalWorkOrderCompletionEntity): Boolean {
            val staged =
                database.withTransaction {
                    val previous = completionDao.completionByWorkOrderId(completion.workOrderId)
                    if (previous != null && previous.submitStatus != "draft") return@withTransaction false
                    val payload =
                        snapshotCompletion(
                            completion.workOrderId,
                            resetRetryableUploads = previous?.submitStatus == "draft",
                        )
                    completionDao.insertOrReplaceCompletion(completion)
                    completionDao.insertSubmissionQueueRows(
                        listOf(queueRow(completion.clientOpId, "complete_work_order", Json.encodeToString(payload))),
                    )
                    true
                }
            if (staged) syncQueueManager.triggerSync()
            return staged
        }

        suspend fun reopenFailedCompletion(workOrderId: String): Boolean {
            if (detailSync.cached(workOrderId)?.taskKind != "pole_gps_update") return false
            return database.withTransaction {
                val completion =
                    completionDao.completionByWorkOrderId(workOrderId)
                        ?.takeIf { it.submitStatus == "failed" } ?: return@withTransaction false
                val operation =
                    completionDao.submissionOperation(completion.clientOpId, "complete_work_order")
                        ?.takeIf { it.status == "failed" } ?: return@withTransaction false
                // Keep all measurements and photos: the engineer decides which pole needs to be retaken.
                completionDao.deleteFailedOperation(operation.id)
                completionDao.updateCompletionSubmitStatus(workOrderId, "draft")
                true
            }
        }

        // Old GPS submissions did not carry a snapshot or photo dependencies. Upgrade them durably before sending.
        suspend fun legacyCompletionPayload(completion: LocalWorkOrderCompletionEntity): CompletionSyncPayload? {
            val order = detailSync.cached(completion.workOrderId) ?: return null
            if (order.taskKind != "pole_gps_update") return null
            return database.withTransaction {
                val payload = snapshotCompletion(completion.workOrderId)
                completionDao.updateCompletionPayload(completion.clientOpId, Json.encodeToString(payload))
                payload
            }
        }

        private suspend fun snapshotCompletion(
            workOrderId: String,
            resetRetryableUploads: Boolean = false,
        ): CompletionSyncPayload {
            val order = checkNotNull(detailSync.cached(workOrderId)) { "Chưa có chi tiết lệnh GPS trên máy." }
            check(order.taskKind == "pole_gps_update")
            val measurements = dao.measurements(workOrderId).filter { it.poleId in order.poleIds }
            check(measurements.any { it.source == MeasurementSourceColumn.DEVICE }) {
                "Cần đo ít nhất một cột trước khi nộp báo cáo."
            }
            measurements.forEach { measurement ->
                check(
                    measurement.lat.isFinite() && measurement.lat in -90.0..90.0 &&
                        measurement.lng.isFinite() && measurement.lng in -180.0..180.0 &&
                        measurement.accuracyM.isFinite() && measurement.accuracyM >= 0,
                ) { "Tọa độ cột không hợp lệ. Hãy đo lại." }
            }
            val evidence = completionDao.evidenceForCompletion(workOrderId, "observation")
            val operations = mutableListOf<SyncQueueEntity>()
            val ids =
                measurements.filter { it.source == MeasurementSourceColumn.DEVICE }.map { measurement ->
                    val matching =
                        evidence.firstOrNull {
                            it.poleId == measurement.poleId && it.capturedAt == measurement.capturedAt &&
                                it.lat == measurement.lat && it.lng == measurement.lng &&
                                (measurement.photoFilePath == null || it.filePath == measurement.photoFilePath)
                        }
                    val photo =
                        matching ?: run {
                            val path = measurement.photoFilePath
                            check(path != null && java.io.File(path).isFile) {
                                "Thiếu ảnh cho cột ${measurement.poleId}. Hãy chụp và đo lại."
                            }
                            LocalWorkOrderEvidenceEntity(
                                clientOpId = UUID.randomUUID().toString(),
                                workOrderId = workOrderId,
                                kind = "observation",
                                filePath = path,
                                capturedAt = measurement.capturedAt,
                                lat = measurement.lat,
                                lng = measurement.lng,
                                uploadStatus = "pending",
                                poleId = measurement.poleId,
                            ).also { completionDao.insertEvidence(it) }
                        }
                    if (resetRetryableUploads) completionDao.resetRetryablePhoto(photo.clientOpId, Instant.now())
                    if (completionDao.completionEvidenceStatus(workOrderId, photo.clientOpId) == null) {
                        check(java.io.File(photo.filePath).isFile) { "Ảnh đã mất. Hãy chụp và đo lại cột." }
                        operations +=
                            queueRow(
                                photo.clientOpId,
                                "upload_work_order_evidence",
                                Json.encodeToString(EvidenceSyncPayload(photo.clientOpId)),
                            )
                    }
                    dao.linkEvidence(workOrderId, measurement.poleId, photo.clientOpId)
                    photo.clientOpId
                }
            completionDao.insertSubmissionQueueRows(operations)
            return CompletionSyncPayload(
                workOrderId,
                evidenceClientOpIds = ids,
                poleLocations =
                    measurements.map {
                        PoleLocationRequestDto(it.poleId, it.lat, it.lng, it.accuracyM, it.capturedAt.toString())
                    },
            )
        }

        fun observeAttachments(workOrderId: String): Flow<List<PoleGpsAttachment>> =
            dao.observeAttachments(workOrderId).map { rows -> rows.map { it.toDomain() } }

        suspend fun attachment(
            workOrderId: String,
            attachmentId: String,
        ): PoleGpsAttachment? = dao.attachments(workOrderId).firstOrNull { it.attachmentId == attachmentId }?.toDomain()

        suspend fun retryAttachment(
            workOrderId: String,
            attachmentId: String,
        ) {
            downloader.retry(workOrderId, attachmentId)
            scheduler.schedule(workOrderId)
        }

        suspend fun poleReference(
            workOrderId: String,
            poleId: String,
        ): PoleReference? = poleReferences(workOrderId).firstOrNull { it.poleId == poleId }

        suspend fun poleReferences(workOrderId: String): List<PoleReference> {
            val order = detailSync.cached(workOrderId) ?: return emptyList()
            val rows = poleCache.byWorkOrder(workOrderId).associateBy { it.poleId }
            return order.poleIds.map { poleId ->
                val row = rows[poleId]
                PoleReference(
                    poleId,
                    row?.position,
                    row?.lat,
                    row?.lng,
                    identification = row?.toDomain(),
                )
            }
        }

        suspend fun completionPoleLocations(workOrderId: String): List<PoleLocationRequestDto>? {
            val order = detailSync.cached(workOrderId) ?: return null
            val now = Instant.now()
            return allMeasurements(workOrderId)
                .filter { it.poleId in order.poleIds }
                .map {
                    val capturedAt = if (it.capturedAt.isAfter(now)) now else it.capturedAt
                    PoleLocationRequestDto(it.poleId, it.lat, it.lng, it.accuracyM, capturedAt.toString())
                }
        }
    }

private fun queueRow(
    clientOpId: String,
    opType: String,
    payload: String,
): SyncQueueEntity {
    val now = Instant.now()
    return SyncQueueEntity(
        clientOpId = clientOpId,
        opType = opType,
        payloadJson = payload,
        dependsOnClientOpId = null,
        status = "queued",
        createdAt = now,
        updatedAt = now,
    )
}
