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
    val sampleNo: Int,
    val moduleEpoch: Int,
    // The module's own wall-clock reading for this line, parsed as milliseconds since epoch in
    // the phone's local zone (the two clocks were observed roughly in sync - not a synchronized
    // monotonic counter like the earlier binary proposal assumed).
    val moduleMs: Long,
    val phoneElapsedNs: Long,
    val lux: Float,
)

// Stateful per BLE/SPP connection (sample_no and module_epoch both reset on reconnect) — callers
// must create a new instance, or call reset(), per connect() (spec: docs/contract-drift.md, lux_log
// row, 2026-10-08). module_epoch only tracks module_ms going backward: this device's serial line
// has no onboard sequence number to compare, unlike what BE's generic feedback assumed.
class LuxPacketCodec {
    private var nextSampleNo = 0
    private var currentEpoch = 0
    private var lastModuleMs: Long? = null

    fun reset() {
        nextSampleNo = 0
        currentEpoch = 0
        lastModuleMs = null
    }

    // Returns null for a line that does not match the expected format (a partial read at the
    // start of a connection, or noise) - the caller skips it rather than crashing the session.
    fun decode(
        line: String,
        receivedAtElapsedRealtimeNs: Long,
    ): LuxSample? {
        val match = LINE_PATTERN.matchEntire(line.trim()) ?: return null
        val (timestampText, luxText) = match.destructured
        val moduleMs =
            LocalDateTime.parse(timestampText, TIMESTAMP_FORMAT)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        lastModuleMs?.let { previous -> if (moduleMs < previous) currentEpoch++ }
        lastModuleMs = moduleMs
        val sample = LuxSample(nextSampleNo, currentEpoch, moduleMs, receivedAtElapsedRealtimeNs, luxText.toFloat())
        nextSampleNo++
        return sample
    }
}
