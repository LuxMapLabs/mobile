package com.luxmap.feature.map.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.luxmap.core.theme.AssetCondition
import com.luxmap.core.theme.BadgeColors
import com.luxmap.core.theme.Dimens
import com.luxmap.core.theme.Spacing
import com.luxmap.core.theme.badgeColors
import com.luxmap.core.theme.label

private val STATUS_FILTER_ORDER =
    listOf(AssetCondition.NORMAL, AssetCondition.DIM, AssetCondition.OUT, AssetCondition.UNKNOWN)

// Alpha applied to a chip's background/text when the whole group is disabled (F12: turning off
// "Cột đèn theo tình trạng" disables every status chip) — same alpha level already used elsewhere
// in this codebase for a disabled selector state.
private const val DISABLED_CHIP_ALPHA = 0.4f

// Status chips inside the "Hiển thị trên bản đồ" sheet (F12/FM-36). selectedStatuses is the set
// of statuses currently shown on the map — MapViewModel.statusFilter defaults to all 4, so every
// chip starts selected, unchecking one hides that status's markers.
// FlowRow (not Row) so 4 chips wrap onto a second line on a narrow screen instead of getting
// squeezed until their text breaks one character per line.
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MapStatusFilterRow(
    selectedStatuses: Set<AssetCondition>,
    onToggle: (AssetCondition) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val isDark = isSystemInDarkTheme()
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        STATUS_FILTER_ORDER.forEach { condition ->
            StatusFilterChip(
                label = condition.label(),
                selected = condition in selectedStatuses,
                selectedColors = condition.badgeColors(isDark),
                enabled = enabled,
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
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val chipAlpha = if (enabled) 1f else DISABLED_CHIP_ALPHA
    val backgroundColor = if (selected) selectedColors.background else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (selected) selectedColors.text else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier =
            Modifier
                .defaultMinSize(minHeight = Dimens.minTouchTarget)
                .clickable(enabled = enabled, onClick = onClick)
                .background(backgroundColor.copy(alpha = chipAlpha), RoundedCornerShape(Dimens.radiusPill))
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = contentColor.copy(alpha = chipAlpha),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible,
        )
    }
}
