package com.luxmap.core.network

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

class MapApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: MapApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val json = Json { ignoreUnknownKeys = true }
        api =
            Retrofit.Builder()
                .baseUrl(server.url("/"))
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(MapApi::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `poles sends bbox as a single query param and parses the FeatureCollection`() =
        runTest {
            server.enqueue(
                MockResponse().setBody(
                    """
                    {"type":"FeatureCollection","features":[
                      {"type":"Feature","geometry":{"type":"Point","coordinates":[105.85,10.55]},
                       "properties":{"pole_id":"POLE-1","segment_id":null,"fixture_status":"normal",
                       "warranty_status":"unknown","commune_id":"COM-001","open_fault_count":0,
                       "has_iot_node":false,"near_sensitive_poi":false}}
                    ]}
                    """.trimIndent(),
                ),
            )

            val result = api.poles(bbox = "105.8,10.5,105.9,10.6")

            assertEquals(1, result.features.size)
            assertEquals("POLE-1", result.features[0].properties.poleId)
            val request = server.takeRequest()
            assertEquals("/api/v1/map/poles?bbox=105.8%2C10.5%2C105.9%2C10.6", request.path)
        }

    @Test
    fun `poleDetail requests the pole-specific path and parses the detail shape`() =
        runTest {
            server.enqueue(
                MockResponse().setBody(
                    """
                    {"pole_id":"POLE-1","segment_id":null,"segment_name":null,"commune_id":"COM-001",
                     "location":null,"fixture":null,
                     "current_status":{"fixture_status":"unknown","status_confidence":null,"determined_at":null,"source_channel":null},
                     "iot_node":null,"luminance_history":[],"runtime_history":[],"open_faults":[],"recent_frames":[],"note":null}
                    """.trimIndent(),
                ),
            )

            val result = api.poleDetail(poleId = "POLE-1")

            assertEquals("POLE-1", result.poleId)
            assertEquals(null, result.location)
            assertEquals(null, result.fixture)
            val request = server.takeRequest()
            assertEquals("/api/v1/map/poles/POLE-1", request.path)
        }

    @Test
    fun `segments sends bbox as a single query param and parses the FeatureCollection`() =
        runTest {
            server.enqueue(
                MockResponse().setBody(
                    """
                    {"type":"FeatureCollection","features":[
                      {"type":"Feature","geometry":{"type":"LineString","coordinates":[[105.85,10.55],[105.86,10.56]]},
                       "properties":{"segment_id":"SEG-1","segment_name":"Đường A","road_class":"commune",
                       "length_m":123.4,"pole_count":5,"has_active_segment_fault":false}}
                    ]}
                    """.trimIndent(),
                ),
            )

            val result = api.segments(bbox = "105.8,10.5,105.9,10.6")

            assertEquals(1, result.features.size)
            assertEquals(123.4, result.features[0].properties.lengthM, 0.0)
            val request = server.takeRequest()
            assertEquals("/api/v1/map/segments?bbox=105.8%2C10.5%2C105.9%2C10.6", request.path)
        }
}
