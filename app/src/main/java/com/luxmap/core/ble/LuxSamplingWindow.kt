package com.luxmap.core.ble

import kotlin.math.max
import kotlin.math.roundToLong

data class LuxWindowState(
    val latestLux: Double?,
    val sampleCount: Int,
    val stableLux: Double?,
) {
    val isStable: Boolean get() = stableLux != null
}

class LuxSamplingWindow(
    private val size: Int = 5,
    private val minSpreadLux: Double = 0.5,
    private val maxRelativeSpread: Double = 0.10,
) {
    private val samples = ArrayDeque<Double>(size)

    fun add(lux: Double): LuxWindowState {
        if (lux.isFinite() && lux >= 0) {
            if (samples.size == size) samples.removeFirst()
            samples.addLast(lux)
        }
        return state()
    }

    fun reset(): LuxWindowState {
        samples.clear()
        return state()
    }

    fun state(): LuxWindowState =
        LuxWindowState(
            latestLux = samples.lastOrNull(),
            sampleCount = samples.size,
            stableLux = stableMedian(),
        )

    private fun stableMedian(): Double? {
        if (samples.size < size) return null
        val sorted = samples.sorted()
        val median =
            if (sorted.size % 2 == 1) {
                sorted[sorted.size / 2]
            } else {
                (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2
            }
        val spread = sorted.last() - sorted.first()
        if (spread > max(minSpreadLux, median * maxRelativeSpread)) return null
        return (median * 100).roundToLong() / 100.0
    }
}
