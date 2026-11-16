package com.luxmap.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.luxmap.core.theme.AssetCondition
import com.luxmap.core.theme.BadgeColors
import com.luxmap.core.theme.LuxMapTheme
import com.luxmap.core.theme.Spacing
import com.luxmap.core.theme.SyncStatus
import com.luxmap.core.theme.badgeColors
import com.luxmap.core.theme.icon
import com.luxmap.core.theme.label

private val BadgeMinHeight = 28.dp
private val BadgeIconSize = 16.dp

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
                .semantics(mergeDescendants = true) {}
                .heightIn(min = BadgeMinHeight)
                .background(color = colors.background, shape = MaterialTheme.shapes.extraSmall)
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.text,
                modifier = Modifier.size(BadgeIconSize),
            )
            Spacer(Modifier.width(Spacing.xs))
        }
        Text(text = text, color = colors.text, style = MaterialTheme.typography.labelMedium)
    }
}

@LuxPreviews
@Composable
private fun StatusBadgePreview() {
    LuxMapTheme {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            AssetCondition.entries.forEach { StatusBadge(text = it.label(), colors = it.badgeColors()) }
            SyncStatus.entries.forEach {
                StatusBadge(text = it.label(), colors = it.badgeColors(), icon = it.icon())
            }
        }
    }
}
