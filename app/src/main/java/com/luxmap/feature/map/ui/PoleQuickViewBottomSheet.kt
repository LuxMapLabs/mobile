package com.luxmap.feature.map.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.luxmap.core.theme.Spacing
import com.luxmap.core.theme.badgeColors
import com.luxmap.core.theme.label
import com.luxmap.core.ui.components.StatusBadge
import com.luxmap.feature.map.data.PoleMarker

// Xem nhanh khi chạm marker (F12) — chỉ hiển thị field cần cho quyết định "có đáng ghé qua
// không". Panel chi tiết đầy đủ (lịch sử luminance/runtime, ảnh khảo sát) là FM-27, mở qua
// onOpenDetail.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PoleQuickViewBottomSheet(
    pole: PoleMarker,
    onDismiss: () -> Unit,
    onOpenDetail: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    val isDark = isSystemInDarkTheme()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm)) {
            Text(text = pole.poleId, style = MaterialTheme.typography.titleLarge)

            Spacer(Modifier.height(Spacing.sm))

            Row {
                StatusBadge(
                    text = pole.fixtureStatus.label(),
                    colors = pole.fixtureStatus.badgeColors(isDark),
                )
            }

            Spacer(Modifier.height(Spacing.md))

            Text(
                text = "Nguồn điện: ${if (pole.powerSource == "solar") "Năng lượng mặt trời" else "Lưới điện"}",
                style = MaterialTheme.typography.bodyMedium,
            )

            if (pole.nearSensitivePoi) {
                Spacer(Modifier.height(Spacing.xs))
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

            TextButton(onClick = onOpenDetail, modifier = Modifier.fillMaxWidth()) {
                Text("Mở chi tiết")
            }

            Spacer(Modifier.height(Spacing.lg))
        }
    }
}
