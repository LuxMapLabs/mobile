package com.luxmap.core.ble

data class LuxSample(
    val lux: Double,
    val receivedAtElapsedNs: Long,
)

object LuxLineParser {
    private val LINE_PATTERN = Regex("""^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2} \| Light: ([0-9.]+) lux$""")

    fun parse(
        line: String,
        receivedAtElapsedNs: Long,
    ): LuxSample? {
        val match = LINE_PATTERN.matchEntire(line.trim()) ?: return null
        val lux = match.groupValues[1].toDoubleOrNull() ?: return null
        if (!lux.isFinite() || lux < 0) return null
        return LuxSample(lux = lux, receivedAtElapsedNs = receivedAtElapsedNs)
    }
}
