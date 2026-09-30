package com.luxmap.core.camera

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExposureLockControllerTest {
    private val controller = ExposureLockController()

    @Test
    fun lockedRequestKeysFixIsoExposureFrameDurationAndDisableAfAndStabilization() {
        val profile =
            LockedCameraProfile(
                isoSensitivity = 800,
                exposureTimeNs = 20_000_000L,
                frameDurationNs = 33_333_333L,
            )

        val keys = controller.lockedRequestKeys(profile)

        assertEquals(CaptureRequest.CONTROL_AE_MODE_OFF, keys[CaptureRequest.CONTROL_AE_MODE])
        assertEquals(CaptureRequest.CONTROL_AF_MODE_OFF, keys[CaptureRequest.CONTROL_AF_MODE])
        assertEquals(800, keys[CaptureRequest.SENSOR_SENSITIVITY])
        assertEquals(20_000_000L, keys[CaptureRequest.SENSOR_EXPOSURE_TIME])
        assertEquals(33_333_333L, keys[CaptureRequest.SENSOR_FRAME_DURATION])
        assertEquals(
            CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF,
            keys[CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE],
        )
    }

    @Test
    fun lockedRequestKeysDoNotForceNoiseReductionOrEdgeMode() {
        val profile =
            LockedCameraProfile(
                isoSensitivity = 800,
                exposureTimeNs = 20_000_000L,
                frameDurationNs = 33_333_333L,
            )

        val keys = controller.lockedRequestKeys(profile)

        assertNull(keys[CaptureRequest.NOISE_REDUCTION_MODE])
        assertNull(keys[CaptureRequest.EDGE_MODE])
    }

    @Test
    fun timestampSourceRealtimeReturnsTrueOnlyWhenCharacteristicEqualsRealtime() {
        val realtimeCharacteristics = mockk<CameraCharacteristics>()
        every { realtimeCharacteristics.get(CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE) } returns
            CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME
        val unknownCharacteristics = mockk<CameraCharacteristics>()
        every { unknownCharacteristics.get(CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE) } returns
            CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE_UNKNOWN

        assertTrue(controller.isTimestampSourceRealtime(realtimeCharacteristics))
        assertFalse(controller.isTimestampSourceRealtime(unknownCharacteristics))
    }

    @Test
    fun lockAwbIfConvergedLocksOnlyOnceAwbStateIsConverged() {
        val builder = mockk<CaptureRequest.Builder>(relaxed = true)
        val notConverged = mockk<CaptureResult>()
        every { notConverged.get(CaptureResult.CONTROL_AWB_STATE) } returns CaptureResult.CONTROL_AWB_STATE_SEARCHING
        val converged = mockk<CaptureResult>()
        every { converged.get(CaptureResult.CONTROL_AWB_STATE) } returns CaptureResult.CONTROL_AWB_STATE_CONVERGED

        assertFalse(controller.lockAwbIfConverged(builder, notConverged))
        assertTrue(controller.lockAwbIfConverged(builder, converged))
        verify { builder.set(CaptureRequest.CONTROL_AWB_LOCK, true) }
    }
}
