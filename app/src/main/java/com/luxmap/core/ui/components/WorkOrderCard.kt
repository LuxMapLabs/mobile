package com.luxmap.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.luxmap.core.common.DateFormatUtils
import com.luxmap.core.theme.Dimens
import com.luxmap.core.theme.Spacing
import com.luxmap.core.theme.WorkOrderPriority
import com.luxmap.core.theme.color
import com.luxmap.core.theme.label

// Thẻ 1 lệnh sửa chữa — cùng field cho F08 (danh sách đầy đủ) lẫn F02 (danh sách rút gọn trên
// Trang chủ), theo đúng "Thành phần giao diện" của F08 trong đặc tả chi tiết: mã lệnh, địa chỉ
// rút gọn, loại sự cố, mức ưu tiên (màu + chữ), hạn xử lý (SLA), khoảng cách tới vị trí hiện tại.
// slaDueAt nhận ISO string thô (như PoleDetail lưu field JSON), format ở đây bằng DateFormatUtils.
@Composable
fun WorkOrderCard(
    workOrderId: String,
    shortAddress: String,
    faultTypeLabel: String,
    priority: WorkOrderPriority,
    slaDueAt: String?,
    distanceMeters: Double?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Dimens.radiusMedium))
                .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = workOrderId, style = MaterialTheme.typography.titleMedium)
            // Priority chỉ có 1 màu chữ, không có nền riêng (mục 2.5 CLAUDE.md) — dùng
            // TextOnlyStatusBadge, không dùng StatusBadge (StatusBadge cần cặp bg/text).
            TextOnlyStatusBadge(text = priority.label(), color = priority.color())
        }
        Text(
            text = shortAddress,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = faultTypeLabel,
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Hạn: ${DateFormatUtils.formatIsoInstant(slaDueAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (distanceMeters != null) {
                Text(
                    text = formatDistance(distanceMeters),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun formatDistance(meters: Double): String =
    if (meters >= 1000) {
        "%.1f km".format(meters / 1000)
    } else {
        "${meters.toInt()} m"
    }
