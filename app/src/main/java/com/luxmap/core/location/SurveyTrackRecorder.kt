package com.luxmap.core.location

import android.location.Location
import javax.inject.Inject

// gpsBearingDeg/speedMps share the fix's own elapsedRealtimeNs (spec §8) — they come from the
// same Location object, unlike heading_log.ndjson which is a fully independent sensor stream.
data class TrackPoint(
    val elapsedRealtimeNs: Long,
    val lat: Double,
    val lng: Double,
    val accuracyM: Float,
    val gpsBearingDeg: Float?,
    val speedMps: Float?,
)

sealed interface GpsSignalState {
    data object Ok : GpsSignalState

    data object Lost : GpsSignalState
}

// Separate from LocationTracker.kt (F12's one-shot "locate me") — this records a continuous track
// for the duration of a survey session (spec §5).
class SurveyTrackRecorder
    @Inject
    constructor() {
        private val signalLostThresholdMs: Long = 10_000L
        private var lastFixElapsedRealtimeNs: Long? = null

        fun onLocationUpdate(location: Location): TrackPoint {
            lastFixElapsedRealtimeNs = location.elapsedRealtimeNanos
            return TrackPoint(
                elapsedRealtimeNs = location.elapsedRealtimeNanos,
                lat = location.latitude,
                lng = location.longitude,
                accuracyM = location.accuracy,
                gpsBearingDeg = if (location.hasBearing()) location.bearing else null,
                speedMps = location.speed,
            )
        }

        fun onTick(nowElapsedRealtimeNs: Long): GpsSignalState {
            val lastFix = lastFixElapsedRealtimeNs ?: return GpsSignalState.Lost
            val ageMs = (nowElapsedRealtimeNs - lastFix) / 1_000_000
            return if (ageMs > signalLostThresholdMs) GpsSignalState.Lost else GpsSignalState.Ok
        }
    }
