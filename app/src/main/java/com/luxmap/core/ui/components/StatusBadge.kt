package com.luxmap.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.luxmap.core.theme.StatusDoneBg
import com.luxmap.core.theme.StatusDoneText
import com.luxmap.core.theme.StatusOverdueBg
import com.luxmap.core.theme.StatusOverdueText
import com.luxmap.core.theme.StatusProgressBg
import com.luxmap.core.theme.StatusProgressText
import com.luxmap.core.theme.StatusReceivedBg
import com.luxmap.core.theme.StatusReceivedText
import com.luxmap.core.theme.StatusRejectedBg
import com.luxmap.core.theme.StatusRejectedText

// 5 trạng thái dùng chung (mục C3 đặc tả) — cặp màu bg/text lấy đúng từ
// design tokens CLAUDE.md, không đổi. Nhãn hiển thị do màn hình gọi cung cấp
// (tra đúng chữ trong đặc tả từng màn, không đoán ở đây).
enum class ReportStatus {
    RECEIVED,
    IN_PROGRESS,
    DONE,
    OVERDUE,
    REJECTED,
}

private fun ReportStatus.backgroundColor(): Color =
    when (this) {
        ReportStatus.RECEIVED -> StatusReceivedBg
        ReportStatus.IN_PROGRESS -> StatusProgressBg
        ReportStatus.DONE -> StatusDoneBg
        ReportStatus.OVERDUE -> StatusOverdueBg
        ReportStatus.REJECTED -> StatusRejectedBg
    }

private fun ReportStatus.textColor(): Color =
    when (this) {
        ReportStatus.RECEIVED -> StatusReceivedText
        ReportStatus.IN_PROGRESS -> StatusProgressText
        ReportStatus.DONE -> StatusDoneText
        ReportStatus.OVERDUE -> StatusOverdueText
        ReportStatus.REJECTED -> StatusRejectedText
    }

@Composable
fun StatusBadge(
    status: ReportStatus,
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        color = status.textColor(),
        style = MaterialTheme.typography.bodySmall,
        modifier =
            modifier
                .background(color = status.backgroundColor(), shape = RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
