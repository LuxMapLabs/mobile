package com.luxmap.feature.workorder.polegps.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class GpsFixQualityTest {
    private val fix = GpsFix(10.0, 106.0, 20.0, Instant.ofEpochSecond(42), 1_000_000_000L)

    @Test
    fun `accuracy boundary permits saving and warns above twenty metres`() {
        assertEquals(GpsFixQuality.ACCEPTABLE, fix.quality(1_000_000_000L))
        assertEquals(GpsFixQuality.LOW_ACCURACY, fix.copy(accuracyM = 20.1).quality(1_000_000_000L))
        assertEquals(GpsFixQuality.ACCEPTABLE, fix.copy(accuracyM = 0.0).quality(1_000_000_000L))
    }

    @Test
    fun `age uses inclusive monotonic ten second boundary`() {
        assertEquals(GpsFixQuality.ACCEPTABLE, fix.quality(11_000_000_000L))
        assertEquals(GpsFixQuality.STALE, fix.quality(11_000_000_001L))
        assertEquals(GpsFixQuality.INVALID, fix.quality(999_999_999L))
    }

    @Test
    fun `invalid coordinates accuracy and time cannot be saved`() {
        val invalid =
            listOf(
                fix.copy(lat = Double.NaN), fix.copy(lat = 90.1), fix.copy(lat = -90.1),
                fix.copy(lng = Double.POSITIVE_INFINITY), fix.copy(lng = 180.1), fix.copy(lng = -180.1),
                fix.copy(accuracyM = -1.0), fix.copy(accuracyM = Double.NaN),
                fix.copy(accuracyM = Double.POSITIVE_INFINITY), fix.copy(capturedAt = Instant.EPOCH),
                fix.copy(elapsedRealtimeNanos = -1L),
            )
        invalid.forEach { assertEquals(GpsFixQuality.INVALID, it.quality(1_000_000_000L)) }
        assertEquals(GpsFixQuality.ACCEPTABLE, fix.copy(lat = 90.0, lng = -180.0).quality(1_000_000_000L))
    }
}
