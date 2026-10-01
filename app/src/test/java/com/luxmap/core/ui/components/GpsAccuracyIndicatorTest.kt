package com.luxmap.core.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class GpsAccuracyIndicatorTest {
    @Test
    fun `no fix reports no valid position`() {
        assertEquals("Không có vị trí hợp lệ", gpsAccuracyLabel(null))
    }

    @Test
    fun `accuracy at the good threshold reports good`() {
        assertEquals("GPS tốt · ±10 m", gpsAccuracyLabel(10f))
    }

    @Test
    fun `accuracy just past the good threshold reports weak`() {
        assertEquals("GPS yếu · ±11 m", gpsAccuracyLabel(11f))
    }

    @Test
    fun `accuracy at the weak threshold reports weak`() {
        assertEquals("GPS yếu · ±30 m", gpsAccuracyLabel(30f))
    }

    @Test
    fun `accuracy past the weak threshold reports no valid position`() {
        assertEquals("Không có vị trí hợp lệ", gpsAccuracyLabel(31f))
    }
}
