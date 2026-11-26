package com.luxmap.core.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class PhotoLocationTest {
    private val vietnam = ZoneId.of("Asia/Ho_Chi_Minh")

    private val iphone =
        mapOf(
            "GPSLatitude" to "10/1,51/1,8265600/1000000",
            "GPSLatitudeRef" to "N",
            "GPSLongitude" to "106/1,50/1,24658800/1000000",
            "GPSLongitudeRef" to "E",
            "DateTimeOriginal" to "2026:09:28 21:59:34",
            "OffsetTimeOriginal" to "+07:00",
        )

    @Test
    fun reads_coordinates_and_capture_time_from_an_iphone_photo() {
        val location = photoLocationFrom(vietnam) { iphone[it] }!!

        assertEquals(10.852296, location.lat, 1e-6)
        assertEquals(106.840183, location.lng, 1e-6)
        assertEquals(Instant.parse("2026-09-28T14:59:34Z"), location.capturedAt)
    }

    @Test
    fun south_and_west_references_make_negative_coordinates() {
        val location =
            photoLocationFrom(vietnam) {
                (iphone + mapOf("GPSLatitudeRef" to "S", "GPSLongitudeRef" to "W"))[it]
            }!!

        assertEquals(-10.852296, location.lat, 1e-6)
        assertEquals(-106.840183, location.lng, 1e-6)
    }

    @Test
    fun redacted_zero_coordinates_count_as_no_location() {
        val redacted =
            iphone + mapOf("GPSLatitude" to "0/1,0/1,0/1", "GPSLongitude" to "0/1,0/1,0/1")

        assertNull(photoLocationFrom(vietnam) { redacted[it] })
    }

    @Test
    fun missing_or_malformed_gps_counts_as_no_location() {
        assertNull(photoLocationFrom(vietnam) { (iphone - "GPSLatitude")[it] })
        assertNull(photoLocationFrom(vietnam) { (iphone - "GPSLongitudeRef")[it] })
        assertNull(photoLocationFrom(vietnam) { (iphone + ("GPSLatitude" to "10/0,51/1,8/1"))[it] })
        assertNull(photoLocationFrom(vietnam) { (iphone + ("GPSLatitude" to "abc"))[it] })
    }

    @Test
    fun capture_time_without_offset_uses_the_device_zone() {
        val location = photoLocationFrom(vietnam) { (iphone - "OffsetTimeOriginal")[it] }!!

        assertEquals(Instant.parse("2026-09-28T14:59:34Z"), location.capturedAt)
    }

    @Test
    fun capture_time_falls_back_to_the_gps_utc_stamp() {
        val gpsOnly =
            iphone - "DateTimeOriginal" + mapOf("GPSDateStamp" to "2026:09:28", "GPSTimeStamp" to "14/1,59/1,30/1")

        assertEquals(Instant.parse("2026-09-28T14:59:30Z"), photoLocationFrom(vietnam) { gpsOnly[it] }!!.capturedAt)
    }

    @Test
    fun capture_time_is_null_when_the_photo_has_none() {
        val noTime = iphone - "DateTimeOriginal"

        assertNull(photoLocationFrom(vietnam) { noTime[it] }!!.capturedAt)
    }
}
