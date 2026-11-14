package com.luxmap.feature.workorder.data

import com.luxmap.feature.workorder.data.dto.WorkOrderDetailDto
import com.luxmap.feature.workorder.data.dto.WorkOrderFaultDetailDto
import java.time.Instant
import java.time.OffsetDateTime

data class WorkOrderDetail(
    val workOrderId: String,
    val title: String,
    val woStatus: String,
    val taskKind: String,
    val dueDate: String?,
    val scheduledDate: String?,
    val note: String?,
    val reviewNote: String? = null,
    val reportNote: String? = null,
    val materialsUsed: String? = null,
    val allowedActions: List<String>,
    val cancellationRequestStatus: String = "none",
    val cancellationRequestNote: String? = null,
    val cancellationRejectNote: String? = null,
    val faults: List<WorkOrderFaultDetail>,
    val poleIds: List<String> = emptyList(),
    val attachments: List<WorkOrderAttachment> = emptyList(),
    val poleLocations: List<WorkOrderPoleLocation> = emptyList(),
)

data class WorkOrderAttachment(
    val attachmentId: String,
    val fileName: String,
    val byteCount: Long,
    val uploadedAt: Instant,
    val downloadUrl: String,
)

data class WorkOrderPoleLocation(
    val poleId: String,
    val lat: Double,
    val lng: Double,
    val accuracyM: Double,
    val capturedAt: Instant,
)

data class WorkOrderFaultDetail(
    val faultId: String,
    val poleId: String?,
    val lat: Double,
    val lng: Double,
    val faultType: String,
    val faultStatus: String,
    val severity: String,
    val inspectionOutcome: String?,
)

fun WorkOrderFaultDetail.hasLocation(): Boolean = lat != 0.0 || lng != 0.0

fun WorkOrderDetailDto.toWorkOrderDetail(): WorkOrderDetail =
    WorkOrderDetail(
        workOrderId = workOrderId,
        title = title,
        woStatus = woStatus,
        taskKind = taskKind,
        dueDate = dueDate,
        scheduledDate = scheduledDate,
        note = note,
        reviewNote = reviewNote,
        reportNote = reportNote,
        materialsUsed = materialsUsed,
        allowedActions = allowedActions,
        cancellationRequestStatus = cancellationRequestStatus,
        cancellationRequestNote = cancellationRequestNote,
        cancellationRejectNote = cancellationRejectNote,
        faults = faults.map { it.toWorkOrderFaultDetail() },
        poleIds = poleIds,
        attachments =
            attachments.map {
                WorkOrderAttachment(
                    it.attachmentId,
                    it.fileName,
                    it.byteCount,
                    OffsetDateTime.parse(it.uploadedAt).toInstant(),
                    it.downloadUrl,
                )
            },
        poleLocations =
            poleLocations.map {
                WorkOrderPoleLocation(
                    it.poleId,
                    it.lat,
                    it.lng,
                    it.accuracyM,
                    OffsetDateTime.parse(it.capturedAt).toInstant(),
                )
            },
    )

private fun WorkOrderFaultDetailDto.toWorkOrderFaultDetail() =
    WorkOrderFaultDetail(
        faultId = faultId,
        poleId = poleId,
        lat = location.lat,
        lng = location.lng,
        faultType = faultType,
        faultStatus = faultStatus,
        severity = severity,
        inspectionOutcome = inspectionOutcome,
    )
