package com.luxmap.feature.home.data

// F02 spec (mục B1, C3): 4 thẻ số liệu ở đầu màn — lệnh được giao / đang xử lý / quá hạn (từ
// work_order.status) và đợt khảo sát đã lên kế hoạch (từ survey_sweep.status = planned).
// plannedSweepCount is null, not 0 - the backend has no survey-sweep API yet (see
// docs/Backend_API_Requirements_For_Mobile.docx), so "no data source" must not look the same as
// "confirmed zero planned sweeps". The UI hides that one KPI chip when this is null.
data class HomeMetrics(
    val assignedCount: Int,
    val inProgressCount: Int,
    val overdueCount: Int,
    val plannedSweepCount: Int?,
)

// One row of F02's summary list, and (once F08 is built) the same shape that list reuses -
// trimmed to exactly what GET /work-orders (list) returns (WorkOrderItem in the backend), not
// the richer shape the Design System v2.0 section 6.4 anatomy describes (address, fault type,
// distance) - those fields only exist on GET /work-orders/{id}, not the list. Known deviation,
// tracked in docs/contract-drift.md.
// dueDate/status are raw strings (ISO date / wo_status wire value), not parsed here - same
// reasoning as PoleDetail: parse at the UI layer when displaying, not in the domain model.
// taskKind is read but intentionally unused by any UI/logic here (see CLAUDE.md: the
// Inspection/Repair split is not yet confirmed with WP2/WP5 for mobile use).
data class WorkOrderSummaryItem(
    val workOrderId: String,
    val woStatus: String,
    val dueDate: String?,
    val priorityScore: Double?,
)

// One cluster - clusterLabel is the raw commune_id (e.g. "COM-001") for now, not a friendly
// commune name: the backend has no endpoint to resolve that name yet. items keep the order the
// backend returned (sorted server-side would be BE-25; not relied on here).
data class WorkOrderCluster(
    val clusterLabel: String,
    val items: List<WorkOrderSummaryItem>,
)

data class HomeData(
    val metrics: HomeMetrics,
    val clusters: List<WorkOrderCluster>,
)
