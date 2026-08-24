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
import com.luxmap.core.theme.BadgeColors
import com.luxmap.core.theme.Dimens

// Component nền, không tự gắn cứng theo 1 bộ trạng thái — LuxMap có nhiều bộ trạng thái độc lập
// (tình trạng tài sản, đồng bộ, ưu tiên work order, work_order.status...), mỗi bộ tự tra đúng cặp
// màu ở core/theme/Color.kt (badgeColors()) rồi gọi StatusBadge này. Không gộp chung 1 enum.
@Composable
fun StatusBadge(
    text: String,
    colors: BadgeColors,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        color = colors.text,
        style = MaterialTheme.typography.bodySmall,
        modifier =
            modifier
                .background(color = colors.background, shape = RoundedCornerShape(Dimens.radiusSmall))
                .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

// Biến thể cho badge chỉ có màu chữ, không có nền riêng (VD: ưu tiên Work Order, mục 2.5 —
// bảng chỉ định nghĩa 1 màu, không có cặp bg/text).
@Composable
fun TextOnlyStatusBadge(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        color = color,
        style = MaterialTheme.typography.labelLarge,
        modifier = modifier,
    )
}
