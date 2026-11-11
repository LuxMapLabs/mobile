package com.luxmap.feature.map.data

import com.luxmap.core.theme.AssetCondition
import com.luxmap.feature.map.data.dto.PoleDetailCurrentStatusDto
import com.luxmap.feature.map.data.dto.PoleDetailDto
import com.luxmap.feature.map.data.dto.PoleDetailFixtureDto
import com.luxmap.feature.map.data.dto.PoleDetailIotNodeDto
import com.luxmap.feature.map.data.dto.PoleDetailLocationDto
import com.luxmap.feature.map.data.dto.PoleDetailLuminancePointDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun minimalDto() =
    PoleDetailDto(
        poleId = "POLE-1",
        segmentId = null,
        segmentName = null,
        communeId = "COM-001",
        location = null,
        fixture = null,
        currentStatus = PoleDetailCurrentStatusDto(fixtureStatus = "unknown"),
        iotNode = null,
        note = null,
    )

class PoleDetailMappingTest {
    @Test
    fun `maps a pole with no location, no fixture and no iot node to all-null fields`() {
        val detail = minimalDto().toPoleDetail()

        assertNull(detail.lat)
        assertNull(detail.lng)
        assertNull(detail.fixtureType)
        assertNull(detail.powerSource)
        assertNull(detail.lampWatt)
        assertNull(detail.warrantyExpiry)
        assertTrue(!detail.hasIotNode)
        assertNull(detail.iotNodeStatus)
        assertEquals(AssetCondition.UNKNOWN, detail.fixtureStatus)
    }

    @Test
    fun `maps a fully populated pole`() {
        val dto =
            minimalDto().copy(
                location = PoleDetailLocationDto(lat = 10.97, lng = 106.49),
                fixture =
                    PoleDetailFixtureDto(
                        fixtureType = "led_road_lamp",
                        powerSource = "grid",
                        lampWatt = 100,
                        installDate = "2022-03-24",
                        warrantyExpiry = "2024-03-24",
                    ),
                currentStatus = PoleDetailCurrentStatusDto(fixtureStatus = "normal"),
                iotNode =
                    PoleDetailIotNodeDto(
                        nodeId = "NODE-1",
                        nodeStatus = "online",
                        lastReportAt = "2026-08-20T06:00:00Z",
                    ),
                note = "Cột gần quán tạp hoá",
            )

        val detail = dto.toPoleDetail()

        assertEquals(10.97, detail.lat!!, 0.0)
        assertEquals(106.49, detail.lng!!, 0.0)
        assertEquals("led_road_lamp", detail.fixtureType)
        assertEquals(100, detail.lampWatt)
        assertEquals(AssetCondition.NORMAL, detail.fixtureStatus)
        assertTrue(detail.hasIotNode)
        assertEquals("online", detail.iotNodeStatus)
        assertEquals("Cột gần quán tạp hoá", detail.note)
    }

    @Test
    fun `keeps a null baseline_ratio null on each luminance point instead of defaulting it`() {
        val dto =
            minimalDto().copy(
                luminanceHistory =
                    listOf(
                        PoleDetailLuminancePointDto(
                            observedAt = "2026-08-19T20:00:00Z",
                            baselineRatio = null,
                            classifiedAs = "unknown",
                        ),
                    ),
            )

        val detail = dto.toPoleDetail()

        assertNull(detail.luminanceHistory.single().baselineRatio)
    }
}
