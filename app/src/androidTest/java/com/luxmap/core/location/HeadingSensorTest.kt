package com.luxmap.core.location

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HeadingSensorTest {
    private val sensor = HeadingSensor()

    @Test
    fun rotationVectorPointingDueNorthYieldsHeadingCloseToZeroDegrees() {
        // Identity-like rotation vector (no rotation applied) — SensorManager.getRotationMatrixFromVector
        // + getOrientation on [0,0,0] yields azimuth 0.
        val sample = sensor.headingFromRotationVector(floatArrayOf(0f, 0f, 0f), eventElapsedRealtimeNs = 42L)

        assertEquals(42L, sample.elapsedRealtimeNs)
        assertEquals(0.0f, sample.headingDeg, 0.5f)
    }

    @Test
    fun headingIsNormalizedToTheZeroToThreeSixtyDegreeRange() {
        // A rotation vector representing a small negative-azimuth rotation around the Z axis
        // (sin(-5 deg / 2), 0, 0 style) should not surface as a negative heading.
        val sample = sensor.headingFromRotationVector(floatArrayOf(0f, 0f, -0.0436f), eventElapsedRealtimeNs = 0L)

        assert(sample.headingDeg in 0.0f..360.0f)
    }
}
