package com.luxmap.feature.map.data

// Single dataset for F12 (GIS operational map) — bundles poles and road segments together so
// the repository exposes one flow and the ViewModel does not have to zip 2 separate flows to
// build one UiState.
data class GisMapDataset(
    val poles: List<PoleMarker>,
    val segments: List<RoadSegmentLine>,
)
