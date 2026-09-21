package com.luxmap.feature.map.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import com.luxmap.core.theme.Dimens
import com.luxmap.core.theme.Spacing

// Summary row under the search bar (F12) — total poles, poles needing attention (dim/out), total
// surveyed routes. Not yet a Design System component (section 10 only lists
// Legend/LayerControl/ZoomControl for the map), so this reuses the same plain
// MaterialTheme.colorScheme.surface card look as MapLegend/MapLayerToggle instead of inventing a
// new chip style. Only "cần xử lý" is a shortcut into the FM-36 status filter (tap to filter the
// map to dim/out poles, tap again to clear) — the other 2 are informational totals only.
@Composable
fun MapKpiChipRow(
    poleCount: Int,
    needsAttentionCount: Int,
    routeCount: Int,
    needsAttentionActive: Boolean,
    onNeedsAttentionClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        KpiChip(text = "$poleCount cột")
        KpiChip(
            text = "$needsAttentionCount cần xử lý",
            active = needsAttentionActive,
            onClick = onNeedsAttentionClick,
        )
        KpiChip(text = "$routeCount tuyến")
    }
}

@Composable
private fun KpiChip(
    text: String,
    active: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val backgroundColor = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val contentColor = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val shape = RoundedCornerShape(Dimens.radiusPill)
    Box(
        modifier =
            Modifier
                .defaultMinSize(minHeight = Dimens.minTouchTarget)
                // Clip before clickable, or the press ripple is drawn as a gray rectangle
                // that sticks out past the rounded corners.
                .clip(shape)
                .background(backgroundColor, shape)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = Spacing.md, vertical = Spacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge, color = contentColor)
    }
}
