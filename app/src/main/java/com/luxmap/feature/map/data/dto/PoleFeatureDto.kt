package com.luxmap.feature.map.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Đúng tên field trong luxmap_backend MapProperties.cs (GET /api/v1/map/poles).
@Serializable
data class PoleFeatureCollectionDto(
    val type: String,
    val features: List<PoleFeatureDto>,
)

@Serializable
data class PoleFeatureDto(
    val type: String,
    val geometry: PoleGeometryDto,
    val properties: PolePropertiesDto,
)

@Serializable
data class PoleGeometryDto(
    val type: String,
    // [lng, lat] — đúng thứ tự GeoJSON, không phải [lat, lng]
    val coordinates: List<Double>,
)

// Đúng tên field trong luxmap_backend/src/LuxMap.Modules.Map/Features/MapProperties.cs
// (GET /api/v1/map/poles — moved under /map by backend drift "MAP-1", NOT /api/v1/poles).
// Nullable fields below match the real record exactly: a pole can exist with no segment
// ("POLE-SEGMENT-NULL" backend work) and no lamp installed yet.
@Serializable
data class PolePropertiesDto(
    @SerialName("pole_id") val poleId: String,
    @SerialName("segment_id") val segmentId: String?,
    @SerialName("fixture_status") val fixtureStatus: String,
    @SerialName("status_confidence") val statusConfidence: Double? = null,
    @SerialName("power_source") val powerSource: String? = null,
    @SerialName("fixture_type") val fixtureType: String? = null,
    @SerialName("lamp_watt") val lampWatt: Int? = null,
    @SerialName("lamp_count") val lampCount: Int? = null,
    @SerialName("install_date") val installDate: String? = null,
    @SerialName("warranty_expiry") val warrantyExpiry: String? = null,
    @SerialName("warranty_status") val warrantyStatus: String,
    @SerialName("commune_id") val communeId: String,
    @SerialName("last_seen_at") val lastSeenAt: String? = null,
    @SerialName("last_sweep_id") val lastSweepId: String? = null,
    @SerialName("open_fault_count") val openFaultCount: Int,
    @SerialName("has_iot_node") val hasIotNode: Boolean,
    @SerialName("near_sensitive_poi") val nearSensitivePoi: Boolean,
)
