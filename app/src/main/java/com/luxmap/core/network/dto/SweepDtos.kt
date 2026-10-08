package com.luxmap.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CreateSweepRequestDto(
    @SerialName("work_order_id") val workOrderId: String,
    @SerialName("client_op_id") val clientOpId: String,
    @SerialName("boot_session_id") val bootSessionId: String,
    @SerialName("elapsed_anchor_ns") val elapsedAnchorNs: String,
    @SerialName("utc_anchor") val utcAnchor: String,
    @SerialName("utc_uncertainty_ms") val utcUncertaintyMs: Double,
    @SerialName("data_source") val dataSource: String,
    @SerialName("started_elapsed_ns") val startedElapsedNs: String,
)

@Serializable
data class SweepResponseDto(
    @SerialName("sweep_id") val sweepId: String,
)

@Serializable
data class ClipManifestDto(
    @SerialName("clip_no") val clipNo: Int,
    val sha256: String,
)

@Serializable
data class SweepManifestDto(
    val clips: List<ClipManifestDto>,
    @SerialName("gps_hash") val gpsHash: String,
    @SerialName("lux_hash") val luxHash: String,
    @SerialName("config_hash") val configHash: String,
)

@Serializable
data class SubmitSweepRequestDto(
    @SerialName("client_op_id") val clientOpId: String,
    @SerialName("ended_elapsed_ns") val endedElapsedNs: String,
    val manifest: SweepManifestDto,
)

@Serializable
data class SurveyClipResponseDto(
    @SerialName("clip_no") val clipNo: Int,
    val sha256: String,
)

@Serializable
data class SurveyRawResponseDto(
    val kind: String,
    val sha256: String,
)
