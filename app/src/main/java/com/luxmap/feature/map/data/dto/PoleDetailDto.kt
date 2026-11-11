package com.luxmap.feature.map.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// GET /api/v1/map/poles/{pole_id} (luxmap_backend MapController.cs/PoleMapDetail.cs) - confirmed
// directly from source, not the older mock-pole-detail.json shape this file used to follow.
@Serializable
data class PoleDetailDto(
    @SerialName("pole_id") val poleId: String,
    @SerialName("segment_id") val segmentId: String? = null,
    @SerialName("segment_name") val segmentName: String? = null,
    @SerialName("commune_id") val communeId: String,
    // Null for a pole known from the books that has no position yet (POLE-GEOM-NULL).
    val location: PoleDetailLocationDto? = null,
    // Null when the pole has no active fixture installed.
    val fixture: PoleDetailFixtureDto? = null,
    @SerialName("current_status") val currentStatus: PoleDetailCurrentStatusDto,
    // null when the pole has no IoT node - always null in practice (IoT sits at the cabinet).
    @SerialName("iot_node") val iotNode: PoleDetailIotNodeDto? = null,
    @SerialName("luminance_history") val luminanceHistory: List<PoleDetailLuminancePointDto> = emptyList(),
    // Always empty in practice - runtime is measured per feeder, not per pole.
    @SerialName("runtime_history") val runtimeHistory: List<PoleDetailRuntimePointDto> = emptyList(),
    @SerialName("open_faults") val openFaults: List<PoleDetailFaultDto> = emptyList(),
    @SerialName("recent_frames") val recentFrames: List<PoleDetailFrameDto> = emptyList(),
    val note: String? = null,
)

@Serializable
data class PoleDetailLocationDto(
    val lat: Double,
    val lng: Double,
)

@Serializable
data class PoleDetailFixtureDto(
    @SerialName("fixture_type") val fixtureType: String,
    @SerialName("power_source") val powerSource: String,
    @SerialName("lamp_watt") val lampWatt: Int,
    @SerialName("install_date") val installDate: String,
    @SerialName("warranty_expiry") val warrantyExpiry: String? = null,
)

@Serializable
data class PoleDetailCurrentStatusDto(
    @SerialName("fixture_status") val fixtureStatus: String,
    // Null when the pole has never been observed (no published sweep yet).
    @SerialName("status_confidence") val statusConfidence: Double? = null,
    @SerialName("determined_at") val determinedAt: String? = null,
    @SerialName("source_channel") val sourceChannel: String? = null,
)

@Serializable
data class PoleDetailIotNodeDto(
    @SerialName("node_id") val nodeId: String,
    @SerialName("node_status") val nodeStatus: String,
    @SerialName("last_report_at") val lastReportAt: String,
)

@Serializable
data class PoleDetailLuminancePointDto(
    @SerialName("observed_at") val observedAt: String,
    // Null when the pole had no usable baseline or was not observed at this point.
    @SerialName("baseline_ratio") val baselineRatio: Double? = null,
    @SerialName("classified_as") val classifiedAs: String,
)

@Serializable
data class PoleDetailRuntimePointDto(
    @SerialName("night_of") val nightOf: String,
    @SerialName("runtime_hours") val runtimeHours: Double,
    @SerialName("on_at") val onAt: String? = null,
    @SerialName("off_at") val offAt: String? = null,
    val source: String,
)

@Serializable
data class PoleDetailFaultDto(
    @SerialName("fault_id") val faultId: String,
    @SerialName("fault_type") val faultType: String,
    val severity: String,
    @SerialName("fault_status") val faultStatus: String,
)

@Serializable
data class PoleDetailFrameDto(
    @SerialName("frame_id") val frameId: String,
    @SerialName("captured_at") val capturedAt: String,
    @SerialName("thumbnail_url") val thumbnailUrl: String,
)
