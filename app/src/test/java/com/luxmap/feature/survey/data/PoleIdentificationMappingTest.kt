package com.luxmap.feature.survey.data

import com.luxmap.feature.survey.data.dto.PoleListItemDto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PoleIdentificationMappingTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `present fields parse and survive DTO to Room to model round trip`() {
        val dto =
            json.decodeFromString<PoleListItemDto>(
                """{"pole_id":"P1","position":2,"location":{"lat":10.5,"lng":106.5},
                "fixture_status":"normal","segment_id":"S1","note":"Trước cổng trường",
                "fixture_type":"led","lamp_watt":100,"lamp_count":2,"fixture_count":1,
                "near_sensitive_poi":true}""",
            )
        val expected = PoleListItem("P1", 2, 10.5, 106.5, "normal", "S1", "Trước cổng trường", "led", 100, 2, 1, true)
        assertEquals(expected, dto.toDomain())
        val entity = dto.toDomain().toEntity("W1")
        assertEquals("W1", entity.workOrderId)
        assertEquals(expected, entity.toDomain())
    }

    @Test
    fun `absent and explicit null optional fields keep older payloads and caches readable`() {
        val absent = json.decodeFromString<PoleListItemDto>("""{"pole_id":"P","fixture_status":"unknown"}""")
        val explicitNull =
            json.decodeFromString<PoleListItemDto>(
                """{"pole_id":"P","fixture_status":"unknown","position":null,"location":null,
                "segment_id":null,"note":null,"fixture_type":null,"lamp_watt":null,
                "lamp_count":null,"fixture_count":null,"near_sensitive_poi":null}""",
            )
        assertEquals(absent, explicitNull)
        val model = absent.toDomain()
        assertEquals(model, model.toEntity("W").toDomain())
        assertNull(model.segmentId)
        assertNull(model.note)
        assertNull(model.nearSensitivePoi)
    }

    @Test
    fun `false and zero values are retained`() {
        val dto =
            json.decodeFromString<PoleListItemDto>(
                """{"pole_id":"P","fixture_status":"unknown","fixture_count":0,"lamp_count":0,
                "near_sensitive_poi":false}""",
            )
        assertEquals(0, dto.toDomain().toEntity("W").toDomain().fixtureCount)
        assertEquals(false, dto.toDomain().toEntity("W").toDomain().nearSensitivePoi)
    }
}
