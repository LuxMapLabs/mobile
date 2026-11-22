package com.luxmap.feature.workorder.polegps.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteCorridorTest {
    private val line = "LINESTRING (106 10, 106.001 10)"
    private val metresPerDegreeLat = 111_195.08

    private fun north(metres: Double) = 10.0 + metres / metresPerDegreeLat

    @Test
    fun `a point on the line is zero metres away`() {
        assertEquals(0.0, distanceToRouteMetres(10.0, 106.0005, parseRouteWkt(line)!!), 0.01)
    }

    @Test
    fun `distance to a straight segment is the perpendicular distance`() {
        assertEquals(20.0, distanceToRouteMetres(north(20.0), 106.0005, parseRouteWkt(line)!!), 0.05)
    }

    @Test
    fun `a point past the end of the segment is measured to the endpoint`() {
        assertEquals(109.5, distanceToRouteMetres(10.0, 105.999, parseRouteWkt(line)!!), 0.5)
    }

    @Test
    fun `just inside and just outside the corridor`() {
        assertTrue(corridorCheck(line, north(24.99), 106.0005) is CorridorCheck.Inside)
        assertTrue(corridorCheck(line, north(25.01), 106.0005) is CorridorCheck.Outside)
    }

    @Test
    fun `multilinestring uses the nearest part`() {
        val wkt = "MULTILINESTRING ((106 10, 106.001 10), (106 10.01, 106.001 10.01))"
        assertEquals(10.0, distanceToRouteMetres(north(10.0), 106.0005, parseRouteWkt(wkt)!!), 0.05)
    }

    @Test
    fun `keyword without a space is accepted`() {
        assertEquals(1, parseRouteWkt("LINESTRING(106 10,106.001 10)")!!.size)
    }

    @Test
    fun `unusable geometry is Unknown, never a crash`() {
        listOf(null, "", "POINT (106 10)", "LINESTRING EMPTY", "LINESTRING (106 10)", "LINESTRING (a b, c d)")
            .forEach { wkt ->
                assertNull(parseRouteWkt(wkt))
                assertEquals(CorridorCheck.Unknown, corridorCheck(wkt, 10.0, 106.0))
            }
    }

    @Test
    fun `distance between two points`() {
        assertEquals(20.0, distanceMetres(10.0, 106.0, north(20.0), 106.0), 0.05)
    }
}
