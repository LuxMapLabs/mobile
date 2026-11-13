package com.luxmap.feature.workorder.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WorkOrderDetailDto(
    @SerialName("work_order_id") val workOrderId: String,
    val title: String,
    @SerialName("task_kind") val taskKind: String,
    @SerialName("wo_status") val woStatus: String,
    @SerialName("due_date") val dueDate: String? = null,
    @SerialName("scheduled_date") val scheduledDate: String? = null,
    val note: String? = null,
    @SerialName("review_note") val reviewNote: String? = null,
    @SerialName("report_note") val reportNote: String? = null,
    @SerialName("materials_used") val materialsUsed: String? = null,
    @SerialName("allowed_actions") val allowedActions: List<String> = emptyList(),
    @SerialName("cancellation_request_status") val cancellationRequestStatus: String = "none",
    @SerialName("cancellation_request_note") val cancellationRequestNote: String? = null,
    @SerialName("cancellation_requested_at") val cancellationRequestedAt: String? = null,
    @SerialName("cancellation_reject_note") val cancellationRejectNote: String? = null,
    @SerialName("cancellation_rejected_at") val cancellationRejectedAt: String? = null,
    val faults: List<WorkOrderFaultDetailDto> = emptyList(),
    @SerialName("pole_ids") val poleIds: List<String> = emptyList(),
    val attachments: List<WorkOrderAttachmentDto> = emptyList(),
    @SerialName("pole_locations") val poleLocations: List<WorkOrderPoleLocationDto> = emptyList(),
)

@Serializable
data class WorkOrderFaultDetailDto(
    @SerialName("fault_id") val faultId: String,
    @SerialName("pole_id") val poleId: String? = null,
    val location: WorkOrderFaultLocationDto,
    @SerialName("fault_type") val faultType: String,
    @SerialName("fault_status") val faultStatus: String,
    val severity: String,
    @SerialName("inspection_outcome") val inspectionOutcome: String? = null,
)

@Serializable
data class WorkOrderFaultLocationDto(
    val lat: Double,
    val lng: Double,
)

@Serializable
data class WorkOrderAttachmentDto(
    @SerialName("attachment_id") val attachmentId: String,
    @SerialName("file_name") val fileName: String,
    @SerialName("byte_count") val byteCount: Long,
    @SerialName("uploaded_at") val uploadedAt: String,
    @SerialName("download_url") val downloadUrl: String,
)

@Serializable
data class WorkOrderPoleLocationDto(
    @SerialName("pole_id") val poleId: String,
    val lat: Double,
    val lng: Double,
    @SerialName("accuracy_m") val accuracyM: Double,
    @SerialName("captured_at") val capturedAt: String,
)

@Serializable
data class PoleCoordinateDto(
    val lat: Double,
    val lng: Double,
)
