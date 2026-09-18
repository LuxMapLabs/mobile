package com.luxmap.feature.map.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.luxmap.core.theme.Dimens
import com.luxmap.core.theme.Spacing
import com.luxmap.core.theme.label
import com.luxmap.feature.map.data.PoleMarker
import com.luxmap.feature.map.data.RoadSegmentLine

private val RESULTS_LIST_MAX_HEIGHT = 240.dp

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
        items(results.poles, key = { it.poleId }) { pole ->
            ResultRow(title = pole.poleId, subtitle = pole.fixtureStatus.label(), onClick = { onPoleClick(pole) })
        }
        items(results.segments, key = { it.segmentId }) { segment ->
            ResultRow(title = segment.name, subtitle = segment.segmentId, onClick = { onSegmentClick(segment) })
        }
    }
}

@Composable
private fun ResultRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = Dimens.minTouchTarget)
                .clickable(onClick = onClick)
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
