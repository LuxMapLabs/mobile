package com.luxmap.feature.map.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Đúng tên field trong mock-segments.geojson (đồng bộ schema với luxmap-web/src/data/mock-segments.geo.json)
// — không tự đặt tên khác.
@Serializable
data class RoadSegmentFeatureCollectionDto(
    val type: String,
    val features: List<RoadSegmentFeatureDto>,
)

@Serializable
data class RoadSegmentFeatureDto(
    val type: String,
    val geometry: RoadSegmentGeometryDto,
    val properties: RoadSegmentPropertiesDto,
)

@Serializable
data class RoadSegmentGeometryDto(
    val type: String,
    // list of [lng, lat] — đúng thứ tự GeoJSON LineString, không phải [lat, lng]
    val coordinates: List<List<Double>>,
)

@Serializable
data class RoadSegmentPropertiesDto(
    @SerialName("segment_id") val segmentId: String,
    @SerialName("segment_name") val segmentName: String,
    @SerialName("road_class") val roadClass: String,
    @SerialName("length_m") val lengthM: Int,
    @SerialName("pole_count") val poleCount: Int,
    @SerialName("controller_node_id") val controllerNodeId: String,
    @SerialName("has_active_segment_fault") val hasActiveSegmentFault: Boolean,
)
