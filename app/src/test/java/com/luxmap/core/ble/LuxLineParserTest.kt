package com.luxmap.core.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LuxLineParserTest {
    @Test
    fun `parses a real device line and keeps the phone receive time`() {
        val sample = LuxLineParser.parse("2026-09-30 23:32:07 | Light: 69.17 lux", receivedAtElapsedNs = 999L)

        assertEquals(LuxSample(lux = 69.17, receivedAtElapsedNs = 999L), sample)
    }

    @Test
    fun `tolerates surrounding whitespace and line endings`() {
        assertEquals(69.17, LuxLineParser.parse("  2026-09-30 23:32:07 | Light: 69.17 lux  \r", 0L)?.lux)
    }

    @Test
    fun `accepts zero lux`() {
        assertEquals(0.0, LuxLineParser.parse("2026-09-30 23:32:07 | Light: 0.00 lux", 0L)?.lux)
    }

    @Test
    fun `returns null for a line that does not match the format`() {
        assertNull(LuxLineParser.parse("garbage", 0L))
        assertNull(LuxLineParser.parse("", 0L))
        assertNull(LuxLineParser.parse("2026-09-30 23:32:07 | Light: lux", 0L))
        assertNull(LuxLineParser.parse("2026-09-30 23:32:07 | Light: -1.0 lux", 0L))
    }

    @Test
    fun `returns null for a malformed number`() {
        assertNull(LuxLineParser.parse("2026-09-30 23:32:07 | Light: 1.2.3 lux", 0L))
        assertNull(LuxLineParser.parse("2026-09-30 23:32:07 | Light: . lux", 0L))
    }
}
