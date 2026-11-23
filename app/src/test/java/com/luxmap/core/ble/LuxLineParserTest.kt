package com.luxmap.core.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LuxLineParserTest {
    @Test
    fun `parses a real device line and keeps the phone receive time`() {
        val sample = LuxLineParser.parse("Lux: 0.83", receivedAtElapsedNs = 999L)

        assertEquals(LuxSample(lux = 0.83, receivedAtElapsedNs = 999L), sample)
    }

    @Test
    fun `tolerates surrounding whitespace and line endings`() {
        assertEquals(69.17, LuxLineParser.parse("  Lux: 69.17  \r", 0L)?.lux)
    }

    @Test
    fun `accepts zero lux`() {
        assertEquals(0.0, LuxLineParser.parse("Lux: 0.00", 0L)?.lux)
    }

    @Test
    fun `returns null for a line that does not match the format`() {
        assertNull(LuxLineParser.parse("garbage", 0L))
        assertNull(LuxLineParser.parse("", 0L))
        assertNull(LuxLineParser.parse("Lux:", 0L))
        assertNull(LuxLineParser.parse("Lux: -1.0", 0L))
        assertNull(LuxLineParser.parse("2026-09-30 23:32:07 | Light: 69.17 lux", 0L))
    }

    @Test
    fun `returns null for a malformed number`() {
        assertNull(LuxLineParser.parse("Lux: 1.2.3", 0L))
        assertNull(LuxLineParser.parse("Lux: .", 0L))
    }

    @Test
    fun `buffer returns complete lines only`() {
        val buffer = LuxLineBuffer()

        assertEquals(emptyList<String>(), buffer.append("Lux: 0."))
        assertEquals(listOf("Lux: 0.83\r"), buffer.append("83\r\nLux"))
        assertEquals(listOf("Lux: 1.20\r", "Lux: 1.30\r"), buffer.append(": 1.20\r\nLux: 1.30\r\n"))
    }

    @Test
    fun `buffer drops an overlong line without a newline`() {
        val buffer = LuxLineBuffer()
        buffer.append("x".repeat(300))

        assertEquals(listOf("Lux: 2.00"), buffer.append("Lux: 2.00\n"))
    }

    @Test
    fun `clear drops a partial line`() {
        val buffer = LuxLineBuffer()
        buffer.append("Lux: 9")
        buffer.clear()

        assertEquals(listOf("Lux: 1.00"), buffer.append("Lux: 1.00\n"))
    }
}
