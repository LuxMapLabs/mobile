package com.luxmap.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class RoutesTest {
    @Test
    fun mapRouteWithoutParameters_returnsBaseRoute() {
        assertEquals("map", Routes.Map.createRoute())
    }

    @Test
    fun mapRouteWithPoleId_includesFocusPoleId() {
        assertEquals("map?focusPoleId=P-101", Routes.Map.createRoute(focusPoleId = "P-101"))
    }

    @Test
    fun mapRouteWithCoordinatesAndProposed_includesAllParameters() {
        val route =
            Routes.Map.createRoute(
                focusPoleId = "P-102",
                focusLat = 10.971234,
                focusLng = 106.497567,
                isProposed = true,
            )
        assertEquals(
            "map?focusPoleId=P-102&focusLat=10.971234&focusLng=106.497567&isProposed=true",
            route,
        )
    }
}
