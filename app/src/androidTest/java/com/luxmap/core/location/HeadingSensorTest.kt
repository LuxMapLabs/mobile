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
        // For a pure-yaw rotation vector, azimuth comes out with the opposite sign of the vector's
        // own z component -- a positive z here yields a small NEGATIVE raw azimuth (about -5 degrees),
        // which is the case that actually exercises the (azimuthDeg + 360f) % 360f wraparound.
        val sample = sensor.headingFromRotationVector(floatArrayOf(0f, 0f, 0.0436f), eventElapsedRealtimeNs = 0L)

        assertEquals(355.0f, sample.headingDeg, 0.5f)
    }
}
