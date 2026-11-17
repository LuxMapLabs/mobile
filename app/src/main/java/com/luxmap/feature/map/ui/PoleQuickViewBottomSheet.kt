package com.luxmap.feature.map.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.luxmap.core.common.openInMaps
import com.luxmap.core.theme.Spacing
import com.luxmap.core.theme.badgeColors
import com.luxmap.core.theme.label
import com.luxmap.core.ui.components.LuxActionPair
import com.luxmap.core.ui.components.LuxBanner
import com.luxmap.core.ui.components.LuxBannerTone
import com.luxmap.core.ui.components.LuxSecondaryButton
import com.luxmap.core.ui.components.PrimaryButton
import com.luxmap.core.ui.components.StatusBadge
import com.luxmap.feature.map.data.PoleMarker

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
) {
    val sheetState = rememberModalBottomSheetState()
    val context = LocalContext.current

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg).padding(bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            QuickViewHeader(title = pole.poleId, onClose = onDismiss)
            StatusBadge(
                text = pole.fixtureStatus.mapLabel(),
                colors = pole.fixtureStatus.badgeColors(),
                icon = Icons.Filled.Lightbulb,
            )
            Column {
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
                        leadingIcon = Icons.Filled.Navigation,
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
