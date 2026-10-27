package com.luxmap.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Help
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.luxmap.core.theme.AssetCondition
import com.luxmap.core.theme.BadgeTextStyle
import com.luxmap.core.theme.Radius
import com.luxmap.core.theme.Spacing
import com.luxmap.core.theme.badgeColors

private data class OutcomeOption(
    val value: String,
    val label: String,
    val icon: ImageVector,
    val condition: AssetCondition,
)

private val OUTCOME_OPTIONS =
    listOf(
        OutcomeOption("fault_present", "Có sự cố", Icons.Filled.Error, AssetCondition.OUT),
        OutcomeOption("fault_absent", "Không có", Icons.Filled.CheckCircle, AssetCondition.NORMAL),
        OutcomeOption("inconclusive", "Chưa xác định", Icons.Filled.Help, AssetCondition.DIM),
    )

@Composable
fun OutcomeSegment(
    selected: String?,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        OUTCOME_OPTIONS.forEach { option ->
            val isSelected = selected == option.value
            val colors = option.condition.badgeColors(isDark = false)
            OutcomeTile(
                label = option.label,
                icon = option.icon,
                selected = isSelected,
                background = if (isSelected) colors.background else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (isSelected) colors.text else MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = { onSelected(option.value) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private val OutcomeTileMinHeight = 60.dp

@Composable
private fun OutcomeTile(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    background: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .defaultMinSize(minHeight = OutcomeTileMinHeight)
                .clip(RoundedCornerShape(Radius.chip))
                .background(background, RoundedCornerShape(Radius.chip))
                .border(if (selected) 2.dp else 0.dp, contentColor, RoundedCornerShape(Radius.chip))
                .selectable(selected = selected, onClick = onClick)
                .padding(horizontal = Spacing.xs, vertical = Spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(20.dp))
        Text(
            text = label,
            style = BadgeTextStyle,
            color = contentColor,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}
