package com.luxmap.core.ble

import kotlin.math.roundToLong

data class LuxWindowState(
    val latestLux: Double?,
    val sampleCount: Int,
    val stableLux: Double?,
    /** The raw window behind [stableLux], in the order measured: sent to the server as `protocol.samples` (LUX-BASELINE). */
    val samples: List<Double> = emptyList(),
) {
    val isStable: Boolean get() = stableLux != null
}

/**
 * The stationary lux protocol the server checks (backend `LuxProtocol`, Contract v1.24): the sensor sits on a pole of fixed
 * height right under the lamp head, and a window counts only when `(max − min) / median` stays within [MAX_WINDOW_SPREAD].
 * The app applies the same rule before "Ghi nhận" so a reading it accepts is never refused later by the server.
 */
object LuxProtocol {
    const val SENSOR_HEIGHT_M = 1.0
    const val MAX_WINDOW_SPREAD = 0.05
}

class LuxSamplingWindow(
    private val size: Int = 5,
    private val maxRelativeSpread: Double = LuxProtocol.MAX_WINDOW_SPREAD,
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

    fun state(): LuxWindowState {
        val stable = stableMedian()
        return LuxWindowState(
            latestLux = samples.lastOrNull(),
            sampleCount = samples.size,
            stableLux = stable,
            samples = if (stable != null) samples.toList() else emptyList(),
        )
    }

    private fun stableMedian(): Double? {
        if (samples.size < size) return null
        val median = median(samples)
        val spread = samples.max() - samples.min()
        if (spread > 0 && (median <= 0 || spread / median > maxRelativeSpread)) return null
        return (median * 100).roundToLong() / 100.0
    }

    companion object {
        fun median(values: Collection<Double>): Double {
            val sorted = values.sorted()
            return if (sorted.size % 2 == 1) {
                sorted[sorted.size / 2]
            } else {
                sorted[sorted.size / 2 - 1] / 2 + sorted[sorted.size / 2] / 2
            }
        }
    }
}
