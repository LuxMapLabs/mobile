package com.luxmap.feature.map.data

import com.luxmap.core.theme.AssetCondition
import com.luxmap.feature.map.data.dto.PoleFeatureDto
import com.luxmap.feature.map.data.dto.PoleGeometryDto
import com.luxmap.feature.map.data.dto.PolePropertiesDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PoleMarkerTest {
    private fun poleFeature(
        segmentId: String? = "SEG-1",
        powerSource: String? = "grid",
    ) = PoleFeatureDto(
        type = "Feature",
        geometry = PoleGeometryDto(type = "Point", coordinates = listOf(105.85, 10.55)),
        properties =
            PolePropertiesDto(
                poleId = "POLE-1",
                segmentId = segmentId,
                fixtureStatus = "normal",
                statusConfidence = null,
                powerSource = powerSource,
                fixtureType = null,
                lampWatt = null,
                lampCount = null,
                installDate = null,
                warrantyExpiry = null,
                warrantyStatus = "unknown",
                communeId = "COM-001",
                lastSeenAt = null,
                lastSweepId = null,
                openFaultCount = 0,
                hasIotNode = false,
                nearSensitivePoi = false,
            ),
    )

    @Test
    fun `a pole with no segment and no lamp maps to null, not a crash or a default`() {
        val marker = poleFeature(segmentId = null, powerSource = null).toPoleMarker()

        assertNull(marker.segmentId)
        assertNull(marker.powerSource)
        assertEquals("POLE-1", marker.poleId)
        assertEquals(AssetCondition.NORMAL, marker.fixtureStatus)
    }

    @Test
    fun `an orphan pole (segmentId null) never counts toward any segment`() {
        val orphan = poleFeature(segmentId = null).toPoleMarker()
        val assigned = poleFeature(segmentId = "SEG-1").toPoleMarker()
        val poles = listOf(orphan, assigned)

        val countForSeg1 = poles.count { it.segmentId == "SEG-1" }

        assertEquals(1, countForSeg1)
    }
}
