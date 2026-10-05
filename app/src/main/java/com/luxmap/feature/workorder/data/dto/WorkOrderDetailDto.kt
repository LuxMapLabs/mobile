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
    @SerialName("allowed_actions") val allowedActions: List<String> = emptyList(),
    val faults: List<WorkOrderFaultDetailDto> = emptyList(),
)

@Serializable
data class WorkOrderFaultDetailDto(
    @SerialName("fault_id") val faultId: String,
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
