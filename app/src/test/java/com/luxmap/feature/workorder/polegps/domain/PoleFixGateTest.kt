package com.luxmap.feature.workorder.polegps.domain

import com.luxmap.feature.workorder.polegps.data.GpsFix
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class PoleFixGateTest {
    private fun fix(
        accuracy: Double,
        atMillis: Long,
    ) = GpsFix(10.0, 106.0, accuracy, Instant.ofEpochMilli(atMillis))

    private fun gate() = PoleFixGate(thresholdMeters = 8.0, holdMillis = 3000)

    @Test
    fun `an accurate fix starts the hold but is not ready yet`() {
        assertEquals(FixQuality.HOLDING, gate().accept(fix(5.0, 0)))
    }

    @Test
    fun `an accurate fix held for the full duration is ready`() {
        val gate = gate()
        gate.accept(fix(5.0, 0))
        gate.accept(fix(6.0, 1500))
        assertEquals(FixQuality.READY, gate.accept(fix(4.0, 3000)))
    }

    @Test
    fun `a weak fix resets the hold`() {
        val gate = gate()
        gate.accept(fix(5.0, 0))
        assertEquals(FixQuality.WEAK, gate.accept(fix(20.0, 1000)))
        assertEquals(FixQuality.HOLDING, gate.accept(fix(5.0, 2000)))
        assertEquals(FixQuality.HOLDING, gate.accept(fix(5.0, 4000)))
        assertEquals(FixQuality.READY, gate.accept(fix(5.0, 5000)))
    }

    @Test
    fun `accuracy exactly at the threshold counts as accurate`() {
        assertEquals(FixQuality.HOLDING, gate().accept(fix(8.0, 0)))
    }

    @Test
    fun `non finite or negative accuracy is weak`() {
        val gate = gate()
        assertEquals(FixQuality.WEAK, gate.accept(fix(Double.NaN, 0)))
        assertEquals(FixQuality.WEAK, gate.accept(fix(-1.0, 100)))
        assertEquals(FixQuality.WEAK, gate.accept(fix(Double.POSITIVE_INFINITY, 200)))
    }

    @Test
    fun `reset forgets the hold`() {
        val gate = gate()
        gate.accept(fix(5.0, 0))
        gate.reset()
        assertEquals(FixQuality.HOLDING, gate.accept(fix(5.0, 5000)))
    }
}
