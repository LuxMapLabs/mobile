package com.luxmap.feature.workorder.polegps.data

import com.luxmap.feature.workorder.data.dto.WorkOrderDetailDto
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.OffsetDateTime
import javax.inject.Inject
import javax.inject.Singleton

private const val POLE_GPS_UPDATE_WIRE = "pole_gps_update"
private val ACTIVE_STATUSES = setOf("assigned", "in_progress")

@Singleton
class PoleGpsDetailSync
    @Inject
    constructor(
        private val dao: PoleGpsDao,
        private val scheduler: AttachmentDownloadScheduler,
        private val files: PoleGpsFiles,
    ) {
        private val json =
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            }

        suspend fun store(dto: WorkOrderDetailDto) {
            if (dto.taskKind != POLE_GPS_UPDATE_WIRE) return
            dao.upsertOrder(LocalPoleGpsOrderEntity(dto.workOrderId, json.encodeToString(dto), Instant.now()))
            if (dto.woStatus !in ACTIVE_STATUSES) {
                files.deleteOrder(dto.workOrderId)
                dao.deleteAttachments(dto.workOrderId)
                return
            }
            dao.replaceAttachments(
                dto.workOrderId,
                dto.attachments.map {
                    LocalWorkOrderAttachmentEntity(
                        dto.workOrderId,
                        it.attachmentId,
                        it.fileName,
                        it.byteCount,
                        it.downloadUrl,
                        AttachmentStatusColumn.PENDING,
                        null,
                    )
                },
            )
            dao.insertServerMeasurements(
                dto.poleLocations.map {
                    LocalPoleGpsMeasurementEntity(
                        dto.workOrderId,
                        it.poleId,
                        it.lat,
                        it.lng,
                        it.accuracyM,
                        OffsetDateTime.parse(it.capturedAt).toInstant(),
                        MeasurementSourceColumn.SERVER,
                    )
                },
            )
            scheduler.schedule(dto.workOrderId)
        }

        suspend fun cached(workOrderId: String): WorkOrderDetailDto? =
            dao.order(workOrderId)?.let { json.decodeFromString<WorkOrderDetailDto>(it.detailJson) }
    }
