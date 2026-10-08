package com.luxmap.core.location

import android.location.Location
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

// gpsBearingDeg/speedMps share the fix's own elapsedRealtimeNs (spec §8) — they come from the
// same Location object. This is the only heading source now; the separate rotation-vector
// HeadingSensor/heading_log.ndjson pipeline was dropped (BE: server derives heading from GPS).
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

        private val _totalDistanceMeters = MutableStateFlow(0f)
        val totalDistanceMeters: StateFlow<Float> = _totalDistanceMeters.asStateFlow()

        // Volatile because onLocationUpdate() runs on the main-looper location callback thread
        // while onTick() runs on LocationHeadingRecorder's own Timer thread (Task 17b) — without
        // this, a write on one thread is not guaranteed to be visible when the other thread reads
        // it, so the GPS-lost warning could stay stale or flip inconsistently.
        @Volatile
        private var lastFixElapsedRealtimeNs: Long? = null

        // Null only before the first fix of the session - nothing to measure a distance against yet.
        @Volatile
        private var lastLocation: Location? = null

        fun onLocationUpdate(location: Location): TrackPoint {
            lastLocation?.let { previous -> _totalDistanceMeters.value += previous.distanceTo(location) }
            lastLocation = location
            lastFixElapsedRealtimeNs = location.elapsedRealtimeNanos
            return TrackPoint(
                elapsedRealtimeNs = location.elapsedRealtimeNanos,
                lat = location.latitude,
                lng = location.longitude,
                accuracyM = location.accuracy,
                gpsBearingDeg = if (location.hasBearing()) location.bearing else null,
                speedMps = if (location.hasSpeed()) location.speed else null,
            )
        }

        fun onTick(nowElapsedRealtimeNs: Long): GpsSignalState {
            val lastFix = lastFixElapsedRealtimeNs ?: return GpsSignalState.Lost
            val ageMs = (nowElapsedRealtimeNs - lastFix) / 1_000_000
            return if (ageMs > signalLostThresholdMs) GpsSignalState.Lost else GpsSignalState.Ok
        }
    }
