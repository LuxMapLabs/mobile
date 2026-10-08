package com.luxmap.feature.survey.domain

// Threshold/hold duration are BE's PROPOSAL (mobile.pdf, 2026-10-08), explicitly "chốt sau buổi
// quay thử" — not final. Named constructor params, not hardcoded inline, so the real number (once
// confirmed) is a one-line change at the call site, not a search through this file.
class GpsAccuracyGate(
    private val thresholdMeters: Float,
    private val holdDurationMs: Long,
) {
    private var goodSinceElapsedNs: Long? = null

    fun onAccuracyUpdate(
        accuracyM: Float,
        nowElapsedRealtimeNs: Long,
    ): Boolean {
        if (accuracyM > thresholdMeters) {
            goodSinceElapsedNs = null
            return false
        }
        val since = goodSinceElapsedNs ?: nowElapsedRealtimeNs.also { goodSinceElapsedNs = it }
        return (nowElapsedRealtimeNs - since) >= holdDurationMs * 1_000_000L
    }

    companion object {
        const val PROPOSED_THRESHOLD_METERS = 8f
        const val PROPOSED_HOLD_DURATION_MS = 3_000L
    }
}
