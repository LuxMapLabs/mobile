package com.luxmap.core.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class SeverityFromWireTest {
    @Test
    fun `low maps to LOW`() {
        assertEquals(WorkOrderPriority.LOW, severityFromWire("low"))
    }

    @Test
    fun `medium maps to NORMAL`() {
        assertEquals(WorkOrderPriority.NORMAL, severityFromWire("medium"))
    }

    @Test
    fun `high maps to HIGH`() {
        assertEquals(WorkOrderPriority.HIGH, severityFromWire("high"))
    }

    @Test
    fun `critical maps to URGENT`() {
        assertEquals(WorkOrderPriority.URGENT, severityFromWire("critical"))
    }

    @Test
    fun `an unknown value falls back to NORMAL, not a crash`() {
        assertEquals(WorkOrderPriority.NORMAL, severityFromWire("something_new"))
    }
}
