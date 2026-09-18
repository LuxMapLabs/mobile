package com.luxmap.feature.map.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.luxmap.core.theme.Dimens
import com.luxmap.core.theme.Spacing
import com.luxmap.core.theme.label
import com.luxmap.feature.map.data.PoleMarker
import com.luxmap.feature.map.data.RoadSegmentLine

private val RESULTS_LIST_MAX_HEIGHT = 240.dp
private val RESULT_ROW_ICON_SIZE = 18.dp

// Result list under the search bar (F12/FM-36) — only rendered while the query is non-blank, so
// no extra UI opens until the user actually types something (same "no bottom sheet until you act"
// rule the brief asks for on marker/route taps). Tapping a row is "chọn" from the brief: zoom to
// a pole, or fit camera + open the route quick-view sheet — same behavior as tapping it directly
// on the map.
@Composable
fun MapSearchResultsList(
    results: MapSearchResults,
    onPoleClick: (PoleMarker) -> Unit,
    onSegmentClick: (RoadSegmentLine) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (results.poles.isEmpty() && results.segments.isEmpty()) {
        Text(
            text = "Không tìm thấy kết quả",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier =
                modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Dimens.radiusMedium))
                    .padding(Spacing.md),
        )
        return
    }

    LazyColumn(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(max = RESULTS_LIST_MAX_HEIGHT)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Dimens.radiusMedium)),
    ) {
        // No tab to pick "pole" or "route" anymore (FM-36) — both groups show together, poles
        // first, each with its own small header so results still read as two kinds, not one
        // mixed pile. Header only renders when its group is non-empty.
        if (results.poles.isNotEmpty()) {
            item { ResultGroupHeader(label = "Cột") }
            items(results.poles, key = { it.poleId }) { pole ->
                ResultRow(
                    icon = Icons.Filled.Lightbulb,
                    title = pole.poleId,
                    subtitle = pole.fixtureStatus.label(),
                    onClick = { onPoleClick(pole) },
                )
            }
        }
        if (results.segments.isNotEmpty()) {
            item { ResultGroupHeader(label = "Tuyến") }
            items(results.segments, key = { it.segmentId }) { segment ->
                ResultRow(
                    icon = Icons.Filled.Route,
                    title = segment.name,
                    subtitle = segment.segmentId,
                    onClick = { onSegmentClick(segment) },
                )
            }
        }
    }
}

@Composable
private fun ResultGroupHeader(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.xs),
    )
}

@Composable
private fun ResultRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = Dimens.minTouchTarget)
                .clickable(onClick = onClick)
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(RESULT_ROW_ICON_SIZE),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
