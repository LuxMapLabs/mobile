package com.luxmap.feature.workorder.polegps.domain

import com.luxmap.feature.survey.domain.GpsAccuracyGate
import com.luxmap.feature.workorder.polegps.data.GpsFix

enum class FixQuality { WAITING, WEAK, HOLDING, READY }

private const val DEFAULT_HOLD_MILLIS = 3000L

class PoleFixGate(
    private val thresholdMeters: Double = GpsAccuracyGate.PROPOSED_THRESHOLD_METERS.toDouble(),
    private val holdMillis: Long = DEFAULT_HOLD_MILLIS,
) {
    private var accurateSince: Long? = null

    fun accept(fix: GpsFix): FixQuality {
        val accurate = fix.accuracyM.isFinite() && fix.accuracyM >= 0.0 && fix.accuracyM <= thresholdMeters
        if (!accurate) {
            accurateSince = null
            return FixQuality.WEAK
        }
        val now = fix.capturedAt.toEpochMilli()
        val since = accurateSince ?: now.also { accurateSince = it }
        return if (now - since >= holdMillis) FixQuality.READY else FixQuality.HOLDING
    }

    fun reset() {
        accurateSince = null
    }
}
