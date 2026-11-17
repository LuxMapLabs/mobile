package com.luxmap.feature.map.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.luxmap.core.theme.Spacing
import com.luxmap.core.ui.components.LuxFilterChip

@Composable
fun MapKpiChipRow(
    poleCount: Int,
    needsAttentionCount: Int,
    routeCount: Int,
    needsAttentionActive: Boolean,
    onNeedsAttentionClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InfoChip(text = "$poleCount cột")
        LuxFilterChip(
            label = "$needsAttentionCount cần xử lý",
            selected = needsAttentionActive,
            onClick = onNeedsAttentionClick,
        )
        InfoChip(text = "$routeCount tuyến")
    }
}

@Composable
private fun InfoChip(text: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 3.dp,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.heightIn(min = 32.dp).padding(horizontal = Spacing.md, vertical = Spacing.xs),
        )
    }
}
