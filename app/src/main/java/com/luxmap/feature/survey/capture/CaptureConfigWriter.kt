package com.luxmap.feature.survey.capture

data class CaptureConfig(
    val bootSessionId: String,
    val elapsedAnchorNs: Long,
    val utcAnchorIso: String,
    val utcUncertaintyMs: Int,
    val phoneModel: String,
    val cameraId: String,
    val appVersion: String,
    // Actually-measured values from the last CaptureResult (VideoCaptureSession.stop()), not the
    // requested config — BE measured a real mismatch between the two in a reviewed sample.
    val iso: Int,
    val exposureTimeNs: Long,
    val aperture: Double,
    val fps: Int,
    val focusDistanceDiopters: Float,
    val whiteBalanceCctK: Int,
    val widthPx: Int,
    val heightPx: Int,
    val orientation: String,
    // Tạm để là 1 per BE — no registry exists yet to resolve a real ID (spec: survey-ingest-p2a.md §Tạo phiên)
    val profileId: Int,
    val moduleFirmwareVersionId: Int,
)

object CaptureConfigWriter {
    fun toJson(config: CaptureConfig): String =
        """
        {"schema_version":1,"boot_session_id":"${config.bootSessionId}",
        "profile_id":${config.profileId},"module_firmware_version_id":${config.moduleFirmwareVersionId},
        "phone_model":"${config.phoneModel}","camera_id":"${config.cameraId}","app_version":"${config.appVersion}",
        "sensor_timestamp_source":"REALTIME",
        "elapsed_anchor_ns":"${config.elapsedAnchorNs}","utc_anchor":"${config.utcAnchorIso}",
        "utc_uncertainty_ms":${config.utcUncertaintyMs},
        "camera":{"iso":${config.iso},"exposure_time_ns":"${config.exposureTimeNs}","aperture":${config.aperture},
        "fps":${config.fps},"focus_mode":"manual","focus_distance":${config.focusDistanceDiopters},
        "white_balance_mode":"manual","white_balance_value":{"cct_k":${config.whiteBalanceCctK}},
        "resolution":{"width":${config.widthPx},"height":${config.heightPx}},
        "ae_enabled":false,"eis_enabled":false,"hdr_enabled":false,"night_mode_enabled":false},
        "mount":{"camera_side":"$MOUNT_CAMERA_SIDE","mount_height_m":$MOUNT_HEIGHT_M,
        "angle_deg":$MOUNT_ANGLE_DEG,"sensor_position":"$MOUNT_SENSOR_POSITION"},
        "orientation":"${config.orientation}"}
        """.trimIndent().replace("\n", "").replace(Regex("""\s+"""), " ").trim()

    // Fixed mount values until WP4/project owner confirms per-vehicle mount measurement is needed —
    // every survey rig today mounts the same way (handlebar, front-facing). Source: mobile.pdf
    // (leader, 2026-10-08).
    private const val MOUNT_CAMERA_SIDE = "front"
    private const val MOUNT_HEIGHT_M = 1.1
    private const val MOUNT_ANGLE_DEG = 10
    private const val MOUNT_SENSOR_POSITION = "handlebar_top"
}
