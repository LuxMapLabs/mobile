package com.luxmap.feature.workorder.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Checked directly against luxmap_backend/src/LuxMap.Modules.WorkOrders/WorkOrderResponses.cs
// (WorkOrderItem) - field names are NOT guessed. Used by GET /api/v1/work-orders (list item
// shape - no location, no fault detail; those only come back from GET /work-orders/{id}).
@Serializable
data class WorkOrderItemDto(
    @SerialName("work_order_id") val workOrderId: String,
    val title: String,
    @SerialName("commune_id") val communeId: String,
    // "inspection" | "repair" | "survey" - checked directly against TaskKind enum in
    // luxmap_backend/src/LuxMap.Modules.WorkOrders/Entities/WorkOrder.cs.
    @SerialName("task_kind") val taskKind: String,
    @SerialName("segment_id") val segmentId: String? = null,
    @SerialName("cluster_id") val clusterId: String? = null,
    // The work order this one followed up; null for the first step of a chain.
    @SerialName("parent_work_order_id") val parentWorkOrderId: String? = null,
    // The chain's first work order id - the same value for every step of the chain.
    @SerialName("case_id") val caseId: String,
    @SerialName("fault_ids") val faultIds: List<String>,
    @SerialName("wo_status") val woStatus: String,
    @SerialName("assigned_to") val assignedTo: String? = null,
    @SerialName("priority_score") val priorityScore: Double? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("due_date") val dueDate: String? = null,
    @SerialName("scheduled_date") val scheduledDate: String? = null,
)
