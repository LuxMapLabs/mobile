package com.luxmap.feature.map.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.luxmap.core.common.openInMaps
import com.luxmap.core.theme.Spacing
import com.luxmap.core.theme.WorkOrderStatus
import com.luxmap.core.theme.badgeColors
import com.luxmap.core.theme.label
import com.luxmap.core.ui.components.LuxActionPair
import com.luxmap.core.ui.components.LuxBanner
import com.luxmap.core.ui.components.LuxBannerTone
import com.luxmap.core.ui.components.LuxSecondaryButton
import com.luxmap.core.ui.components.PrimaryButton
import com.luxmap.core.ui.components.StatusBadge
import com.luxmap.feature.map.data.PoleMarker
import java.util.Locale

// Xem nhanh khi chạm marker (F12) — chỉ hiển thị field cần cho quyết định "có đáng ghé qua
// không". Panel chi tiết đầy đủ (lịch sử luminance/runtime, ảnh khảo sát) là FM-27, mở qua
// onOpenDetail. No "lux gần nhất" stat here (the real screen mockup shows one) - F07 removed
// lux_reading from the app entirely, there is no real recent-lux value to show; the IoT presence
// stat below uses the real hasIotNode field instead.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PoleQuickViewBottomSheet(
    pole: PoleMarker,
    onDismiss: () -> Unit,
    onOpenDetail: () -> Unit,
    onNavigate: (() -> Unit)? = null,
    isProposedGps: Boolean = false,
) {
    val sheetState = rememberModalBottomSheetState()
    val context = LocalContext.current

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg).padding(bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            QuickViewHeader(title = pole.poleId, onClose = onDismiss)
            if (isProposedGps) {
                StatusBadge(
                    text = "GPS đề xuất — chưa duyệt",
                    colors = WorkOrderStatus.IN_PROGRESS.badgeColors(),
                    icon = Icons.Rounded.MyLocation,
                )
                LuxBanner(
                    title = "Tọa độ chưa duyệt",
                    message =
                        "Vị trí được hiển thị theo dữ liệu GPS mới đo, " +
                            "đang chờ Quản lý duyệt vào GIS chính thức.",
                    tone = LuxBannerTone.WARNING,
                )
            } else {
                StatusBadge(
                    text = pole.fixtureStatus.mapLabel(),
                    colors = pole.fixtureStatus.badgeColors(),
                    icon = Icons.Rounded.Lightbulb,
                )
            }
            Column {
                if (isProposedGps) {
                    QuickViewFactRow(
                        label = "Tọa độ GPS đề xuất",
                        value = "%.6f, %.6f".format(Locale.ROOT, pole.lat, pole.lng),
                    )
                }
                QuickViewFactRow(
                    label = "Nguồn điện",
                    value =
                        when (pole.powerSource) {
                            "solar" -> "Năng lượng mặt trời"
                            "grid" -> "Lưới điện"
                            else -> "Chưa xác định"
                        },
                )
                QuickViewFactRow(label = "Thiết bị IoT", value = if (pole.hasIotNode) "Có" else "Không")
            }
            if (pole.nearSensitivePoi) {
                LuxBanner(
                    message = "Gần khu vực nhạy cảm (trường học/chợ/cầu/ngã ba)",
                    tone = LuxBannerTone.WARNING,
                )
            }
            LuxActionPair(
                secondary = { actionModifier ->
                    LuxSecondaryButton(
                        text = "Đường đi",
                        onClick = onNavigate ?: { openInMaps(context, pole.lat, pole.lng) },
                        leadingIcon = Icons.Rounded.Navigation,
                        modifier = actionModifier,
                    )
                },
                primary = { actionModifier ->
                    PrimaryButton(text = "Chi tiết", onClick = onOpenDetail, modifier = actionModifier)
                },
            )
        }
    }
}
