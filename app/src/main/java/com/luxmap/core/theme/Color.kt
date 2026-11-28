package com.luxmap.core.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Assignment
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

// Primitive — giá trị màu gốc (mục 2.1 Design System v2.0)
val Navy700 = Color(0xFF1F3864)
val Navy100 = Color(0xFFD9E2EF)
val Navy800 = Color(0xFF172B4D)
val Blue500 = Color(0xFF3E86C9)
val Green400 = Color(0xFF5FC4B0)
val Success600 = Color(0xFF059669)
val Gray25 = Color(0xFFF8FAFC)
val Gray50 = Color(0xFFF1F5F9)
val Gray200 = Color(0xFFE2E8F0)
val Gray500 = Color(0xFF64748B)
val Slate600 = Color(0xFF475569)
val Gray900 = Color(0xFF0F172A)
val Dark950 = Color(0xFF0D0D0D)
val Dark900 = Color(0xFF121212)
val Dark800 = Color(0xFF1A1A1A)
val Dark700 = Color(0xFF2A2A2A)
val DarkSecondary = Color(0xFFB3B3B3)

val DisabledContainer = Color(0xFFE2E8F0)
val DisabledContent = Color(0xFF475569)
val DisabledContainerDark = Color(0xFF2A2A2A)
val DisabledContentDark = Color(0xFFA0A0A0)
val ErrorDark = Color(0xFFFF9A9A)

val Blue600 = Color(0xFF2D6388)

val White = Color(0xFFFFFFFF)
val Gray150 = Color(0xFFE8EDF3)
val Slate800 = Color(0xFF1E293B)
val Navy200 = Color(0xFFA9BCDD)
val Blue50 = Color(0xFFE4EEF6)
val Blue900 = Color(0xFF173B57)
val Red700 = Color(0xFFB91C1C)
val Red50 = Color(0xFFFEE2E2)
val Red900 = Color(0xFF7F1D1D)

val Navy300 = Color(0xFF9DB7E0)
val Navy750 = Color(0xFF21375C)
val Navy950 = Color(0xFF0E1E38)
val Blue100 = Color(0xFFD2E4F2)
val Blue300 = Color(0xFF8FB8DA)
val Blue800 = Color(0xFF1B3A52)
val Blue950 = Color(0xFF0B2235)
val Night100 = Color(0xFFE6E6E6)
val Night500 = Color(0xFF8C8C8C)
val Night750 = Color(0xFF2E2E2E)
val Night800 = Color(0xFF262626)
val Night850 = Color(0xFF1C1C1C)
val NightError700 = Color(0xFF4A1717)
val NightOnError = Color(0xFF3B0A0A)
val NightOnErrorContainer = Color(0xFFFFD5D5)

// Component — badge tình trạng tài sản (mục 2.3), cặp bg/text theo theme, không đổi
enum class AssetCondition { NORMAL, DIM, OUT, UNKNOWN }

// Nhãn đúng chữ trong Design System v2.0 mục 2.3 — dùng chung cho badge, legend bản đồ...
// không tự đặt tên khác ở từng nơi gọi.
fun AssetCondition.label(): String =
    when (this) {
        AssetCondition.NORMAL -> "Bình thường"
        AssetCondition.DIM -> "Đèn mờ"
        AssetCondition.OUT -> "Hỏng/Tắt"
        AssetCondition.UNKNOWN -> "Chưa xác định"
    }

data class BadgeColors(val background: Color, val text: Color)

@Composable
@ReadOnlyComposable
fun AssetCondition.badgeColors(): BadgeColors = LuxTheme.status.asset.getValue(this)

// Component — badge trạng thái đồng bộ (mục 2.4). queued tách offline/online ở tầng gọi,
// không gộp failed/conflict (xem CLAUDE.md "Nguyên tắc nghiệp vụ cốt lõi")
enum class SyncStatus { QUEUED_OFFLINE, QUEUED_ONLINE, SYNCING, FAILED, CONFLICT, DONE }

@Composable
@ReadOnlyComposable
fun SyncStatus.badgeColors(): BadgeColors = LuxTheme.status.sync.getValue(this)

fun SyncStatus.label(): String =
    when (this) {
        SyncStatus.QUEUED_OFFLINE -> "Chờ mạng"
        SyncStatus.QUEUED_ONLINE -> "Chờ đồng bộ"
        SyncStatus.SYNCING -> "Đang đồng bộ"
        SyncStatus.FAILED -> "Đồng bộ lỗi"
        SyncStatus.CONFLICT -> "Xung đột"
        SyncStatus.DONE -> "Đã đồng bộ"
    }

fun SyncStatus.icon(): ImageVector =
    when (this) {
        SyncStatus.QUEUED_OFFLINE, SyncStatus.QUEUED_ONLINE -> Icons.Rounded.Schedule
        SyncStatus.SYNCING -> Icons.Rounded.Sync
        SyncStatus.FAILED -> Icons.Rounded.ErrorOutline
        SyncStatus.CONFLICT -> Icons.Rounded.Warning
        SyncStatus.DONE -> Icons.Rounded.CloudDone
    }

enum class WorkOrderPriority { LOW, NORMAL, HIGH, URGENT }

// Nhãn đúng chữ trong CLAUDE.md "Badge ưu tiên Work Order" — dùng chung cho mọi nơi hiển thị priority.
fun WorkOrderPriority.label(): String =
    when (this) {
        WorkOrderPriority.LOW -> "Thấp"
        WorkOrderPriority.NORMAL -> "Bình thường"
        WorkOrderPriority.HIGH -> "Cao"
        WorkOrderPriority.URGENT -> "Khẩn"
    }

@Composable
@ReadOnlyComposable
fun WorkOrderPriority.badgeColors(): BadgeColors = LuxTheme.status.priority.getValue(this)

fun severityFromWire(value: String): WorkOrderPriority =
    when (value) {
        "low" -> WorkOrderPriority.LOW
        "high" -> WorkOrderPriority.HIGH
        "critical" -> WorkOrderPriority.URGENT
        else -> WorkOrderPriority.NORMAL
    }

// Component - badge trạng thái work order (wo_status, backend WorkOrderStatus enum). Design
// System v2.0 mục 6.3 chỉ nói "work-order status" là 1 variant của Status Badge, không cho bảng
// màu cụ thể - các cặp bg/text dưới đây dùng lại đúng những giá trị đã có ở badge sync (mục trên),
// không tự bịa màu mới, chỉ ánh xạ theo ngữ nghĩa (mở/mới = xám, đang xử lý = cam, hoàn tất/đã
// duyệt = xanh lá, huỷ = đỏ).
enum class WorkOrderStatus { OPEN, ASSIGNED, IN_PROGRESS, DONE, VERIFIED, CANCELLED }

fun WorkOrderStatus.label(): String =
    when (this) {
        WorkOrderStatus.OPEN -> "Mở"
        WorkOrderStatus.ASSIGNED -> "Chưa bắt đầu"
        WorkOrderStatus.IN_PROGRESS -> "Đang thực hiện"
        WorkOrderStatus.DONE -> "Đã nộp"
        WorkOrderStatus.VERIFIED -> "Đã nghiệm thu"
        WorkOrderStatus.CANCELLED -> "Đã huỷ"
    }

fun WorkOrderStatus.icon(): ImageVector =
    when (this) {
        WorkOrderStatus.OPEN, WorkOrderStatus.ASSIGNED -> Icons.Rounded.Assignment
        WorkOrderStatus.IN_PROGRESS -> Icons.Rounded.Autorenew
        WorkOrderStatus.DONE -> Icons.Rounded.CheckCircle
        WorkOrderStatus.VERIFIED -> Icons.Rounded.Verified
        WorkOrderStatus.CANCELLED -> Icons.Rounded.Cancel
    }

@Composable
@ReadOnlyComposable
fun WorkOrderStatus.badgeColors(): BadgeColors = LuxTheme.status.workOrder.getValue(this)

// wo_status arrives from the backend as a snake_case wire string (WireEnum, e.g. "in_progress")
// - never guess a mapping at the call site, always go through this one function. An unknown
// value (a status added on the backend before the app knows about it) falls back to OPEN's
// neutral gray rather than crashing the screen.
fun workOrderStatusFromWire(value: String): WorkOrderStatus =
    when (value) {
        "open" -> WorkOrderStatus.OPEN
        "assigned" -> WorkOrderStatus.ASSIGNED
        "in_progress" -> WorkOrderStatus.IN_PROGRESS
        "done" -> WorkOrderStatus.DONE
        "verified" -> WorkOrderStatus.VERIFIED
        "cancelled" -> WorkOrderStatus.CANCELLED
        else -> WorkOrderStatus.OPEN
    }

enum class WorkOrderTaskKind { INSPECTION, REPAIR, SURVEY, POLE_GPS_UPDATE }

fun WorkOrderTaskKind.label(): String =
    when (this) {
        WorkOrderTaskKind.INSPECTION -> "Kiểm tra"
        WorkOrderTaskKind.REPAIR -> "Sửa chữa"
        WorkOrderTaskKind.SURVEY -> "Khảo sát"
        WorkOrderTaskKind.POLE_GPS_UPDATE -> "Cập nhật GPS cột"
    }

fun WorkOrderTaskKind.icon(): ImageVector =
    when (this) {
        WorkOrderTaskKind.INSPECTION -> Icons.Rounded.Search
        WorkOrderTaskKind.REPAIR -> Icons.Rounded.Build
        WorkOrderTaskKind.SURVEY -> Icons.Rounded.Videocam
        WorkOrderTaskKind.POLE_GPS_UPDATE -> Icons.Rounded.LocationOn
    }

@Composable
@ReadOnlyComposable
fun WorkOrderTaskKind.badgeColors(): BadgeColors = LuxTheme.status.kind

// task_kind arrives from the backend as a snake_case wire string ("inspection"/"repair"/
// "survey") - an unknown value falls back to Inspection (the non-destructive one) rather than
// crashing.
fun workOrderTaskKindFromWire(value: String): WorkOrderTaskKind =
    when (value) {
        "repair" -> WorkOrderTaskKind.REPAIR
        "survey" -> WorkOrderTaskKind.SURVEY
        "pole_gps_update" -> WorkOrderTaskKind.POLE_GPS_UPDATE
        else -> WorkOrderTaskKind.INSPECTION
    }
