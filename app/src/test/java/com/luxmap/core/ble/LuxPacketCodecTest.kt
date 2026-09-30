package com.luxmap.core.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class LuxPacketCodecTest {
    @Test
    fun `decodes a real device line and stamps it with the phone's receive time and given seq`() {
        val decoded =
            LuxPacketCodec.decode(
                line = "2026-09-30 23:32:07 | Light: 69.17 lux",
                receivedAtElapsedRealtimeNs = 999L,
                seq = 10,
            )

        val expectedModuleMs =
            LocalDateTime.parse("2026-09-30 23:32:07", DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        assertEquals(
            LuxSample(seq = 10, moduleMs = expectedModuleMs, phoneElapsedNs = 999L, lux = 69.17f, bootId = 0),
            decoded,
        )
    }

    @Test
    fun `tolerates surrounding whitespace and line endings from the serial stream`() {
        val decoded = LuxPacketCodec.decode("  2026-09-30 23:32:07 | Light: 69.17 lux  \r", 0L, seq = 0)
        assertEquals(69.17f, decoded?.lux)
    }

    @Test
    fun `returns null for a line that does not match the expected format`() {
        assertNull(LuxPacketCodec.decode("garbage", 0L, seq = 0))
        assertNull(LuxPacketCodec.decode("", 0L, seq = 0))
        assertNull(LuxPacketCodec.decode("2026-09-30 23:32:07 | Light: lux", 0L, seq = 0))
    }
}
