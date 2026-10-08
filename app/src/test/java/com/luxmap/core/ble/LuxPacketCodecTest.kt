package com.luxmap.core.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class LuxPacketCodecTest {
    private lateinit var codec: LuxPacketCodec

    @Before
    fun setUp() {
        codec = LuxPacketCodec()
    }

    @Test
    fun `decodes a real device line and stamps it with the phone's receive time`() {
        val decoded = codec.decode(line = "2026-09-30 23:32:07 | Light: 69.17 lux", receivedAtElapsedRealtimeNs = 999L)

        val expectedModuleMs =
            LocalDateTime.parse("2026-09-30 23:32:07", DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        assertEquals(
            LuxSample(sampleNo = 0, moduleEpoch = 0, moduleMs = expectedModuleMs, phoneElapsedNs = 999L, lux = 69.17f),
            decoded,
        )
    }

    @Test
    fun `tolerates surrounding whitespace and line endings from the serial stream`() {
        val decoded = codec.decode("  2026-09-30 23:32:07 | Light: 69.17 lux  \r", 0L)
        assertEquals(69.17f, decoded?.lux)
    }

    @Test
    fun `returns null for a line that does not match the expected format`() {
        assertNull(codec.decode("garbage", 0L))
        assertNull(codec.decode("", 0L))
        assertNull(codec.decode("2026-09-30 23:32:07 | Light: lux", 0L))
    }

    @Test
    fun `first sample is epoch 0`() {
        val sample = codec.decode("2026-09-30 23:32:07 | Light: 69.17 lux", receivedAtElapsedRealtimeNs = 1L)
        assertEquals(0, sample?.sampleNo)
        assertEquals(0, sample?.moduleEpoch)
    }

    @Test
    fun `module_ms jumping backward bumps the epoch`() {
        codec.decode("2026-09-30 23:32:07 | Light: 1.0 lux", receivedAtElapsedRealtimeNs = 1L)
        val afterReboot = codec.decode("2000-01-01 00:00:01 | Light: 1.0 lux", receivedAtElapsedRealtimeNs = 2L)
        assertEquals(1, afterReboot?.moduleEpoch)
    }

    @Test
    fun `sample_no increments per decoded line, does not reset on a bad line`() {
        codec.decode("2026-09-30 23:32:07 | Light: 1.0 lux", receivedAtElapsedRealtimeNs = 1L)
        codec.decode("garbage", receivedAtElapsedRealtimeNs = 2L)
        val third = codec.decode("2026-09-30 23:32:08 | Light: 1.0 lux", receivedAtElapsedRealtimeNs = 3L)
        assertEquals(1, third?.sampleNo)
    }

    @Test
    fun `reset restarts sample_no and module_epoch, and clears the backward-jump baseline`() {
        codec.decode("2026-09-30 23:32:07 | Light: 1.0 lux", receivedAtElapsedRealtimeNs = 1L)
        codec.decode("2000-01-01 00:00:01 | Light: 1.0 lux", receivedAtElapsedRealtimeNs = 2L)

        codec.reset()
        val afterReset = codec.decode("2026-09-30 23:32:08 | Light: 1.0 lux", receivedAtElapsedRealtimeNs = 3L)

        assertEquals(0, afterReset?.sampleNo)
        assertEquals(0, afterReset?.moduleEpoch)
    }
}
