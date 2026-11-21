package com.luxmap.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PoleIdentificationTest {
    @Test
    fun `position names the place on the segment without a count and tolerates missing fields`() {
        assertEquals("Cột số 2 · S1", polePositionLabel(2, "S1"))
        assertEquals("Cột số 2", polePositionLabel(2, null))
        assertEquals("S1", polePositionLabel(null, "S1"))
        assertNull(polePositionLabel(null, null))
        assertNull(polePositionLabel(null, " "))
    }

    @Test
    fun `lamp omits missing parts and suppresses inactive fixtures`() {
        assertEquals("Đèn LED · 100W × 2", poleLampLabel("led_road_lamp", 100, 2, 1))
        assertEquals("Chưa có bóng", poleLampLabel("led_road_lamp", 100, 2, 0))
        assertEquals("Đèn LED", poleLampLabel("led_road_lamp", null, null, null))
        assertEquals("100W × 2", poleLampLabel("some_new_type", 100, 2, 1))
        assertEquals("100W", poleLampLabel(null, 100, null, null))
        assertEquals("2 bóng", poleLampLabel(null, null, 2, null))
        assertEquals("100W × 2", poleLampLabel(null, 100, 2, null))
        assertNull(poleLampLabel(" ", null, null, null))
    }

    @Test
    fun `distance requires valid fixes and handles zero and kilometers`() {
        assertNull(poleDistanceLabel(null, 0.0, 1.0, 1.0))
        assertNull(poleDistanceLabel(0.0, 0.0, null, 1.0))
        assertNull(poleDistanceLabel(0.0, 0.0, 1.0, null))
        assertNull(poleDistanceLabel(Double.NaN, 0.0, 1.0, 1.0))
        assertNull(poleDistanceLabel(91.0, 0.0, 1.0, 1.0))
        assertEquals("0 m", poleDistanceLabel(0.0, 0.0, 0.0, 0.0))
        assertEquals("111 m · Bắc", poleDistanceLabel(0.0, 0.0, 0.001, 0.0))
        assertEquals("1.1 km · Bắc", poleDistanceLabel(0.0, 0.0, 0.01, 0.0))
        assertEquals("222 m · Đông", poleDistanceLabel(0.0, 179.999, 0.0, -179.999))
        assertTrue(poleDistanceLabel(0.0, 0.0, 0.0, 180.0)!!.startsWith("20015.1 km"))
    }

    @Test
    fun `bearing covers all eight Vietnamese compass directions`() {
        val targets =
            listOf(
                1.0 to 0.0,
                1.0 to 1.0,
                0.0 to 1.0,
                -1.0 to 1.0,
                -1.0 to 0.0,
                -1.0 to -1.0,
                0.0 to -1.0,
                1.0 to -1.0,
            )
        val directions = listOf("Bắc", "Đông Bắc", "Đông", "Đông Nam", "Nam", "Tây Nam", "Tây", "Tây Bắc")
        targets.zip(directions).forEach { (target, direction) ->
            assertEquals(direction, poleDistanceLabel(0.0, 0.0, target.first, target.second)!!.substringAfter(" · "))
        }
    }
}
