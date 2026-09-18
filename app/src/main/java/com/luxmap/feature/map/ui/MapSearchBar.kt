package com.luxmap.feature.map.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.luxmap.core.theme.Dimens
import com.luxmap.core.theme.Spacing

// What the search query is matched against — pole_id, or segment_name/segment_id (both confirmed
// fields, see FM-35/FM-36). ATLAS stays a visible-but-disabled tab: the brief asked for search to
// also cover "atlas", but that field does not exist in API Contract v1.1 or the mock GeoJSON yet
// (FM-35 note) — do not wire it until WP2 confirms the real field/endpoint.
enum class MapSearchTarget { POLE, ROUTE, ATLAS }

// Single search entry point for F12 (replaces the old always-open layer-toggle card) — the filter
// icon opens a bottom sheet with layer/status filters instead of a floating card, per FM-36.
@Composable
fun MapSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    activeTarget: MapSearchTarget,
    onTargetChange: (MapSearchTarget) -> Unit,
    onFilterClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(Dimens.radiusMedium),
            placeholder = { Text(activeTarget.placeholder()) },
            leadingIcon = { Icon(imageVector = Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                // No dedicated "filter" icon exists in material-icons-core (same limit this file's
                // sibling MapScreen.kt already notes for Icons.Filled.Remove) — List stands in for
                // "map layers/filter", same substitution pattern already used in this codebase.
                IconButton(onClick = onFilterClick) {
                    Icon(imageVector = Icons.Filled.List, contentDescription = "Bộ lọc lớp bản đồ")
                }
            },
            colors =
                OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                ),
        )
        Row(
            modifier =
                Modifier
                    .padding(top = Spacing.xs)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Dimens.radiusMedium))
                    .padding(horizontal = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SearchTargetTab(
                label = "Cột",
                selected = activeTarget == MapSearchTarget.POLE,
                enabled = true,
                onClick = { onTargetChange(MapSearchTarget.POLE) },
            )
            SearchTargetTab(
                label = "Tuyến",
                selected = activeTarget == MapSearchTarget.ROUTE,
                enabled = true,
                onClick = { onTargetChange(MapSearchTarget.ROUTE) },
            )
            SearchTargetTab(label = "Atlas (sắp có)", selected = false, enabled = false, onClick = {})
        }
    }
}

private fun MapSearchTarget.placeholder(): String =
    when (this) {
        MapSearchTarget.POLE -> "Tìm theo mã cột (VD: POLE-0047)"
        MapSearchTarget.ROUTE -> "Tìm theo tên tuyến"
        MapSearchTarget.ATLAS -> ""
    }

@Composable
private fun SearchTargetTab(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val contentColor =
        when {
            !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            selected -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    Box(
        modifier =
            Modifier
                .defaultMinSize(minHeight = Dimens.minTouchTarget)
                .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = Spacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = contentColor)
    }
}
