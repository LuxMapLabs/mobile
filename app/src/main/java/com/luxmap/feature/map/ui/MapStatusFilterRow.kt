package com.luxmap.feature.map.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.luxmap.core.theme.AssetCondition
import com.luxmap.core.theme.BadgeColors
import com.luxmap.core.theme.Dimens
import com.luxmap.core.theme.Spacing
import com.luxmap.core.theme.badgeColors
import com.luxmap.core.theme.label

private val STATUS_FILTER_ORDER =
    listOf(AssetCondition.NORMAL, AssetCondition.DIM, AssetCondition.OUT, AssetCondition.UNKNOWN)

// Status filter chips inside the layer-filter bottom sheet (F12/FM-36: "Lọc theo trạng thái
// đèn"). Empty selectedStatuses means "no filter, show all" — same meaning MapViewModel uses, so
// the caller can pass MapViewModel.statusFilter straight through.
@Composable
fun MapStatusFilterRow(
    selectedStatuses: Set<AssetCondition>,
    onToggle: (AssetCondition) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        STATUS_FILTER_ORDER.forEach { condition ->
            StatusFilterChip(
                label = condition.label(),
                selected = condition in selectedStatuses,
                selectedColors = condition.badgeColors(isDark),
                onClick = { onToggle(condition) },
            )
        }
    }
}

@Composable
private fun StatusFilterChip(
    label: String,
    selected: Boolean,
    selectedColors: BadgeColors,
    onClick: () -> Unit,
) {
    val backgroundColor = if (selected) selectedColors.background else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (selected) selectedColors.text else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier =
            Modifier
                .defaultMinSize(minHeight = Dimens.minTouchTarget)
                .clickable(onClick = onClick)
                .background(backgroundColor, RoundedCornerShape(Dimens.radiusPill))
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = contentColor)
    }
}
