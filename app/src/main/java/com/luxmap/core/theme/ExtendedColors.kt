package com.luxmap.core.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

enum class IotStatus { ONLINE, OFFLINE, NONE }

@Immutable
data class LuxExtendedColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val info: Color,
    val onInfo: Color,
    val infoContainer: Color,
    val onInfoContainer: Color,
    val disabledContainer: Color,
    val disabledContent: Color,
    val focusRing: Color,
    val overlayOnMedia: Color,
    val cardContainer: Color,
    val dialogContainer: Color,
    val dueOverdue: Color,
    val dueToday: Color,
    val dueFuture: Color,
)

@Immutable
data class LuxStatusColors(
    val asset: Map<AssetCondition, BadgeColors>,
    val workOrder: Map<WorkOrderStatus, BadgeColors>,
    val priority: Map<WorkOrderPriority, BadgeColors>,
    val sync: Map<SyncStatus, BadgeColors>,
    val iot: Map<IotStatus, BadgeColors>,
    val kind: BadgeColors,
)

private val OverlayOnMedia = Color.Black.copy(alpha = 0.6f)

internal val LightExtendedColors =
    LuxExtendedColors(
        success = Color(0xFF047857),
        onSuccess = White,
        successContainer = Color(0xFFD1FAE5),
        onSuccessContainer = Color(0xFF064E3B),
        warning = Color(0xFFB45309),
        onWarning = White,
        warningContainer = Color(0xFFFEF3C7),
        onWarningContainer = Color(0xFF78350F),
        info = Color(0xFF0369A1),
        onInfo = White,
        infoContainer = Color(0xFFE0F2FE),
        onInfoContainer = Color(0xFF0C4A6E),
        disabledContainer = DisabledContainer,
        disabledContent = DisabledContent,
        focusRing = Blue500,
        overlayOnMedia = OverlayOnMedia,
        cardContainer = White,
        dialogContainer = White,
        dueOverdue = Red700,
        dueToday = Color(0xFFB45309),
        dueFuture = Slate600,
    )

internal val NightFieldExtendedColors =
    LuxExtendedColors(
        success = Color(0xFF6FD3A8),
        onSuccess = Color(0xFF11392A),
        successContainer = Color(0xFF11392A),
        onSuccessContainer = Color(0xFFBDF0D6),
        warning = Color(0xFFF7C66D),
        onWarning = Color(0xFF3A2606),
        warningContainer = Color(0xFF4A3310),
        onWarningContainer = Color(0xFFFBE2B0),
        info = Color(0xFF7CC4F0),
        onInfo = Color(0xFF0F3550),
        infoContainer = Color(0xFF0F3550),
        onInfoContainer = Color(0xFFCDE9FA),
        disabledContainer = DisabledContainerDark,
        disabledContent = DisabledContentDark,
        focusRing = Navy300,
        overlayOnMedia = OverlayOnMedia,
        cardContainer = Night850,
        dialogContainer = Night800,
        dueOverdue = ErrorDark,
        dueToday = Color(0xFFF7C66D),
        dueFuture = DarkSecondary,
    )

private fun pair(
    background: Long,
    text: Long,
) = BadgeColors(Color(background), Color(text))

private val LightNeutral = pair(0xFFEFEFEF, 0xFF555555)
private val LightNavy = pair(0xFFE8EEF7, 0xFF1F3864)
private val LightAmberProgress = pair(0xFFFFF3DC, 0xFF8A5A00)
private val LightTeal = pair(0xFFE3F6F1, 0xFF1E6B5C)
private val LightRose = pair(0xFFFBE4E4, 0xFF9B2C2C)
private val LightOrange = pair(0xFFFDE8D0, 0xFF8A3B00)
private val LightSlate = pair(0xFFF1F5F9, 0xFF475569)
private val LightGreen = pair(0xFFD1FAE5, 0xFF065F46)
private val LightAmber = pair(0xFFFEF3C7, 0xFF92400E)
private val LightRed = pair(0xFFFEE2E2, 0xFF991B1B)

private val NightNeutral = pair(0xFF2E2E2E, 0xFFCFCFCF)
private val NightNavy = pair(0xFF1C2A44, 0xFFC3D3EE)
private val NightAmberProgress = pair(0xFF3D2C0B, 0xFFF3C978)
private val NightTeal = pair(0xFF12352E, 0xFF93DCCB)
private val NightRose = pair(0xFF451818, 0xFFF8A5A5)
private val NightOrange = pair(0xFF45250B, 0xFFF6B27A)
private val NightSlate = pair(0xFF262626, 0xFFBDBDBD)
private val NightAmber = pair(0xFF4A3310, 0xFFF7C66D)
private val NightRed = pair(0xFF4A1717, 0xFFFF9A9A)

internal val LightStatusColors =
    LuxStatusColors(
        asset =
            mapOf(
                AssetCondition.NORMAL to LightGreen,
                AssetCondition.DIM to LightAmber,
                AssetCondition.OUT to LightRed,
                AssetCondition.UNKNOWN to LightNeutral,
            ),
        workOrder =
            mapOf(
                WorkOrderStatus.OPEN to LightNeutral,
                WorkOrderStatus.ASSIGNED to LightNavy,
                WorkOrderStatus.IN_PROGRESS to LightAmberProgress,
                WorkOrderStatus.DONE to LightTeal,
                WorkOrderStatus.VERIFIED to LightGreen,
                WorkOrderStatus.CANCELLED to LightRose,
            ),
        priority =
            mapOf(
                WorkOrderPriority.LOW to LightSlate,
                WorkOrderPriority.NORMAL to LightNavy,
                WorkOrderPriority.HIGH to LightAmber,
                WorkOrderPriority.URGENT to LightRed,
            ),
        sync =
            mapOf(
                SyncStatus.QUEUED_OFFLINE to LightNeutral,
                SyncStatus.QUEUED_ONLINE to LightNavy,
                SyncStatus.SYNCING to LightAmberProgress,
                SyncStatus.FAILED to LightRose,
                SyncStatus.CONFLICT to LightOrange,
                SyncStatus.DONE to LightTeal,
            ),
        iot =
            mapOf(
                IotStatus.ONLINE to LightTeal,
                IotStatus.OFFLINE to LightOrange,
                IotStatus.NONE to LightSlate,
            ),
        kind = pair(0xFFF1F5F9, 0xFF334155),
    )

internal val NightFieldStatusColors =
    LuxStatusColors(
        asset =
            mapOf(
                AssetCondition.NORMAL to pair(0xFF123D34, 0xFF8CE3D1),
                AssetCondition.DIM to NightAmber,
                AssetCondition.OUT to NightRed,
                AssetCondition.UNKNOWN to pair(0xFF303030, 0xFFD0D0D0),
            ),
        workOrder =
            mapOf(
                WorkOrderStatus.OPEN to NightNeutral,
                WorkOrderStatus.ASSIGNED to NightNavy,
                WorkOrderStatus.IN_PROGRESS to NightAmberProgress,
                WorkOrderStatus.DONE to NightTeal,
                WorkOrderStatus.VERIFIED to pair(0xFF0F3B2B, 0xFF86E0B5),
                WorkOrderStatus.CANCELLED to NightRose,
            ),
        priority =
            mapOf(
                WorkOrderPriority.LOW to NightSlate,
                WorkOrderPriority.NORMAL to NightNavy,
                WorkOrderPriority.HIGH to NightAmber,
                WorkOrderPriority.URGENT to NightRed,
            ),
        sync =
            mapOf(
                SyncStatus.QUEUED_OFFLINE to NightNeutral,
                SyncStatus.QUEUED_ONLINE to NightNavy,
                SyncStatus.SYNCING to NightAmberProgress,
                SyncStatus.FAILED to NightRose,
                SyncStatus.CONFLICT to NightOrange,
                SyncStatus.DONE to NightTeal,
            ),
        iot =
            mapOf(
                IotStatus.ONLINE to NightTeal,
                IotStatus.OFFLINE to NightOrange,
                IotStatus.NONE to NightSlate,
            ),
        kind = pair(0xFF262626, 0xFFD4D4D4),
    )

val LocalLuxExtendedColors = staticCompositionLocalOf { LightExtendedColors }
val LocalLuxStatusColors = staticCompositionLocalOf { LightStatusColors }

object LuxTheme {
    val colors: LuxExtendedColors
        @Composable
        @ReadOnlyComposable
        get() = LocalLuxExtendedColors.current

    val status: LuxStatusColors
        @Composable
        @ReadOnlyComposable
        get() = LocalLuxStatusColors.current

    val brand: LuxBrandColors
        @Composable
        @ReadOnlyComposable
        get() = LocalLuxBrandColors.current
}
