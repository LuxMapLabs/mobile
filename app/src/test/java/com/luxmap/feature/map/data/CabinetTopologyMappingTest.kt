package com.luxmap.feature.map.data

import com.luxmap.feature.map.data.dto.CabinetTopologyDto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CabinetTopologyMappingTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `edges keep their feeder, target pole and line`() {
        val topology =
            json
                .decodeFromString<CabinetTopologyDto>(
                    """
                    {"type":"FeatureCollection","crs_note":"EPSG:4326 (lng, lat)",
                     "unlocated_pole_count":2,"unrouted_pole_count":1,
                     "features":[{"type":"Feature",
                      "geometry":{"type":"LineString","coordinates":[[106.7,10.8],[106.71,10.81]]},
                      "properties":{"feeder_id":"F-2","segment_id":"SEG-1","branch":1,"order":1,
                       "from_id":"CAB-1","to_pole_id":"POLE-1","feeder_source":"inferred"}}]}
                    """.trimIndent(),
                ).toCabinetTopology()
        assertEquals(2, topology.unlocatedPoleCount)
        assertEquals(1, topology.unroutedPoleCount)
        val edge = topology.edges.single()
        assertEquals("F-2", edge.feederId)
        assertEquals("POLE-1", edge.toPoleId)
        assertEquals(listOf(106.7 to 10.8, 106.71 to 10.81), edge.coordinates)
    }

    @Test
    fun `an empty diagram has no edges`() {
        val topology =
            json
                .decodeFromString<CabinetTopologyDto>(
                    """{"type":"FeatureCollection","unlocated_pole_count":0,"unrouted_pole_count":0,"features":[]}""",
                ).toCabinetTopology()
        assertTrue(topology.edges.isEmpty())
    }
}
