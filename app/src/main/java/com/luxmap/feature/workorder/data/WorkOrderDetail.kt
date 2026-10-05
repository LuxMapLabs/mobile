package com.luxmap.feature.workorder.data

import com.luxmap.feature.workorder.data.dto.WorkOrderDetailDto
import com.luxmap.feature.workorder.data.dto.WorkOrderFaultDetailDto

data class WorkOrderDetail(
    val workOrderId: String,
    val title: String,
    val woStatus: String,
    val taskKind: String,
    val dueDate: String?,
    val scheduledDate: String?,
    val note: String?,
    val allowedActions: List<String>,
    val faults: List<WorkOrderFaultDetail>,
)

data class WorkOrderFaultDetail(
    val faultId: String,
    val lat: Double,
    val lng: Double,
    val faultType: String,
    val faultStatus: String,
    val severity: String,
    val inspectionOutcome: String?,
)

fun WorkOrderDetailDto.toWorkOrderDetail(): WorkOrderDetail =
    WorkOrderDetail(
        workOrderId = workOrderId,
        title = title,
        woStatus = woStatus,
        taskKind = taskKind,
        dueDate = dueDate,
        scheduledDate = scheduledDate,
        note = note,
        allowedActions = allowedActions,
        faults = faults.map { it.toWorkOrderFaultDetail() },
    )

private fun WorkOrderFaultDetailDto.toWorkOrderFaultDetail() =
    WorkOrderFaultDetail(
        faultId = faultId,
        lat = location.lat,
        lng = location.lng,
        faultType = faultType,
        faultStatus = faultStatus,
        severity = severity,
        inspectionOutcome = inspectionOutcome,
    )
