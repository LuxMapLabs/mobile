package com.luxmap.feature.map.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Đúng tên field trong Contract v1.1 §2.1 (GET /api/v1/poles) — xác nhận khớp
// docs/mock-poles.geojson người dùng cung cấp, không tự đặt tên khác.
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

@Serializable
data class PolePropertiesDto(
    @SerialName("pole_id") val poleId: String,
    @SerialName("segment_id") val segmentId: String,
    @SerialName("fixture_status") val fixtureStatus: String,
    @SerialName("status_confidence") val statusConfidence: Double? = null,
    @SerialName("power_source") val powerSource: String,
    @SerialName("fixture_type") val fixtureType: String,
    @SerialName("lamp_watt") val lampWatt: Int,
    @SerialName("install_date") val installDate: String,
    @SerialName("warranty_expiry") val warrantyExpiry: String,
    @SerialName("commune_id") val communeId: String,
    @SerialName("last_seen_at") val lastSeenAt: String,
    @SerialName("last_sweep_id") val lastSweepId: String,
    @SerialName("open_fault_count") val openFaultCount: Int,
    @SerialName("has_iot_node") val hasIotNode: Boolean,
    @SerialName("near_sensitive_poi") val nearSensitivePoi: Boolean,
)
