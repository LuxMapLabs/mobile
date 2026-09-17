package com.luxmap.feature.map.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Field/endpoint theo Contract v1.1 §2.2 (GET /api/v1/poles/{pole_id}) — khớp
// docs/mock-pole-detail.json, không tự đặt tên khác.
@Serializable
data class PoleDetailDto(
    @SerialName("pole_id") val poleId: String,
    @SerialName("segment_id") val segmentId: String,
    @SerialName("segment_name") val segmentName: String,
    @SerialName("commune_id") val communeId: String,
    val location: PoleDetailLocationDto,
    val fixture: PoleDetailFixtureDto,
    @SerialName("current_status") val currentStatus: PoleDetailCurrentStatusDto,
    // null when the pole has no IoT node — most poles, per the contract note.
    @SerialName("iot_node") val iotNode: PoleDetailIotNodeDto? = null,
    @SerialName("luminance_baseline") val luminanceBaseline: PoleDetailLuminanceBaselineDto,
    @SerialName("luminance_history") val luminanceHistory: List<PoleDetailLuminancePointDto> = emptyList(),
    // Only present when the pole has an IoT node.
    @SerialName("runtime_history") val runtimeHistory: List<PoleDetailRuntimePointDto> = emptyList(),
    @SerialName("open_faults") val openFaults: List<PoleDetailFaultDto> = emptyList(),
    @SerialName("recent_frames") val recentFrames: List<PoleDetailFrameDto> = emptyList(),
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
    @SerialName("warranty_expiry") val warrantyExpiry: String,
    val supplier: String,
)

@Serializable
data class PoleDetailCurrentStatusDto(
    @SerialName("fixture_status") val fixtureStatus: String,
    @SerialName("status_confidence") val statusConfidence: Double,
    @SerialName("determined_at") val determinedAt: String,
    // "cv" or "iot" — which channel produced this status.
    @SerialName("source_channel") val sourceChannel: String,
)

@Serializable
data class PoleDetailIotNodeDto(
    @SerialName("node_id") val nodeId: String,
    @SerialName("node_status") val nodeStatus: String,
    @SerialName("last_report_at") val lastReportAt: String,
)

@Serializable
data class PoleDetailLuminanceBaselineDto(
    @SerialName("baseline_value") val baselineValue: Double,
    @SerialName("baseline_window_nights") val baselineWindowNights: Int,
    @SerialName("dim_threshold_ratio") val dimThresholdRatio: Double,
    @SerialName("out_threshold_ratio") val outThresholdRatio: Double,
    @SerialName("computed_at") val computedAt: String,
)

@Serializable
data class PoleDetailLuminancePointDto(
    @SerialName("observed_at") val observedAt: String,
    @SerialName("sweep_id") val sweepId: String,
    @SerialName("normalized_luminance") val normalizedLuminance: Double,
    @SerialName("baseline_ratio") val baselineRatio: Double,
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
    @SerialName("priority_score") val priorityScore: Double,
)

@Serializable
data class PoleDetailFrameDto(
    @SerialName("frame_id") val frameId: String,
    @SerialName("sweep_id") val sweepId: String,
    @SerialName("captured_at") val capturedAt: String,
    @SerialName("thumbnail_url") val thumbnailUrl: String,
    @SerialName("distance_m") val distanceM: Double,
    @SerialName("heading_deg") val headingDeg: Double,
)
