package com.luxmap.core.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

// Primitive — giá trị màu gốc (mục 2.1 Design System v2.0)
val Navy700 = Color(0xFF1F3864)
val Blue500 = Color(0xFF3E86C9)
val Green400 = Color(0xFF5FC4B0)
val Amber500 = Color(0xFFE9A23B)
val Rose600 = Color(0xFFD64545)
val Success600 = Color(0xFF059669)
val Danger600 = Color(0xFFDC2626)
val Gray25 = Color(0xFFF8FAFC)
val Gray50 = Color(0xFFF1F5F9)
val Gray200 = Color(0xFFE2E8F0)
val Gray500 = Color(0xFF64748B)
val Gray700 = Color(0xFF334155)
val Gray900 = Color(0xFF0F172A)
val Dark950 = Color(0xFF0D0D0D)
val Dark900 = Color(0xFF121212)
val Dark800 = Color(0xFF1A1A1A)
val Dark700 = Color(0xFF2A2A2A)
val DarkText = Color(0xFFF5F5F5)
val DarkMuted = Color(0xFFA0A0A0)

// Extra primitives — section 3.1 of Global Design System v1.0, needed for the hero gradient
// (F01 and other hero-style screens). Does not change the meaning of the existing tokens above.
val Navy900 = Color(0xFF102443)
val Blue600 = Color(0xFF2D6388)

// Gradient — section 3.3 of Global Design System v1.0. Only for the single most prominent
// hero area on a screen, never for badges or warning states (rule in the same section).
val BrandHeroGradient =
    Brush.verticalGradient(
        0f to Navy900,
        0.58f to Navy700,
        1f to Blue600,
    )

// Semantic — theo theme (mục 2.2), dùng trong LuxMapColorScheme ở Theme.kt
data class LuxMapSemanticColors(
    val background: Color,
    val surface: Color,
    val surfaceSubtle: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val border: Color,
    val focus: Color,
    val navigationActive: Color,
    val navigationInactive: Color,
)

val LightSemanticColors =
    LuxMapSemanticColors(
        background = Gray25,
        surface = Color(0xFFFFFFFF),
        surfaceSubtle = Gray50,
        textPrimary = Gray900,
        textSecondary = Gray500,
        border = Gray200,
        focus = Blue500,
        navigationActive = Navy700,
        navigationInactive = Gray500,
    )

val DarkSemanticColors =
    LuxMapSemanticColors(
        background = Dark950,
        surface = Dark800,
        surfaceSubtle = Dark900,
        textPrimary = DarkText,
        textSecondary = DarkMuted,
        border = Dark700,
        focus = Green400,
        navigationActive = Green400,
        navigationInactive = DarkMuted,
    )

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

fun AssetCondition.badgeColors(isDark: Boolean): BadgeColors =
    when (this) {
        AssetCondition.NORMAL ->
            if (isDark) {
                BadgeColors(
                    Color(0xFF123D34),
                    Color(0xFF8CE3D1),
                )
            } else {
                BadgeColors(Color(0xFFD1FAE5), Color(0xFF065F46))
            }
        AssetCondition.DIM ->
            if (isDark) {
                BadgeColors(
                    Color(0xFF4A3310),
                    Color(0xFFF7C66D),
                )
            } else {
                BadgeColors(Color(0xFFFEF3C7), Color(0xFF92400E))
            }
        AssetCondition.OUT ->
            if (isDark) {
                BadgeColors(
                    Color(0xFF4A1717),
                    Color(0xFFFF9A9A),
                )
            } else {
                BadgeColors(Color(0xFFFEE2E2), Color(0xFF991B1B))
            }
        AssetCondition.UNKNOWN ->
            if (isDark) {
                BadgeColors(
                    Color(0xFF303030),
                    Color(0xFFD0D0D0),
                )
            } else {
                BadgeColors(Color(0xFFEFEFEF), Color(0xFF555555))
            }
    }

// Component — badge trạng thái đồng bộ (mục 2.4). queued tách offline/online ở tầng gọi,
// không gộp failed/conflict (xem CLAUDE.md "Nguyên tắc nghiệp vụ cốt lõi")
enum class SyncStatus { QUEUED_OFFLINE, QUEUED_ONLINE, SYNCING, FAILED, CONFLICT, DONE }

fun SyncStatus.badgeColors(): BadgeColors =
    when (this) {
        SyncStatus.QUEUED_OFFLINE -> BadgeColors(Color(0xFFEFEFEF), Color(0xFF555555))
        SyncStatus.QUEUED_ONLINE -> BadgeColors(Color(0xFFE8EEF7), Color(0xFF1F3864))
        SyncStatus.SYNCING -> BadgeColors(Color(0xFFFFF3DC), Color(0xFF8A5A00))
        SyncStatus.FAILED -> BadgeColors(Color(0xFFFBE4E4), Color(0xFF9B2C2C))
        SyncStatus.CONFLICT -> BadgeColors(Color(0xFFFDE8D0), Color(0xFF8A3B00))
        SyncStatus.DONE -> BadgeColors(Color(0xFFE3F6F1), Color(0xFF1E6B5C))
    }

fun SyncStatus.label(): String =
    when (this) {
        SyncStatus.QUEUED_OFFLINE -> "Chờ mạng"
        SyncStatus.QUEUED_ONLINE -> "Chờ đồng bộ"
        SyncStatus.SYNCING -> "Đang đồng bộ"
        SyncStatus.FAILED -> "Đồng bộ lỗi"
        SyncStatus.CONFLICT -> "Xung đột"
        SyncStatus.DONE -> "Đã đồng bộ"
    }

// Component — badge ưu tiên Work Order (mục 2.5), một màu chữ trên nền surface, không có nền riêng
enum class WorkOrderPriority { LOW, NORMAL, HIGH, URGENT }

// Nhãn đúng chữ trong CLAUDE.md "Badge ưu tiên Work Order" — dùng chung cho mọi nơi hiển thị priority.
fun WorkOrderPriority.label(): String =
    when (this) {
        WorkOrderPriority.LOW -> "Thấp"
        WorkOrderPriority.NORMAL -> "Bình thường"
        WorkOrderPriority.HIGH -> "Cao"
        WorkOrderPriority.URGENT -> "Khẩn"
    }

fun WorkOrderPriority.color(): Color =
    when (this) {
        WorkOrderPriority.LOW -> Gray500
        WorkOrderPriority.NORMAL -> Blue500
        WorkOrderPriority.HIGH -> Amber500
        WorkOrderPriority.URGENT -> Rose600
    }

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
        WorkOrderStatus.ASSIGNED -> "Đã giao"
        WorkOrderStatus.IN_PROGRESS -> "Đang xử lý"
        WorkOrderStatus.DONE -> "Hoàn tất"
        WorkOrderStatus.VERIFIED -> "Đã nghiệm thu"
        WorkOrderStatus.CANCELLED -> "Đã huỷ"
    }

fun WorkOrderStatus.icon(): ImageVector =
    when (this) {
        WorkOrderStatus.OPEN, WorkOrderStatus.ASSIGNED -> Icons.Filled.Assignment
        WorkOrderStatus.IN_PROGRESS -> Icons.Filled.Autorenew
        WorkOrderStatus.DONE -> Icons.Filled.CheckCircle
        WorkOrderStatus.VERIFIED -> Icons.Filled.Verified
        WorkOrderStatus.CANCELLED -> Icons.Filled.Cancel
    }

fun WorkOrderStatus.badgeColors(): BadgeColors =
    when (this) {
        WorkOrderStatus.OPEN -> BadgeColors(Color(0xFFEFEFEF), Color(0xFF555555))
        WorkOrderStatus.ASSIGNED -> BadgeColors(Color(0xFFE8EEF7), Color(0xFF1F3864))
        WorkOrderStatus.IN_PROGRESS -> BadgeColors(Color(0xFFFFF3DC), Color(0xFF8A5A00))
        WorkOrderStatus.DONE -> BadgeColors(Color(0xFFE3F6F1), Color(0xFF1E6B5C))
        WorkOrderStatus.VERIFIED -> BadgeColors(Color(0xFFD1FAE5), Color(0xFF065F46))
        WorkOrderStatus.CANCELLED -> BadgeColors(Color(0xFFFBE4E4), Color(0xFF9B2C2C))
    }

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

// Component - task_kind badge (F02 cluster list), distinguishes an inspection ticket from a
// repair ticket on the same work_order entity. Colors reuse the existing ASSIGNED/IN_PROGRESS
// pairs above rather than adding new ones.
enum class WorkOrderTaskKind { INSPECTION, REPAIR, SURVEY }

fun WorkOrderTaskKind.label(): String =
    when (this) {
        WorkOrderTaskKind.INSPECTION -> "Kiểm tra"
        WorkOrderTaskKind.REPAIR -> "Sửa chữa"
        WorkOrderTaskKind.SURVEY -> "Khảo sát"
    }

fun WorkOrderTaskKind.icon(): ImageVector =
    when (this) {
        WorkOrderTaskKind.INSPECTION -> Icons.Filled.Search
        WorkOrderTaskKind.REPAIR -> Icons.Filled.Build
        WorkOrderTaskKind.SURVEY -> Icons.Filled.Videocam
    }

fun WorkOrderTaskKind.badgeColors(): BadgeColors =
    when (this) {
        WorkOrderTaskKind.INSPECTION -> WorkOrderStatus.ASSIGNED.badgeColors()
        WorkOrderTaskKind.REPAIR -> WorkOrderStatus.IN_PROGRESS.badgeColors()
        WorkOrderTaskKind.SURVEY -> WorkOrderStatus.DONE.badgeColors()
    }

// task_kind arrives from the backend as a snake_case wire string ("inspection"/"repair"/
// "survey") - an unknown value falls back to Inspection (the non-destructive one) rather than
// crashing.
fun workOrderTaskKindFromWire(value: String): WorkOrderTaskKind =
    when (value) {
        "repair" -> WorkOrderTaskKind.REPAIR
        "survey" -> WorkOrderTaskKind.SURVEY
        else -> WorkOrderTaskKind.INSPECTION
    }
