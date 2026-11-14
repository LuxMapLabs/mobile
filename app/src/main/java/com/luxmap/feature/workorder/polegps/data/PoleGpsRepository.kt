package com.luxmap.feature.workorder.polegps.data

import com.luxmap.feature.survey.data.dao.PoleListCacheDao
import com.luxmap.feature.workorder.data.dto.PoleLocationRequestDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
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
        private val dao: PoleGpsDao,
        private val downloader: AttachmentDownloader,
        private val scheduler: AttachmentDownloadScheduler,
        private val detailSync: PoleGpsDetailSync,
        private val poleCache: PoleListCacheDao,
    ) {
        fun observeMeasurements(workOrderId: String): Flow<List<PoleMeasurement>> =
            dao.observeMeasurements(workOrderId).map { rows -> rows.map { it.toDomain() } }

        suspend fun allMeasurements(workOrderId: String): List<PoleMeasurement> =
            dao.measurements(workOrderId).map { it.toDomain() }

        suspend fun saveDeviceMeasurement(
            workOrderId: String,
            poleId: String,
            fix: GpsFix,
        ) {
            dao.upsertDeviceMeasurement(
                LocalPoleGpsMeasurementEntity(
                    workOrderId = workOrderId,
                    poleId = poleId,
                    lat = fix.lat,
                    lng = fix.lng,
                    accuracyM = fix.accuracyM,
                    capturedAt = fix.capturedAt,
                    source = MeasurementSourceColumn.DEVICE,
                ),
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
