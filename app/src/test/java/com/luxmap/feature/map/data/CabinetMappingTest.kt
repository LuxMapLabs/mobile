package com.luxmap.feature.map.data

import com.luxmap.feature.map.data.dto.CabinetFeatureCollectionDto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CabinetMappingTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun decode(body: String) =
        json.decodeFromString<CabinetFeatureCollectionDto>(body).features.map {
            it.toCabinetMarker()
        }

    @Test
    fun `a cabinet with one unpowered feeder is a power-off cabinet`() {
        val cabinet =
            decode(
                """
                {"type":"FeatureCollection","features":[{"type":"Feature",
                 "geometry":{"type":"Point","coordinates":[106.7,10.8]},
                 "properties":{"cabinet_id":"CAB-1","cabinet_name":"Tủ chợ","commune_id":"COM-1",
                  "feeder_ids":["F-1","F-2"],
                  "feeders":[{"feeder_id":"F-1","relay_no":1,"control_mode":"auto","power_state":"on"},
                             {"feeder_id":"F-2","relay_no":2,"control_mode":"off","power_state":"off"}],
                  "iot_node_ids":["IOT-1"],"iot_node_id":"IOT-1","photo_count":0}}]}
                """.trimIndent(),
            ).single()
        assertEquals("CAB-1", cabinet.cabinetId)
        assertEquals(10.8, cabinet.lat, 0.0)
        assertEquals(106.7, cabinet.lng, 0.0)
        assertEquals(listOf(FeederMode.AUTO, FeederMode.OFF), cabinet.feeders.map { it.mode })
        assertEquals(listOf(FeederPower.ON, FeederPower.OFF), cabinet.feeders.map { it.power })
        assertEquals(1, cabinet.iotNodeCount)
        assertTrue(cabinet.hasPowerOff)
    }

    @Test
    fun `no feeders and unknown values map safely`() {
        val cabinet =
            decode(
                """
                {"type":"FeatureCollection","features":[{"type":"Feature",
                 "geometry":{"type":"Point","coordinates":[106.7,10.8]},
                 "properties":{"cabinet_id":"CAB-2","cabinet_name":"Tủ đồng","commune_id":"COM-1",
                  "feeder_ids":[],"feeders":[{"feeder_id":"F-9","power_state":"weird"}],"iot_node_ids":[]}}]}
                """.trimIndent(),
            ).single()
        assertEquals(FeederMode.UNKNOWN, cabinet.feeders.single().mode)
        assertEquals(FeederPower.UNKNOWN, cabinet.feeders.single().power)
        assertFalse(cabinet.hasPowerOff)
        assertEquals(0, cabinet.iotNodeCount)
    }
}
