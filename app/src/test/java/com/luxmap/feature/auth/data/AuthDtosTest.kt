package com.luxmap.feature.auth.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthDtosTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `decodes communes and map_bounds from a real-shaped response`() {
        val body =
            """
            {
              "user_id": "USER-1", "username": "fe1", "email": "fe1@luxmap.vn",
              "full_name": "Field Engineer 1", "role": "field_engineer",
              "commune_ids": ["COM-001"],
              "communes": [
                {
                  "commune_id": "COM-001", "name": "Xã Tân Hòa",
                  "bbox": {"min_lng": 105.8, "min_lat": 10.5, "max_lng": 105.9, "max_lat": 10.6},
                  "center": {"lat": 10.55, "lng": 105.85}
                }
              ],
              "map_bounds": {
                "bbox": {"min_lng": 105.8, "min_lat": 10.5, "max_lng": 105.9, "max_lat": 10.6},
                "center": {"lat": 10.55, "lng": 105.85}
              }
            }
            """.trimIndent()

        val result = json.decodeFromString<CurrentUserResponseDto>(body)

        assertEquals(1, result.communes.size)
        assertEquals("COM-001", result.communes[0].communeId)
        assertEquals(105.8, result.communes[0].bbox?.minLng ?: 0.0, 0.0)
        assertEquals(10.55, result.mapBounds?.center?.lat ?: 0.0, 0.0)
    }

    @Test
    fun `a commune with no frame yet decodes bbox and center as null`() {
        val body =
            """
            {
              "user_id": "USER-1", "username": "fe1", "email": "fe1@luxmap.vn",
              "full_name": "Field Engineer 1", "role": "field_engineer",
              "commune_ids": ["COM-002"],
              "communes": [
                {"commune_id": "COM-002", "name": "Xã Mới", "bbox": null, "center": null}
              ],
              "map_bounds": null
            }
            """.trimIndent()

        val result = json.decodeFromString<CurrentUserResponseDto>(body)

        assertNull(result.communes[0].bbox)
        assertNull(result.mapBounds)
    }
}
