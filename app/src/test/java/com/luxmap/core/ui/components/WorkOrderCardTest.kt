package com.luxmap.core.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WorkOrderCardTest {
    private val today = LocalDate.of(2026, 9, 26)

    @Test
    fun `a due date equal to today reports its own label`() {
        val label = dueDateLabel("2026-09-26", today)

        assertEquals("Hạn hôm nay", label.text)
        assertTrue(label.isToday)
    }

    @Test
    fun `a due date in the future reports the formatted date, not today's label`() {
        val label = dueDateLabel("2026-09-29", today)

        assertEquals("Hạn 29/09/2026", label.text)
        assertFalse(label.isToday)
    }

    @Test
    fun `a null due date reports no deadline`() {
        val label = dueDateLabel(null, today)

        assertEquals("Chưa có hạn", label.text)
        assertFalse(label.isToday)
    }
}
