package com.luxmap.feature.workorder.polegps.data

import androidx.room.withTransaction
import com.luxmap.core.database.AppDatabase
import com.luxmap.core.sync.SyncQueueManager
import com.luxmap.feature.survey.data.dao.PoleListCacheDao
import com.luxmap.feature.workorder.data.dao.WorkOrderCompletionDao
import com.luxmap.feature.workorder.data.dto.PoleLocationRequestDto
import com.luxmap.feature.workorder.data.entity.LocalWorkOrderEvidenceEntity
import com.luxmap.feature.workorder.data.sync.EvidenceSyncPayload
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
)

private fun LocalPoleGpsMeasurementEntity.toDomain() =
    PoleMeasurement(
        poleId = poleId,
        lat = lat,
        lng = lng,
        accuracyM = accuracyM,
        capturedAt = capturedAt,
        source = if (source == MeasurementSourceColumn.DEVICE) MeasurementSource.DEVICE else MeasurementSource.SERVER,
        photoFilePath = photoFilePath,
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
    ) {
        fun observeMeasurements(workOrderId: String): Flow<List<PoleMeasurement>> =
            dao.observeMeasurements(workOrderId).map { rows -> rows.map { it.toDomain() } }

        suspend fun allMeasurements(workOrderId: String): List<PoleMeasurement> =
            dao.measurements(workOrderId).map { it.toDomain() }

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

        suspend fun enqueuePendingPhotoUploads(workOrderId: String) {
            dao.pendingPhotoMeasurements(workOrderId).forEach { measurement ->
                database.withTransaction {
                    val clientOpId = UUID.randomUUID().toString()
                    completionDao.insertEvidence(
                        LocalWorkOrderEvidenceEntity(
                            clientOpId = clientOpId,
                            workOrderId = workOrderId,
                            kind = "observation",
                            filePath = measurement.photoFilePath!!,
                            capturedAt = measurement.capturedAt,
                            lat = measurement.lat,
                            lng = measurement.lng,
                            uploadStatus = "pending",
                            poleId = measurement.poleId,
                        ),
                    )
                    syncQueueManager.enqueue(
                        opType = "upload_work_order_evidence",
                        payloadJson = Json.encodeToString(EvidenceSyncPayload(clientOpId)),
                        clientOpId = clientOpId,
                    )
                    dao.updateEvidenceUploadStatus(workOrderId, measurement.poleId, "queued")
                }
            }
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
                PoleReference(poleId, row?.position, row?.lat, row?.lng)
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
