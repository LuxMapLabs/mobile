package com.luxmap.core.common

import org.junit.Assert.assertEquals
import org.junit.Test

class MapIntentsTest {
    @Test
    fun `builds a geo URI with 6 decimal places and a query for the pin label`() {
        assertEquals("geo:10.964558,106.495788?q=10.964558,106.495788", geoUri(10.964558, 106.495788))
    }

    @Test
    fun `rounds to 6 decimal places`() {
        assertEquals("geo:10.123457,106.000000?q=10.123457,106.000000", geoUri(10.1234567, 106.0))
    }
}
