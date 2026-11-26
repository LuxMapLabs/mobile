package com.luxmap.feature.workorder.polegps.data

import java.time.Instant

const val GPS_MAX_AGE_NANOS = 10_000_000_000L
const val GPS_ACCEPTABLE_ACCURACY_METRES = 20.0

enum class GpsFixQuality { INVALID, STALE, ACCEPTABLE, LOW_ACCURACY }

fun GpsFix.quality(nowElapsedRealtimeNanos: Long): GpsFixQuality {
    val accuracyValid = accuracyM == null || (accuracyM.isFinite() && accuracyM >= 0)
    if (!lat.isFinite() || lat !in -90.0..90.0 || !lng.isFinite() || lng !in -180.0..180.0 ||
        !accuracyValid || capturedAt <= Instant.EPOCH || elapsedRealtimeNanos < 0 ||
        nowElapsedRealtimeNanos < elapsedRealtimeNanos
    ) {
        return GpsFixQuality.INVALID
    }
    if (nowElapsedRealtimeNanos - elapsedRealtimeNanos > GPS_MAX_AGE_NANOS) return GpsFixQuality.STALE
    val lowAccuracy = accuracyM != null && accuracyM > GPS_ACCEPTABLE_ACCURACY_METRES
    return if (lowAccuracy) GpsFixQuality.LOW_ACCURACY else GpsFixQuality.ACCEPTABLE
}
