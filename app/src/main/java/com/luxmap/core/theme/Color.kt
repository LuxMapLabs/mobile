package com.luxmap.core.theme

import androidx.compose.ui.graphics.Color

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

// Component — badge ưu tiên Work Order (mục 2.5), một màu chữ trên nền surface, không có nền riêng
enum class WorkOrderPriority { LOW, NORMAL, HIGH, URGENT }

fun WorkOrderPriority.color(): Color =
    when (this) {
        WorkOrderPriority.LOW -> Gray500
        WorkOrderPriority.NORMAL -> Blue500
        WorkOrderPriority.HIGH -> Amber500
        WorkOrderPriority.URGENT -> Rose600
    }
