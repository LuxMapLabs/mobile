package com.luxmap.feature.workorder.polegps.data

import kotlinx.coroutines.flow.Flow
import java.time.Instant

data class GpsFix(
    val lat: Double,
    val lng: Double,
    val accuracyM: Double,
    val capturedAt: Instant,
)

interface GpsFixSource {
    fun fixes(): Flow<GpsFix>
}
