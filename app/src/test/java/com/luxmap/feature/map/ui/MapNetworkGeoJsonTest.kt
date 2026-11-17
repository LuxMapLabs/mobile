package com.luxmap.feature.map.ui

import com.luxmap.feature.map.data.CabinetFeeder
import com.luxmap.feature.map.data.CabinetMarker
import com.luxmap.feature.map.data.CabinetTopology
import com.luxmap.feature.map.data.CommuneOutline
import com.luxmap.feature.map.data.FeederMode
import com.luxmap.feature.map.data.FeederPower
import com.luxmap.feature.map.data.TopologyEdge
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class MapNetworkGeoJsonTest {
    private fun features(geoJson: String) =
        Json.parseToJsonElement(geoJson).jsonObject["features"]!!.jsonArray.map { it.jsonObject }

    private fun JsonObject.prop(name: String) = this["properties"]!!.jsonObject[name]!!.jsonPrimitive.content

    private val cabinet =
        CabinetMarker(
            cabinetId = "CAB-1",
            name = "Tủ chợ",
            communeId = "COM-1",
            lat = 10.8,
            lng = 106.7,
            feeders =
                listOf(
                    CabinetFeeder("F-ON", 1, FeederMode.AUTO, FeederPower.ON),
                    CabinetFeeder("F-OFF", 2, FeederMode.OFF, FeederPower.OFF),
                ),
            iotNodeCount = 0,
        )

    @Test
    fun `cabinets carry their id and power-off flag`() {
        val feature = features(listOf(cabinet).toCabinetGeoJson()).single()
        assertEquals("CAB-1", feature.prop("cabinet_id"))
        assertEquals("true", feature.prop("has_power_off"))
        assertEquals("[106.7,10.8]", feature["geometry"]!!.jsonObject["coordinates"].toString())
    }

    @Test
    fun `diagram edges take the power state of their feeder`() {
        val topology =
            CabinetTopology(
                edges =
                    listOf(
                        TopologyEdge("F-ON", "P-1", listOf(106.7 to 10.8, 106.71 to 10.81)),
                        TopologyEdge("F-OFF", "P-2", listOf(106.7 to 10.8, 106.72 to 10.82)),
                        TopologyEdge("F-UNKNOWN", "P-3", listOf(106.7 to 10.8, 106.73 to 10.83)),
                    ),
                unlocatedPoleCount = 0,
                unroutedPoleCount = 0,
            )
        val powers = features(topology.toTopologyGeoJson(cabinet)).map { it.prop("power") }
        assertEquals(listOf("on", "off", "unknown"), powers)
    }

    @Test
    fun `communes become multipolygons with their id`() {
        val ring = listOf(106.0 to 10.0, 106.1 to 10.0, 106.1 to 10.1, 106.0 to 10.0)
        val feature =
            features(
                listOf(CommuneOutline("COM-1", "Phước Hiệp", listOf(listOf(ring)))).toCommuneGeoJson(),
            ).single()
        assertEquals("COM-1", feature.prop("commune_id"))
        assertEquals("MultiPolygon", feature["geometry"]!!.jsonObject["type"]!!.jsonPrimitive.content)
    }
}
