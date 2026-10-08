package com.luxmap.feature.survey.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GpsAccuracyGateTest {
    private val gate = GpsAccuracyGate(thresholdMeters = 8f, holdDurationMs = 3_000L)

    @Test
    fun `not ready on the first good reading alone`() {
        assertFalse(gate.onAccuracyUpdate(5f, nowElapsedRealtimeNs = 0L))
    }

    @Test
    fun `ready once accuracy stays under threshold for the full hold duration`() {
        gate.onAccuracyUpdate(5f, nowElapsedRealtimeNs = 0L)
        assertTrue(gate.onAccuracyUpdate(5f, nowElapsedRealtimeNs = 3_000_000_000L))
    }

    @Test
    fun `a bad reading mid-hold resets the timer`() {
        gate.onAccuracyUpdate(5f, nowElapsedRealtimeNs = 0L)
        gate.onAccuracyUpdate(20f, nowElapsedRealtimeNs = 2_000_000_000L)
        assertFalse(gate.onAccuracyUpdate(5f, nowElapsedRealtimeNs = 4_000_000_000L))
    }
}
