package com.luxmap.feature.home.data

import com.luxmap.core.theme.WorkOrderPriority

// F02 spec (mục B1, C3): 4 thẻ số liệu ở đầu màn — lệnh được giao / đang xử lý / quá hạn (từ
// work_order.status) và đợt khảo sát đã lên kế hoạch (từ survey_sweep.status = planned).
data class HomeMetrics(
    val assignedCount: Int,
    val inProgressCount: Int,
    val overdueCount: Int,
    val plannedSweepCount: Int,
)

// Một dòng trong danh sách rút gọn của F02 — cùng field với thẻ WorkOrder ở F08 (mã lệnh, địa
// chỉ rút gọn, loại sự cố, ưu tiên, SLA, khoảng cách), vì F02 chỉ là bản rút gọn của cùng danh sách.
// slaDueAt để String (ISO), không phải Instant — theo đúng cách PoleDetail lưu field JSON, parse
// ở lớp UI bằng DateFormatUtils khi hiển thị, không parse sớm ở domain model.
data class WorkOrderSummaryItem(
    val workOrderId: String,
    val shortAddress: String,
    val faultTypeLabel: String,
    val priority: WorkOrderPriority,
    val slaDueAt: String?,
    val distanceMeters: Double?,
)

// Một cụm địa lý (BE-25 gom ở server) — clusterLabel là tên hiển thị của cụm (VD tên xã/tuyến),
// items đã sắp theo mức ưu tiên trong cụm.
data class WorkOrderCluster(
    val clusterLabel: String,
    val items: List<WorkOrderSummaryItem>,
)

data class HomeData(
    val metrics: HomeMetrics,
    val clusters: List<WorkOrderCluster>,
)
