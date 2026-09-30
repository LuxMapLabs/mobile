package com.luxmap.core.ble

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// The real device (LuxMap_ESP32) sends one plain text line per reading over classic Bluetooth
// SPP, roughly once a second, like: "2026-09-30 23:32:07 | Light: 69.17 lux" - confirmed by a
// real-device check (2026-09-30), replacing an earlier binary layout that was only ever a
// proposal (spec §9) and never matched what the firmware actually sends. See docs/contract-drift.md.
private val LINE_PATTERN = Regex("""^(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}) \| Light: ([0-9.]+) lux$""")
private val TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

data class LuxSample(
    // Locally counted from 0 for this connection - the device's own text line carries no
    // sequence number, so this cannot detect a real dropped reading the way a device-side
    // counter would. Confirming a real sequence/boot field with the firmware owner is needed
    // before this can support accurate gap detection for RQ1.
    val seq: Int,
    // The module's own wall-clock reading for this line, parsed as milliseconds since epoch in
    // the phone's local zone (the two clocks were observed roughly in sync - not a synchronized
    // monotonic counter like the earlier binary proposal assumed).
    val moduleMs: Long,
    val phoneElapsedNs: Long,
    val lux: Float,
    // Always 0 - the device's own text line carries no boot/reset counter to tell reconnects
    // after a power cycle apart from an ordinary reconnect.
    val bootId: Int,
)

object LuxPacketCodec {
    // Returns null for a line that does not match the expected format (a partial read at the
    // start of a connection, or noise) - the caller skips it rather than crashing the session.
    fun decode(
        line: String,
        receivedAtElapsedRealtimeNs: Long,
        seq: Int,
    ): LuxSample? {
        val match = LINE_PATTERN.matchEntire(line.trim()) ?: return null
        val (timestampText, luxText) = match.destructured
        val moduleMs =
            LocalDateTime.parse(timestampText, TIMESTAMP_FORMAT)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        return LuxSample(seq, moduleMs, receivedAtElapsedRealtimeNs, luxText.toFloat(), bootId = 0)
    }
}
