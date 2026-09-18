package com.luxmap.feature.map.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.luxmap.core.theme.Dimens
import com.luxmap.core.theme.Spacing

// Read-only summary row under the search bar (F12) — total poles, poles needing attention
// (dim/out), total surveyed routes. Not yet a Design System component (section 10 only lists
// Legend/LayerControl/ZoomControl for the map), so this reuses the same plain
// MaterialTheme.colorScheme.surface card look as MapLegend/MapLayerToggle instead of inventing a
// new chip style. Not clickable yet — turning "needsAttentionCount" into a status-filter shortcut
// is wired in a later FM-36 step, once the status filter itself exists.
@Composable
fun MapKpiChipRow(
    poleCount: Int,
    needsAttentionCount: Int,
    routeCount: Int,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        KpiChip(text = "$poleCount cột")
        KpiChip(text = "$needsAttentionCount cần xử lý")
        KpiChip(text = "$routeCount tuyến")
    }
}

@Composable
private fun KpiChip(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier =
            Modifier
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Dimens.radiusPill))
                .padding(horizontal = Spacing.md, vertical = Spacing.xs),
    )
}
