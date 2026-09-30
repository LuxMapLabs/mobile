package com.luxmap.core.ble

import java.nio.ByteBuffer
import java.nio.ByteOrder

// Byte layout is a PROPOSAL (spec §9), not yet confirmed with the firmware owner — see
// docs/contract-drift.md. seq: uint16 LE, module_ms: uint32 LE, lux: float32 LE, boot_id: uint8.
private const val SEQ_MODULO = 65536

data class LuxSample(
    val seq: Int,
    val moduleMs: Long,
    val phoneElapsedNs: Long,
    val lux: Float,
    val bootId: Int,
)

object LuxPacketCodec {
    fun decode(
        bytes: ByteArray,
        receivedAtElapsedRealtimeNs: Long,
    ): LuxSample {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val seq = buffer.short.toInt() and 0xFFFF
        val moduleMs = buffer.int.toLong() and 0xFFFFFFFFL
        val lux = buffer.float
        val bootId = buffer.get().toInt() and 0xFF
        return LuxSample(seq, moduleMs, receivedAtElapsedRealtimeNs, lux, bootId)
    }

    // Counts packets missed between two samples, correctly handling both the uint16 seq wraparound
    // and a module reboot (bootId change) — a naive (currentSeq - previousSeq - 1) would report a
    // false ~65000-packet gap at the rollover, and an even bigger false gap across a reboot.
    fun gapSize(
        previous: LuxSample,
        current: LuxSample,
    ): Int {
        if (previous.bootId != current.bootId) return 0
        val forwardDistance = ((current.seq - previous.seq) + SEQ_MODULO) % SEQ_MODULO
        return (forwardDistance - 1).coerceAtLeast(0)
    }
}
