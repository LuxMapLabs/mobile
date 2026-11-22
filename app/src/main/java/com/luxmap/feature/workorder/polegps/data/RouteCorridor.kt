package com.luxmap.feature.workorder.polegps.data

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot

const val ROUTE_CORRIDOR_METRES = 25.0
private const val EARTH_RADIUS_METRES = 6_371_008.8
private const val METRES_PER_DEGREE = EARTH_RADIUS_METRES * PI / 180.0

data class RoutePoint(val lat: Double, val lng: Double)

sealed interface CorridorCheck {
    data class Inside(val distanceM: Double) : CorridorCheck

    data class Outside(val distanceM: Double) : CorridorCheck

    data object Unknown : CorridorCheck
}

fun parseRouteWkt(wkt: String?): List<List<RoutePoint>>? {
    val text = wkt?.trim().orEmpty()
    if (!text.contains("(")) return null
    val upper = text.uppercase()
    val inner = text.substringAfter("(").substringBeforeLast(")")
    val body =
        when {
            upper.startsWith("MULTILINESTRING") -> inner
            upper.startsWith("LINESTRING") -> "($inner)"
            else -> return null
        }
    val lines =
        Regex("\\(([^()]*)\\)").findAll(body).toList().map { match ->
            match.groupValues[1].split(",").map { pair ->
                val parts = pair.trim().split(Regex("\\s+"))
                val lng = parts.getOrNull(0)?.toDoubleOrNull() ?: return null
                val lat = parts.getOrNull(1)?.toDoubleOrNull() ?: return null
                RoutePoint(lat, lng)
            }
        }
    if (lines.isEmpty() || lines.any { it.size < 2 }) return null
    return lines
}

fun distanceToRouteMetres(
    lat: Double,
    lng: Double,
    lines: List<List<RoutePoint>>,
): Double {
    val metresPerDegreeLng = METRES_PER_DEGREE * cos(lat * PI / 180.0)

    fun x(point: RoutePoint) = (point.lng - lng) * metresPerDegreeLng

    fun y(point: RoutePoint) = (point.lat - lat) * METRES_PER_DEGREE
    return lines.minOf { line ->
        line.zipWithNext().minOf { (a, b) -> distanceFromOrigin(x(a), y(a), x(b), y(b)) }
    }
}

private fun distanceFromOrigin(
    ax: Double,
    ay: Double,
    bx: Double,
    by: Double,
): Double {
    val dx = bx - ax
    val dy = by - ay
    val lengthSquared = dx * dx + dy * dy
    val t = if (lengthSquared == 0.0) 0.0 else (-(ax * dx + ay * dy) / lengthSquared).coerceIn(0.0, 1.0)
    return hypot(ax + t * dx, ay + t * dy)
}

fun corridorCheck(
    geomWkt: String?,
    lat: Double,
    lng: Double,
): CorridorCheck {
    val lines = parseRouteWkt(geomWkt) ?: return CorridorCheck.Unknown
    val distance = distanceToRouteMetres(lat, lng, lines)
    return if (distance <= ROUTE_CORRIDOR_METRES) CorridorCheck.Inside(distance) else CorridorCheck.Outside(distance)
}

fun distanceMetres(
    lat1: Double,
    lng1: Double,
    lat2: Double,
    lng2: Double,
): Double {
    val metresPerDegreeLng = METRES_PER_DEGREE * cos(lat1 * PI / 180.0)
    return hypot((lng2 - lng1) * metresPerDegreeLng, (lat2 - lat1) * METRES_PER_DEGREE)
}
