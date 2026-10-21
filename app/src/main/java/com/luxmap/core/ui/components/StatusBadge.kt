package com.luxmap.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.luxmap.core.theme.BadgeColors
import com.luxmap.core.theme.BadgeTextStyle
import com.luxmap.core.theme.Radius
import com.luxmap.core.theme.Spacing

// Component nền, không tự gắn cứng theo 1 bộ trạng thái — LuxMap có nhiều bộ trạng thái độc lập
// (tình trạng tài sản, đồng bộ, ưu tiên work order, work_order.status...), mỗi bộ tự tra đúng cặp
// màu ở core/theme/Color.kt (badgeColors()) rồi gọi StatusBadge này. Không gộp chung 1 enum.
// icon is optional and defaults to null so every existing text-only caller keeps working
// unchanged - F02's task-kind badge is the first caller that needs it.
@Composable
fun StatusBadge(
    text: String,
    colors: BadgeColors,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Row(
        modifier =
            modifier
                .background(color = colors.background, shape = RoundedCornerShape(Radius.badge))
                .padding(horizontal = Spacing.sm, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, tint = colors.text, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text = text, color = colors.text, style = BadgeTextStyle)
    }
}

// Bordered pill, same padding/shape as StatusBadge - previously bare dot+text with no box, which
// looked uneven sitting next to StatusBadge's filled pills in the same row (e.g. the work order
// hero's "Kiểm tra"/"Hoàn tất" badges next to "Ưu tiên Bình thường").
@Composable
fun PriorityDot(
    text: String,
    dotColor: Color,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    borderColor: Color = MaterialTheme.colorScheme.outline,
) {
    Row(
        modifier =
            modifier
                .border(1.dp, borderColor, RoundedCornerShape(Radius.badge))
                .padding(horizontal = Spacing.sm, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(8.dp)
                    .background(color = dotColor, shape = CircleShape),
        )
        Spacer(Modifier.width(Spacing.xs))
        Text(text = text, style = BadgeTextStyle, color = textColor, maxLines = 1)
    }
}
