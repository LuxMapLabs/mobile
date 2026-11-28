package com.luxmap.feature.survey.ui.polephoto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RouteHandoffTest {
    @Test
    fun `a handoff survives the trip through the navigation argument`() {
        val handoff = RouteHandoff("POLE-0110", "off")

        assertEquals(handoff, RouteHandoff.decode(handoff.encode()))
        assertEquals("Đã lưu POLE-0110 · Tắt", handoff.message())
        assertEquals("Đã bỏ qua POLE-0216", RouteHandoff("POLE-0216", RouteHandoff.SKIPPED).message())
    }

    @Test
    fun `a missing or malformed argument means no previous pole`() {
        assertNull(RouteHandoff.decode(null))
        assertNull(RouteHandoff.decode("POLE-0110"))
    }
}
