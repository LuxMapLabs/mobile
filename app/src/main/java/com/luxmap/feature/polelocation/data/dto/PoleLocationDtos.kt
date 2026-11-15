package com.luxmap.feature.polelocation.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AssetLocationDto(
    val lat: Double,
    val lng: Double,
)

// GET /api/v1/assets/poles?missing_location=true - only the fields this feature's list needs;
// the real response carries many more (photo_count, feeder_id, active_fixture...), ignored.
@Serializable
data class PoleMissingLocationItemDto(
    @SerialName("pole_id") val poleId: String,
    @SerialName("external_ref") val externalRef: String? = null,
    @SerialName("segment_id") val segmentId: String? = null,
    @SerialName("commune_name") val communeName: String? = null,
    val location: AssetLocationDto? = null,
    val note: String? = null,
)

@Serializable
data class SetPoleLocationRequestDto(
    val lat: Double,
    val lng: Double,
)

@Serializable
data class PoleLocationResponseDto(
    @SerialName("pole_id") val poleId: String,
    val lat: Double,
    val lng: Double,
    @SerialName("moved_m") val movedM: Double? = null,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("updated_by") val updatedBy: String? = null,
    @SerialName("updated_by_name") val updatedByName: String? = null,
)
