package com.luxmap.feature.survey.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureConfigWriterTest {
    @Test
    fun `produces json with the fields BE's schema v1 requires`() {
        val config =
            CaptureConfig(
                bootSessionId = "95b59f63-5083-4e5f-911d-c070e75351d5",
                elapsedAnchorNs = 112156244127385L,
                utcAnchorIso = "2026-10-02T15:31:26.432377Z",
                utcUncertaintyMs = 50,
                phoneModel = "samsung SM-A075F",
                cameraId = "0",
                appVersion = "1.0",
                iso = 1600,
                exposureTimeNs = 30_004_000L,
                aperture = 1.8f,
                fps = 30,
                focusDistanceDiopters = 0.0f,
                whiteBalanceCctK = 4000,
                widthPx = 1920,
                heightPx = 1080,
                orientation = "portrait",
                profileId = 1,
                moduleFirmwareVersionId = 1,
            )

        val json = CaptureConfigWriter.toJson(config)

        assertEquals(
            """{"schema_version":1,"boot_session_id":"95b59f63-5083-4e5f-911d-c070e75351d5",""" +
                """"profile_id":1,"module_firmware_version_id":1,""" +
                """"phone_model":"samsung SM-A075F","camera_id":"0","app_version":"1.0",""" +
                """"sensor_timestamp_source":"REALTIME",""" +
                """"elapsed_anchor_ns":"112156244127385","utc_anchor":"2026-10-02T15:31:26.432377Z",""" +
                """"utc_uncertainty_ms":50,""" +
                """"camera":{"iso":1600,"exposure_time_ns":"30004000","aperture":1.8,""" +
                """"fps":30,"focus_mode":"manual","focus_distance":0.0,""" +
                """"white_balance_mode":"auto_locked","white_balance_value":{"cct_k":4000},""" +
                """"resolution":{"width":1920,"height":1080},""" +
                """"ae_enabled":false,"eis_enabled":false,"hdr_enabled":false,"night_mode_enabled":false},""" +
                """"mount":{"camera_side":"left","mount_height_m":1.1,""" +
                """"angle_deg":10,"sensor_position":"handlebar_top"},""" +
                """"orientation":"portrait"}""",
            json,
        )
    }

    @Test
    fun `reports aperture as json null when the device did not provide it, never f-0`() {
        val config =
            CaptureConfig(
                bootSessionId = "boot-1",
                elapsedAnchorNs = 1L,
                utcAnchorIso = "2026-10-02T15:31:26.432377Z",
                utcUncertaintyMs = 50,
                phoneModel = "test",
                cameraId = "0",
                appVersion = "1.0",
                iso = 1600,
                exposureTimeNs = 30_004_000L,
                aperture = null,
                fps = 30,
                focusDistanceDiopters = 0.0f,
                whiteBalanceCctK = 4000,
                widthPx = 1920,
                heightPx = 1080,
                orientation = "portrait",
                profileId = 1,
                moduleFirmwareVersionId = 1,
            )

        val json = CaptureConfigWriter.toJson(config)

        assertTrue(json.contains(""""aperture":null"""))
    }
}
