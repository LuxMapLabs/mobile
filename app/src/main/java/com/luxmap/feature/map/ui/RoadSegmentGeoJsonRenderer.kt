package com.luxmap.feature.map.ui

import com.luxmap.feature.map.data.RoadSegmentLine
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// Turn List<RoadSegmentLine> (domain model) into a GeoJSON LineString string for MapLibre's
// GeoJsonSource — keep segment_id (lookup key) and has_active_segment_fault (route color, see
// buildRoadSegmentsLineLayer in MapScreen.kt), not a full copy of RoadSegmentFeatureDto (see the
// reason in RoadSegmentLine.kt).
private val renderJson = Json { encodeDefaults = true }

fun List<RoadSegmentLine>.toGeoJson(): String =
    renderJson.encodeToString(
        RenderSegmentFeatureCollection.serializer(),
        RenderSegmentFeatureCollection(features = map { it.toRenderFeature() }),
    )

private fun RoadSegmentLine.toRenderFeature() =
    RenderSegmentFeature(
        geometry = RenderSegmentGeometry(coordinates = coordinates.map { (lng, lat) -> listOf(lng, lat) }),
        properties =
            RenderSegmentProperties(
                segmentId = segmentId,
                hasActiveSegmentFault = hasActiveSegmentFault,
            ),
    )

@Serializable
private data class RenderSegmentFeatureCollection(
    val type: String = "FeatureCollection",
    val features: List<RenderSegmentFeature>,
)

@Serializable
private data class RenderSegmentFeature(
    val type: String = "Feature",
    val geometry: RenderSegmentGeometry,
    val properties: RenderSegmentProperties,
)

@Serializable
private data class RenderSegmentGeometry(
    val type: String = "LineString",
    val coordinates: List<List<Double>>,
)

@Serializable
private data class RenderSegmentProperties(
    @SerialName("segment_id") val segmentId: String,
    @SerialName("has_active_segment_fault") val hasActiveSegmentFault: Boolean,
)
