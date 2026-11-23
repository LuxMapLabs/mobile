package com.luxmap.core.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LuxSamplingWindowTest {
    private fun LuxSamplingWindow.addAll(vararg values: Double): LuxWindowState = values.map { add(it) }.last()

    @Test
    fun `is not stable before the window is full`() {
        val state = LuxSamplingWindow().addAll(12.1, 12.2, 12.3, 12.4)

        assertFalse(state.isStable)
        assertEquals(4, state.sampleCount)
        assertEquals(12.4, state.latestLux)
    }

    @Test
    fun `is stable when five close samples arrive and returns their median`() {
        val state = LuxSamplingWindow().addAll(12.1, 12.4, 12.3, 12.2, 12.5)

        assertTrue(state.isStable)
        assertEquals(12.3, state.stableLux)
    }

    @Test
    fun `is not stable when the spread is too wide`() {
        val state = LuxSamplingWindow().addAll(10.2, 15.8, 11.4, 13.0, 12.1)

        assertFalse(state.isStable)
        assertNull(state.stableLux)
    }

    @Test
    fun `uses the absolute floor for very low readings`() {
        val state = LuxSamplingWindow().addAll(0.1, 0.4, 0.2, 0.5, 0.3)

        assertTrue(state.isStable)
        assertEquals(0.3, state.stableLux)
    }

    @Test
    fun `uses the relative limit for high readings`() {
        val stable = LuxSamplingWindow().addAll(100.0, 104.0, 108.0, 102.0, 106.0)
        val unstable = LuxSamplingWindow().addAll(100.0, 104.0, 112.0, 102.0, 106.0)

        assertTrue(stable.isStable)
        assertFalse(unstable.isStable)
    }

    @Test
    fun `old samples slide out so the window becomes stable again`() {
        val state = LuxSamplingWindow().addAll(30.0, 12.1, 12.2, 12.3, 12.2, 12.1)

        assertTrue(state.isStable)
        assertEquals(12.2, state.stableLux)
    }

    @Test
    fun `ignores negative and non finite values`() {
        val window = LuxSamplingWindow()
        window.addAll(12.0, 12.0)

        val state = window.addAll(-1.0, Double.NaN, Double.POSITIVE_INFINITY)

        assertEquals(2, state.sampleCount)
        assertEquals(12.0, state.latestLux)
    }

    @Test
    fun `reset clears the window`() {
        val window = LuxSamplingWindow()
        window.addAll(12.1, 12.2, 12.3, 12.2, 12.1)

        val state = window.reset()

        assertEquals(0, state.sampleCount)
        assertNull(state.latestLux)
        assertFalse(state.isStable)
    }

    @Test
    fun `rounds the median to two decimals`() {
        val state = LuxSamplingWindow().addAll(12.3456, 12.3456, 12.3456, 12.3456, 12.3456)

        assertEquals(12.35, state.stableLux)
    }
}
