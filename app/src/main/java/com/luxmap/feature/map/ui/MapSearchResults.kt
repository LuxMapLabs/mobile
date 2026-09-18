package com.luxmap.feature.map.ui

import com.luxmap.feature.map.data.PoleMarker
import com.luxmap.feature.map.data.RoadSegmentLine

// Matches for the current search query/target (F12/FM-36) — matched against the full dataset
// (MapUiState.Success.dataset), not the status-filtered one, so search can still find a pole the
// active filter is hiding.
data class MapSearchResults(
    val poles: List<PoleMarker> = emptyList(),
    val segments: List<RoadSegmentLine> = emptyList(),
)
