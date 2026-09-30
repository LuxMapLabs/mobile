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
import androidx.compose.ui.text.style.TextOverflow
import com.luxmap.core.common.DateFormatUtils
import com.luxmap.core.theme.Dimens
import com.luxmap.core.theme.Spacing
import com.luxmap.core.theme.badgeColors
import com.luxmap.core.theme.label
import com.luxmap.core.theme.workOrderStatusFromWire

// Thẻ 1 lệnh sửa chữa — dùng chung cho F02 (danh sách rút gọn trên Trang chủ) và F08 (danh sách
// đầy đủ) sau này, cả 2 đều đọc từ cùng GET /work-orders. Rút gọn còn đúng field API danh sách
// thật sự trả về (mã lệnh, trạng thái, hạn xử lý, điểm ưu tiên thô) - KHÔNG có địa chỉ/loại sự
// cố/khoảng cách như mục 6.4 Design System v2.0 mô tả, vì backend list endpoint không trả các
// field đó (chỉ GET /work-orders/{id} mới có). Sai lệch có chủ đích, ghi ở docs/contract-drift.md.
// dueDate nhận chuỗi ngày thô "YYYY-MM-DD" (wo_status.due_date), không parse ở đây.
@Composable
fun WorkOrderCard(
    workOrderId: String,
    woStatus: String,
    dueDate: String?,
    priorityScore: Double?,
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
            Text(
                text = workOrderId,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val status = workOrderStatusFromWire(woStatus)
            StatusBadge(text = status.label(), colors = status.badgeColors())
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = if (dueDate != null) "Hạn: ${DateFormatUtils.formatPlainDate(dueDate)}" else "Chưa có hạn",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (priorityScore != null) {
                Text(
                    text = "Ưu tiên: ${priorityScore.toInt()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
