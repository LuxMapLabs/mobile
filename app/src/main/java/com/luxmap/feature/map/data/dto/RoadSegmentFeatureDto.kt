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

// Đúng tên field trong luxmap_backend MapProperties.cs (GET /api/v1/map/segments).
// controller_node_ids replaces the old single controller_node_id (backend drift "BE-14 / IoT",
// a documented breaking change — the old field was always null, nothing real depended on it).
// length_m is a real double (declared length, not derived from geometry — can be fractional).
@Serializable
data class RoadSegmentPropertiesDto(
    @SerialName("segment_id") val segmentId: String,
    @SerialName("segment_name") val segmentName: String,
    @SerialName("road_class") val roadClass: String,
    @SerialName("length_m") val lengthM: Double,
    @SerialName("pole_count") val poleCount: Int,
    @SerialName("controller_node_ids") val controllerNodeIds: List<String> = emptyList(),
    @SerialName("has_active_segment_fault") val hasActiveSegmentFault: Boolean,
)
