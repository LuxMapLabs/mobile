package com.luxmap.feature.survey.capture

// Values (except sensor/exposure numbers, which come from the real applied CaptureResult in
// Task 17a) are the Task 2 spike's findings, not invented here. sensorTimestampSource is always
// "REALTIME" because CheckSurveyReadinessUseCase (Task 11) already hard-blocked any device that
// is not.
data class CaptureConfig(
    val utcAnchorIso: String,
    val elapsedAnchorNs: Long,
    val resolution: String,
    val fps: Int,
    val isoSensitivity: Int,
    val shutterNs: Long,
    val frameDurationNs: Long,
    val codec: String,
    val bitrateBps: Int,
    val keyframeIntervalS: Int,
    val segmentDurationS: Int,
    val cameraManufacturer: String,
    val cameraModel: String,
    val cameraId: String,
    val appVersion: String,
    val luxModuleId: String,
    val luxModuleFirmware: String,
)

object CaptureConfigWriter {
    // schema_version is a JSON FIELD here, unlike the .ndjson files' header line (spec §8) —
    // capture_config.json is a single object, not a line-delimited log.
    fun toJson(config: CaptureConfig): String =
        """
        {"schema_version":"v0",
        "utc_elapsed_anchor":{"utc_iso":"${config.utcAnchorIso}","elapsed_realtime_ns":${config.elapsedAnchorNs}},
        "camera":{"resolution":"${config.resolution}","fps":${config.fps},"iso":${config.isoSensitivity},
        "shutter_ns":${config.shutterNs},"frame_duration_ns":${config.frameDurationNs},"codec":"${config.codec}",
        "bitrate_bps":${config.bitrateBps},"keyframe_interval_s":${config.keyframeIntervalS},
        "af_locked":true,"eis_disabled":true,"hdr_disabled":true,"night_mode_disabled":true,
        "sensor_timestamp_source":"REALTIME"},
        "segment_duration_s":${config.segmentDurationS},
        "device":{"manufacturer":"${config.cameraManufacturer}","model":"${config.cameraModel}","camera_id":"${config.cameraId}"},
        "app_version":"${config.appVersion}","lux_module_id":"${config.luxModuleId}","lux_module_firmware":"${config.luxModuleFirmware}"}
        """.trimIndent().replace("\n", "")
}
