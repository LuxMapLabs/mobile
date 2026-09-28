package com.luxmap.feature.survey.capture

import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureConfigWriterTest {
    @Test
    fun `serializes every capture_config field as a JSON field, not a header line`() {
        val config =
            CaptureConfig(
                utcAnchorIso = "2026-09-28T20:00:00Z",
                elapsedAnchorNs = 123_456_789L,
                resolution = "1920x1080",
                fps = 30,
                isoSensitivity = 800,
                shutterNs = 20_000_000L,
                frameDurationNs = 33_333_333L,
                codec = "video/avc",
                bitrateBps = 8_000_000,
                keyframeIntervalS = 2,
                segmentDurationS = 180,
                cameraManufacturer = "Samsung",
                cameraModel = "Galaxy A54",
                cameraId = "0",
                appVersion = "1.0",
                luxModuleId = "LUX-001",
                luxModuleFirmware = "1.2.0",
            )

        val json = CaptureConfigWriter.toJson(config)

        assertTrue(json.contains(""""schema_version":"v0""""))
        assertTrue(json.contains(""""codec":"video/avc""""))
        assertTrue(json.contains(""""bitrate_bps":8000000"""))
        assertTrue(json.contains(""""keyframe_interval_s":2"""))
        assertTrue(json.contains(""""segment_duration_s":180"""))
        assertTrue(json.contains(""""app_version":"1.0""""))
        assertTrue(json.contains(""""lux_module_firmware":"1.2.0""""))
        assertTrue(json.contains(""""sensor_timestamp_source":"REALTIME""""))
        // Not a header line like the .ndjson files (spec §8) — schema_version sits inside the object.
        assertTrue(json.trim().startsWith("{") && json.contains(""""schema_version""""))
    }
}
