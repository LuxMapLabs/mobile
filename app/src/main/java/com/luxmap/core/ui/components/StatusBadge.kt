package com.luxmap.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.luxmap.core.theme.BadgeColors
import com.luxmap.core.theme.BadgeTextStyle
import com.luxmap.core.theme.Radius
import com.luxmap.core.theme.Spacing

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
        style = BadgeTextStyle,
        modifier =
            modifier
                .background(color = colors.background, shape = RoundedCornerShape(Radius.badge))
                .padding(horizontal = Spacing.sm, vertical = 3.dp),
    )
}

// PriorityDot — 8dp colored dot + onSurface-colored text, replaces TextOnlyStatusBadge.
// The spec explicitly calls out not using the priority color as text color on a light
// background (e.g. Amber500 text is ~2.2:1 contrast) — the dot carries the color instead.
@Composable
fun PriorityDot(
    text: String,
    dotColor: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(8.dp)
                    .background(color = dotColor, shape = CircleShape),
        )
        Spacer(Modifier.width(Spacing.xs))
        Text(text = text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}
