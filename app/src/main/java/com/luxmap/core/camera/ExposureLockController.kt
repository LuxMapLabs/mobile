package com.luxmap.core.camera

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import javax.inject.Inject

// Held across a whole recording session (spec: "áp dụng và giữ suốt phiên quay"), not just one shot.
data class LockedCameraProfile(
    val isoSensitivity: Int,
    val exposureTimeNs: Long,
    // Required whenever CONTROL_AE_MODE_OFF is set — otherwise the frame rate is undefined.
    val frameDurationNs: Long,
    val focusDistanceDiopters: Float = 0f,
)

class ExposureLockController
    @Inject
    constructor() {
        // Pure map so this is testable without a real CaptureRequest.Builder — applyTo() below
        // does the actual mutation and is only meaningfully verified on a real device (spike, spec §16).
        // AWB and NOISE_REDUCTION_MODE/EDGE_MODE are intentionally absent — see lockAwbIfConverged
        // and docs/contract-drift.md.
        fun lockedRequestKeys(profile: LockedCameraProfile): Map<CaptureRequest.Key<*>, Any> =
            mapOf(
                CaptureRequest.CONTROL_AE_MODE to CaptureRequest.CONTROL_AE_MODE_OFF,
                CaptureRequest.CONTROL_AF_MODE to CaptureRequest.CONTROL_AF_MODE_OFF,
                CaptureRequest.SENSOR_SENSITIVITY to profile.isoSensitivity,
                CaptureRequest.SENSOR_EXPOSURE_TIME to profile.exposureTimeNs,
                CaptureRequest.SENSOR_FRAME_DURATION to profile.frameDurationNs,
                CaptureRequest.LENS_FOCUS_DISTANCE to profile.focusDistanceDiopters,
                CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE to CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF,
            )

        fun applyTo(
            builder: CaptureRequest.Builder,
            profile: LockedCameraProfile,
        ) {
            lockedRequestKeys(profile).forEach { (key, value) ->
                @Suppress("UNCHECKED_CAST")
                builder.set(key as CaptureRequest.Key<Any>, value)
            }
        }

        fun isTimestampSourceRealtime(characteristics: CameraCharacteristics): Boolean =
            characteristics.get(CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE) ==
                CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME

        // Called on every CaptureResult from the repeating request until it returns true; the
        // caller (Task 17a) then stops calling it and keeps reusing the now-locked builder.
        fun lockAwbIfConverged(
            builder: CaptureRequest.Builder,
            latestResult: CaptureResult,
        ): Boolean {
            if (latestResult.get(CaptureResult.CONTROL_AWB_STATE) != CaptureResult.CONTROL_AWB_STATE_CONVERGED) {
                return false
            }
            builder.set(CaptureRequest.CONTROL_AWB_LOCK, true)
            return true
        }
    }
