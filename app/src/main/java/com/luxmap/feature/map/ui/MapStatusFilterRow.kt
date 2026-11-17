package com.luxmap.feature.map.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.luxmap.core.theme.AssetCondition
import com.luxmap.core.theme.Spacing
import com.luxmap.core.ui.components.LuxFilterChip

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
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        MAP_STATUS_ORDER.forEach { condition ->
            LuxFilterChip(
                label = condition.mapLabel(),
                selected = condition in selectedStatuses,
                onClick = { onToggle(condition) },
                enabled = enabled,
            )
        }
    }
}
