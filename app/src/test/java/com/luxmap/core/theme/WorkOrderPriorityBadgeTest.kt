package com.luxmap.core.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkOrderPriorityBadgeTest {
    @Test
    fun `every priority badge meets AA for text`() {
        WorkOrderPriority.entries.forEach { priority ->
            val colors = LightStatusColors.priority.getValue(priority)
            val ratio = contrastRatio(colors.text, colors.background)
            assertTrue("${priority.name} contrast was $ratio", ratio >= 4.5)
        }
    }

    @Test
    fun `the high priority badge is no longer the unreadable amber`() {
        val colors = LightStatusColors.priority.getValue(WorkOrderPriority.HIGH)
        val ratio = contrastRatio(colors.text, colors.background)
        assertTrue("HIGH contrast was $ratio", ratio >= 6.0)
    }

    @Test
    fun `each priority has a distinct background so the four stay tellable apart`() {
        val backgrounds = WorkOrderPriority.entries.map { LightStatusColors.priority.getValue(it).background }
        assertEquals(backgrounds.size, backgrounds.toSet().size)
    }

    @Test
    fun `every priority badge still carries a Vietnamese label`() {
        assertEquals("Thấp", WorkOrderPriority.LOW.label())
        assertEquals("Bình thường", WorkOrderPriority.NORMAL.label())
        assertEquals("Cao", WorkOrderPriority.HIGH.label())
        assertEquals("Khẩn", WorkOrderPriority.URGENT.label())
    }

    @Test
    fun `an unknown wire severity still resolves to a readable badge`() {
        val colors = LightStatusColors.priority.getValue(severityFromWire("something_the_backend_added_later"))
        val ratio = contrastRatio(colors.text, colors.background)
        assertTrue("fallback contrast was $ratio", ratio >= 4.5)
        assertTrue(colors.background != Color.Unspecified)
    }
}
