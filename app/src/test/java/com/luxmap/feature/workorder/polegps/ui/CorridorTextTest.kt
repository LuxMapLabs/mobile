package com.luxmap.feature.workorder.polegps.ui

import com.luxmap.feature.workorder.polegps.data.CorridorCheck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CorridorTextTest {
    @Test
    fun `inside shows the distance against the limit`() {
        assertEquals("Cách tuyến 12.3 m / 25.0 m", corridorListLabel(CorridorCheck.Inside(12.34)))
    }

    @Test
    fun `outside is labelled as outside the corridor`() {
        assertEquals(
            "Ngoài hành lang tuyến, cách tuyến 31.0 m / 25.0 m",
            corridorListLabel(CorridorCheck.Outside(31.0)),
        )
    }

    @Test
    fun `unknown shows nothing in the list but explains itself on the measure screen`() {
        assertNull(corridorListLabel(CorridorCheck.Unknown))
        assertEquals("Chưa kiểm tra được hành lang tuyến", corridorPreviewLabel(CorridorCheck.Unknown))
    }

    @Test
    fun `previous distance is optional`() {
        assertNull(previousDistanceLabel(null))
        assertEquals("Lệch vị trí cũ 40.0 m", previousDistanceLabel(40.0))
    }
}
