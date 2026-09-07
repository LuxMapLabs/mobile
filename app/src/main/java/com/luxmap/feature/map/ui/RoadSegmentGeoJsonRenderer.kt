package com.luxmap.feature.map.ui

import com.luxmap.feature.map.data.RoadSegmentLine
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// Chuyển List<RoadSegmentLine> (domain model) thành chuỗi GeoJSON LineString cho GeoJsonSource
// của MapLibre — chỉ giữ segment_id (dùng làm key nếu cần tra cứu sau này), không phải bản
// sao đầy đủ của RoadSegmentFeatureDto (xem lý do ở RoadSegmentLine.kt).
private val renderJson = Json { encodeDefaults = true }

fun List<RoadSegmentLine>.toGeoJson(): String =
    renderJson.encodeToString(
        RenderSegmentFeatureCollection.serializer(),
        RenderSegmentFeatureCollection(features = map { it.toRenderFeature() }),
    )

private fun RoadSegmentLine.toRenderFeature() =
    RenderSegmentFeature(
        geometry = RenderSegmentGeometry(coordinates = coordinates.map { (lng, lat) -> listOf(lng, lat) }),
        properties = RenderSegmentProperties(segmentId = segmentId),
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
)
