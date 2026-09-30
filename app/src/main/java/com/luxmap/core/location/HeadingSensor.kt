package com.luxmap.core.location

import android.hardware.SensorManager
import javax.inject.Inject

// Fully independent stream from GPS bearing (spec §8) — own sampling rate, own timestamp source
// (the rotation-vector sensor's own event time), logged to its own heading_log.ndjson file.
data class HeadingSample(
    val elapsedRealtimeNs: Long,
    val headingDeg: Float,
)

class HeadingSensor
    @Inject
    constructor() {
        fun headingFromRotationVector(
            rotationVector: FloatArray,
            eventElapsedRealtimeNs: Long,
        ): HeadingSample {
            val rotationMatrix = FloatArray(9)
            SensorManager.getRotationMatrixFromVector(rotationMatrix, rotationVector)
            val orientation = FloatArray(3)
            SensorManager.getOrientation(rotationMatrix, orientation)
            val azimuthDeg = Math.toDegrees(orientation[0].toDouble()).toFloat()
            val normalizedDeg = (azimuthDeg + 360f) % 360f
            return HeadingSample(eventElapsedRealtimeNs, normalizedDeg)
        }
    }
