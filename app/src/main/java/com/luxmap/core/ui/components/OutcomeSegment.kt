package com.luxmap.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.luxmap.core.theme.AssetCondition
import com.luxmap.core.theme.BadgeTextStyle
import com.luxmap.core.theme.Radius
import com.luxmap.core.theme.Spacing
import com.luxmap.core.theme.badgeColors

// (wire value, label, which AssetCondition pair supplies the selected dot/text color) - reuses
// the existing light-theme OUT/NORMAL/DIM text colors instead of inventing a new trio, since
// this chip tray always sits on the light content sheet, never on a dark hero.
private val OUTCOME_OPTIONS =
    listOf(
        Triple("fault_present", "Có sự cố", AssetCondition.OUT),
        Triple("fault_absent", "Không có", AssetCondition.NORMAL),
        Triple("inconclusive", "Chưa xác định", AssetCondition.DIM),
    )

// 3 equal chips in a gray tray, matching the F09 redesign mockup - replaces the earlier plain
// M3 SegmentedButton look (black-on-white active state, no color coding per outcome).
@Composable
fun OutcomeSegment(
    selected: String?,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(Radius.chip))
                .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        OUTCOME_OPTIONS.forEach { (value, label, condition) ->
            val isSelected = selected == value
            val activeColor = condition.badgeColors(isDark = false).text
            OutcomeChip(
                label = label,
                selected = isSelected,
                dotColor = if (isSelected) activeColor else MaterialTheme.colorScheme.outline,
                contentColor = if (isSelected) activeColor else MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = { onSelected(value) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }
}

private val OutcomeChipMinHeight = 44.dp

@Composable
private fun OutcomeChip(
    label: String,
    selected: Boolean,
    dotColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .defaultMinSize(minHeight = OutcomeChipMinHeight)
                .shadow(
                    elevation = if (selected) 1.dp else 0.dp,
                    shape = RoundedCornerShape(Radius.chip),
                    clip = false,
                )
                // Without this, the clickable ripple below draws as a plain rectangle past the
                // rounded background on tap - this clips both the fill and the ripple to the
                // chip's real rounded shape.
                .clip(RoundedCornerShape(Radius.chip))
                .background(if (selected) Color.White else Color.Transparent, RoundedCornerShape(Radius.chip))
                .clickable(onClick = onClick)
                .padding(horizontal = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
    ) {
        Box(modifier = Modifier.size(8.dp).background(dotColor, CircleShape))
        Text(text = label, style = BadgeTextStyle, color = contentColor)
    }
}
