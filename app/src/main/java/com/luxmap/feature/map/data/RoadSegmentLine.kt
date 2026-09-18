package com.luxmap.feature.map.data

import com.luxmap.feature.map.data.dto.RoadSegmentFeatureDto

// Domain model trimmed from RoadSegmentFeatureDto, keeps only the UI fields F12 needs (draw the
// "surveyed route" line on the map, colored by grid fault to match Web GIS, plus the summary
// fields the route quick-view bottom sheet shows) — does NOT keep controllerNodeId, that field is
// only for the grid detail panel on Web GIS (Maintenance Engineer role), F12 mobile has no such
// panel so it's not needed.
data class RoadSegmentLine(
    val segmentId: String,
    val name: String,
    val hasActiveSegmentFault: Boolean,
    val lengthM: Int,
    val poleCount: Int,
    // [lng, lat] per point, in GeoJSON order
    val coordinates: List<Pair<Double, Double>>,
)

fun RoadSegmentFeatureDto.toRoadSegmentLine(): RoadSegmentLine =
    RoadSegmentLine(
        segmentId = properties.segmentId,
        name = properties.segmentName,
        hasActiveSegmentFault = properties.hasActiveSegmentFault,
        lengthM = properties.lengthM,
        poleCount = properties.poleCount,
        coordinates = geometry.coordinates.map { (lng, lat) -> lng to lat },
    )
