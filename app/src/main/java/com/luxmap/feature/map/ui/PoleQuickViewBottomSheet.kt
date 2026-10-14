package com.luxmap.feature.map.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.luxmap.core.common.openInMaps
import com.luxmap.core.theme.Dimens
import com.luxmap.core.theme.Radius
import com.luxmap.core.theme.Spacing
import com.luxmap.core.theme.badgeColors
import com.luxmap.core.theme.label
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
) {
    val sheetState = rememberModalBottomSheetState()
    val isDark = isSystemInDarkTheme()
    val context = LocalContext.current

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = pole.poleId, style = MaterialTheme.typography.titleLarge)
                StatusBadge(
                    text = pole.fixtureStatus.label(),
                    colors = pole.fixtureStatus.badgeColors(isDark),
                )
            }

            Spacer(Modifier.height(Spacing.md))

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                PoleStatCell(
                    label = "Nguồn điện",
                    value = if (pole.powerSource == "solar") "Năng lượng mặt trời" else "Lưới điện",
                    modifier = Modifier.weight(1f),
                )
                PoleStatCell(
                    label = "Cảm biến IoT",
                    value = if (pole.hasIotNode) "Có" else "Không",
                    modifier = Modifier.weight(1f),
                )
            }

            if (pole.nearSensitivePoi) {
                Spacer(Modifier.height(Spacing.sm))
                Row {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(Spacing.xs))
                    Text(
                        text = "Gần khu vực nhạy cảm (trường học/chợ/cầu/ngã ba)",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Spacer(Modifier.height(Spacing.lg))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedButton(
                    onClick = { openInMaps(context, pole.lat, pole.lng) },
                    modifier = Modifier.weight(1f).defaultMinSize(minHeight = Dimens.minTouchTarget),
                ) {
                    Icon(imageVector = Icons.Filled.Navigation, contentDescription = null)
                    Spacer(Modifier.width(Spacing.xs))
                    Text("Đường đi")
                }
                PrimaryButton(text = "Chi tiết", onClick = onOpenDetail, modifier = Modifier.weight(1f))
            }

            Spacer(Modifier.height(Spacing.lg))
        }
    }
}

@Composable
private fun PoleStatCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(Radius.control))
                .padding(Spacing.sm),
    ) {
        Text(text = value, style = MaterialTheme.typography.titleMedium)
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
