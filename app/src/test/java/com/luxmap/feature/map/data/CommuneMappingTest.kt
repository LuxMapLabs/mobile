package com.luxmap.feature.map.data

import com.luxmap.feature.map.data.dto.CommuneFeatureCollectionDto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class CommuneMappingTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `a multipolygon keeps every ring as lng-lat pairs`() {
        val commune =
            json
                .decodeFromString<CommuneFeatureCollectionDto>(
                    """
                    {"type":"FeatureCollection","features":[{"type":"Feature",
                     "geometry":{"type":"MultiPolygon","coordinates":[[[[106.0,10.0],[106.1,10.0],[106.1,10.1],[106.0,10.0]]]]},
                     "properties":{"commune_id":"COM-1","name":"Phước Hiệp"}}]}
                    """.trimIndent(),
                ).features
                .single()
                .toCommuneOutline()
        assertEquals("COM-1", commune.communeId)
        assertEquals("Phước Hiệp", commune.name)
        assertEquals(1, commune.polygons.size)
        assertEquals(106.1 to 10.1, commune.polygons.single().single()[2])
    }
}
