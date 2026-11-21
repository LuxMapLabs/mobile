package com.luxmap.core.common

import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// No "x/N": the work order may hold only some poles of the segment, so a count taken from it would
// read as the segment's size ("Cột 1/1" on a segment of 12 poles).
fun polePositionLabel(
    position: Int?,
    segmentId: String?,
): String? {
    val segment = segmentId?.takeIf { it.isNotBlank() }
    val number = position?.let { "Cột số $it" }
    return listOfNotNull(number, segment).joinToString(" · ").ifEmpty { null }
}

// fixture_type is a locked enum (Contract section 1): led_road_lamp is its only value. An unknown
// code is left out rather than shown raw.
private val FIXTURE_TYPE_LABELS = mapOf("led_road_lamp" to "Đèn LED")

fun poleLampLabel(
    fixtureType: String?,
    lampWatt: Int?,
    lampCount: Int?,
    fixtureCount: Int?,
): String? {
    if (fixtureCount == 0) return "Chưa có bóng"
    val type = fixtureType?.trim()?.let(FIXTURE_TYPE_LABELS::get)
    val lamp =
        when {
            lampWatt != null && lampCount != null -> "${lampWatt}W × $lampCount"
            lampWatt != null -> "${lampWatt}W"
            lampCount != null -> "$lampCount bóng"
            else -> ""
        }
    return listOfNotNull(type, lamp.takeIf { it.isNotEmpty() }).joinToString(" · ").ifEmpty { null }
}

// Initial great-circle bearing from the phone to the pole, rather than the phone's heading.
fun poleDistanceLabel(
    phoneLat: Double?,
    phoneLng: Double?,
    poleLat: Double?,
    poleLng: Double?,
): String? {
    if (!validCoordinate(phoneLat, phoneLng) || !validCoordinate(poleLat, poleLng)) return null
    val fromLat = Math.toRadians(phoneLat!!)
    val toLat = Math.toRadians(poleLat!!)
    val deltaLat = toLat - fromLat
    val deltaLng = Math.toRadians(poleLng!! - phoneLng!!)
    val a =
        (
            sin(deltaLat / 2) * sin(deltaLat / 2) +
                cos(fromLat) * cos(toLat) * sin(deltaLng / 2) * sin(deltaLng / 2)
        ).coerceIn(0.0, 1.0)
    val meters = 6_371_000.0 * 2 * atan2(sqrt(a), sqrt(1 - a))
    if (meters < 0.5) return "0 m"
    val distance =
        if (meters < 1000) {
            "${meters.roundToInt()} m"
        } else {
            String.format(
                Locale.ROOT,
                "%.1f km",
                meters / 1000,
            )
        }
    val bearing =
        (
            Math.toDegrees(
                atan2(
                    sin(deltaLng) * cos(toLat),
                    cos(fromLat) * sin(toLat) -
                        sin(fromLat) * cos(toLat) * cos(deltaLng),
                ),
            ) + 360
        ) % 360
    val directions = listOf("Bắc", "Đông Bắc", "Đông", "Đông Nam", "Nam", "Tây Nam", "Tây", "Tây Bắc")
    return "$distance · ${directions[((bearing + 22.5) / 45).toInt() % 8]}"
}

private fun validCoordinate(
    lat: Double?,
    lng: Double?,
): Boolean =
    lat != null && lng != null && lat.isFinite() && lng.isFinite() && lat in -90.0..90.0 && lng in -180.0..180.0
