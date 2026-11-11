package com.luxmap.feature.map.data

// Single dataset for F12 (GIS operational map) — bundles poles and road segments together so
// the repository exposes one flow and the ViewModel does not have to zip 2 separate flows to
// build one UiState.
data class GisMapDataset(
    val poles: List<PoleMarker>,
    val segments: List<RoadSegmentLine>,
    // (lat, lng) to open the map camera on — null for FakeMapRepository and any other source
    // with no real centre to offer; MapScreen falls back to its existing hardcoded default.
    val center: Pair<Double, Double>? = null,
    // Real extent the center was computed from — null under the same conditions as center.
    // MapScreen uses this to fit the initial camera to the whole commune instead of a fixed
    // zoom, since a commune's real area varies a lot more than one hardcoded zoom can cover.
    val bbox: GisMapBbox? = null,
)

data class GisMapBbox(
    val minLng: Double,
    val minLat: Double,
    val maxLng: Double,
    val maxLat: Double,
)
