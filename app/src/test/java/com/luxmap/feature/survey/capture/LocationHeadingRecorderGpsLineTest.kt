package com.luxmap.feature.survey.capture

import com.luxmap.core.location.TrackPoint
import org.junit.Assert.assertEquals
import org.junit.Test

class LocationHeadingRecorderGpsLineTest {
    @Test
    fun `serializes heading_deg and sample_no, null bearing stays null`() {
        val point = TrackPoint(
            elapsedRealtimeNs = 112157310000000L,
            lat = 10.7605505,
            lng = 106.6300307,
            accuracyM = 11.7f,
            gpsBearingDeg = null,
            speedMps = null,
        )
        val line = buildGpsTrackLine(point, sampleNo = 0)
        assertEquals(
            """{"sample_no":0,"phone_elapsed_ns":"112157310000000","lat":10.7605505,"lng":106.6300307,""" +
                """"accuracy_m":11.7,"heading_deg":null,"speed_mps":null,"provider":"gps"}""",
            line,
        )
    }

    @Test
    fun `serializes a real bearing value, not null`() {
        val point = TrackPoint(
            elapsedRealtimeNs = 1L,
            lat = 1.0,
            lng = 2.0,
            accuracyM = 5f,
            gpsBearingDeg = 87.5f,
            speedMps = 1.2f,
        )
        val line = buildGpsTrackLine(point, sampleNo = 3)
        assertEquals(
            """{"sample_no":3,"phone_elapsed_ns":"1","lat":1.0,"lng":2.0,""" +
                """"accuracy_m":5.0,"heading_deg":87.5,"speed_mps":1.2,"provider":"gps"}""",
            line,
        )
    }
}
