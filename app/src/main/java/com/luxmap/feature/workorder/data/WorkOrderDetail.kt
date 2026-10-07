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
    val reviewNote: String? = null,
    val reportNote: String? = null,
    val materialsUsed: String? = null,
    val allowedActions: List<String>,
    val faults: List<WorkOrderFaultDetail>,
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
        faults = faults.map { it.toWorkOrderFaultDetail() },
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
