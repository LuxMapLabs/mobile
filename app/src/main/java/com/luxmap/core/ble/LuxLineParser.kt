package com.luxmap.core.ble

data class LuxSample(
    val lux: Double,
    val receivedAtElapsedNs: Long,
)

object LuxLineParser {
    private val LINE_PATTERN = Regex("""^Lux:\s*([0-9.]+)$""")

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

class LuxLineBuffer {
    private val pending = StringBuilder()

    fun append(chunk: String): List<String> {
        pending.append(chunk)
        val lines = mutableListOf<String>()
        while (true) {
            val end = pending.indexOf("\n")
            if (end < 0) break
            lines += pending.substring(0, end)
            pending.delete(0, end + 1)
        }
        if (pending.length > MAX_PENDING) pending.clear()
        return lines
    }

    fun clear() {
        pending.clear()
    }

    private companion object {
        const val MAX_PENDING = 256
    }
}
