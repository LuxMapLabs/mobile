package com.luxmap.core.location

import android.location.Location
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SurveyTrackRecorderTest {
    private fun fakeLocation(
        elapsedRealtimeNs: Long,
        lat: Double = 10.0,
        lng: Double = 106.0,
        accuracy: Float = 5.0f,
        bearing: Float = 90.0f,
        hasBearing: Boolean = true,
        speed: Float = 8.3f,
        hasSpeed: Boolean = true,
    ): Location =
        mockk<Location>().apply {
            every { this@apply.elapsedRealtimeNanos } returns elapsedRealtimeNs
            every { this@apply.latitude } returns lat
            every { this@apply.longitude } returns lng
            every { this@apply.accuracy } returns accuracy
            every { this@apply.hasBearing() } returns hasBearing
            every { this@apply.bearing } returns bearing
            every { this@apply.hasSpeed() } returns hasSpeed
            every { this@apply.speed } returns speed
        }

    @Test
    fun `maps a location fix to a track point carrying its own elapsed realtime timestamp`() {
        val recorder = SurveyTrackRecorder()

        val point = recorder.onLocationUpdate(fakeLocation(elapsedRealtimeNs = 1_000_000_000L))

        assertEquals(TrackPoint(1_000_000_000L, 10.0, 106.0, 5.0f, 90.0f, 8.3f), point)
    }

    @Test
    fun `reports GPS signal ok right after a fresh fix`() {
        val recorder = SurveyTrackRecorder()
        recorder.onLocationUpdate(fakeLocation(elapsedRealtimeNs = 0L))

        val state = recorder.onTick(nowElapsedRealtimeNs = 5_000_000_000L) // 5s later

        assertEquals(GpsSignalState.Ok, state)
    }

    @Test
    fun `reports GPS signal lost once the last fix is older than the threshold`() {
        val recorder = SurveyTrackRecorder()
        recorder.onLocationUpdate(fakeLocation(elapsedRealtimeNs = 0L))

        val state = recorder.onTick(nowElapsedRealtimeNs = 15_000_000_000L) // 15s later

        assertEquals(GpsSignalState.Lost, state)
    }

    @Test
    fun `reports GPS signal ok at exactly the threshold age`() {
        val recorder = SurveyTrackRecorder()
        recorder.onLocationUpdate(fakeLocation(elapsedRealtimeNs = 0L))

        // exactly 10s later, same as the internal signalLostThresholdMs -- pins the ">" (not ">=")
        // comparison so a future refactor can't silently flip this boundary without a test failing.
        val state = recorder.onTick(nowElapsedRealtimeNs = 10_000_000_000L)

        assertEquals(GpsSignalState.Ok, state)
    }

    @Test
    fun `reports GPS signal lost when ticked before any fix ever arrived`() {
        val recorder = SurveyTrackRecorder()

        val state = recorder.onTick(nowElapsedRealtimeNs = 1_000_000_000L)

        assertEquals(GpsSignalState.Lost, state)
    }

    @Test
    fun `maps a location fix with no bearing to a null gpsBearingDeg`() {
        val recorder = SurveyTrackRecorder()

        val point = recorder.onLocationUpdate(fakeLocation(elapsedRealtimeNs = 0L, hasBearing = false))

        assertNull(point.gpsBearingDeg)
    }

    @Test
    fun `maps a location fix with no speed to a null speedMps`() {
        val recorder = SurveyTrackRecorder()

        val point = recorder.onLocationUpdate(fakeLocation(elapsedRealtimeNs = 0L, hasSpeed = false))

        assertNull(point.speedMps)
    }
}
