package com.luxmap.core.ble

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class LuxPacketCodecTest {
    private fun packet(
        seq: Int,
        moduleMs: Long,
        lux: Float,
        bootId: Int,
    ): ByteArray =
        ByteBuffer
            .allocate(11)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putShort(seq.toShort())
            .putInt(moduleMs.toInt())
            .putFloat(lux)
            .put(bootId.toByte())
            .array()

    private fun sample(
        seq: Int,
        bootId: Int = 1,
    ) = LuxSample(seq = seq, moduleMs = 0L, phoneElapsedNs = 0L, lux = 0f, bootId = bootId)

    @Test
    fun `decodes a packet and stamps it with the phone's receive time`() {
        val bytes = packet(seq = 10, moduleMs = 5_000L, lux = 123.5f, bootId = 1)

        val decoded = LuxPacketCodec.decode(bytes, receivedAtElapsedRealtimeNs = 999L)

        assertEquals(LuxSample(seq = 10, moduleMs = 5_000L, phoneElapsedNs = 999L, lux = 123.5f, bootId = 1), decoded)
    }

    @Test
    fun `gap size between two adjacent packets is zero`() {
        assertEquals(0, LuxPacketCodec.gapSize(sample(seq = 10), sample(seq = 11)))
    }

    @Test
    fun `gap size counts missed packets in the ordinary non-wrapping case`() {
        assertEquals(4, LuxPacketCodec.gapSize(sample(seq = 10), sample(seq = 15)))
    }

    @Test
    fun `gap size handles seq rolling over past 65535 without reporting a huge false gap`() {
        // previousSeq near the uint16 ceiling, currentSeq wrapped back to a small value just after it
        assertEquals(0, LuxPacketCodec.gapSize(sample(seq = 65535), sample(seq = 0)))
        assertEquals(2, LuxPacketCodec.gapSize(sample(seq = 65534), sample(seq = 1)))
    }

    @Test
    fun `gap size is zero across a module reboot even though seq resets toward zero`() {
        // bootId changes -> the module restarted; seq/module_ms going "backwards" is expected and
        // must not be reported as a huge packet-loss event.
        assertEquals(0, LuxPacketCodec.gapSize(sample(seq = 60_000, bootId = 1), sample(seq = 3, bootId = 2)))
    }
}
