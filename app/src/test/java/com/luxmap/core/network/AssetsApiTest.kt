package com.luxmap.core.network

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.luxmap.feature.polelocation.data.dto.SetPoleLocationRequestDto
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

class AssetsApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: AssetsApi

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
                .create(AssetsApi::class.java)
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `listPoles sends missing_location and parses an item without a location`() =
        runTest {
            server.enqueue(
                MockResponse().setBody(
                    """
                    {"page":1,"page_size":200,"total":1,"items":[
                      {"pole_id":"POLE-1","external_ref":"REF-1","segment_id":"SEG-1",
                       "commune_name":"Xã A","location":null,"note":"cột bê tông"}
                    ]}
                    """.trimIndent(),
                ),
            )

            val result = api.listPoles(missingLocation = true)

            assertEquals(1, result.items.size)
            assertEquals("POLE-1", result.items[0].poleId)
            assertNull(result.items[0].location)
            val request = server.takeRequest()
            assertEquals("/api/v1/assets/poles?missing_location=true&page=1&page_size=200", request.path)
        }

    @Test
    fun `setPoleLocation puts to the pole-specific path and parses the response`() =
        runTest {
            server.enqueue(
                MockResponse().setBody(
                    """
                    {"pole_id":"POLE-1","lat":10.5,"lng":106.5,"moved_m":3.2,
                     "updated_at":"2026-11-15T01:00:00Z","updated_by":"USR-1","updated_by_name":"crew"}
                    """.trimIndent(),
                ),
            )

            val result = api.setPoleLocation("POLE-1", SetPoleLocationRequestDto(10.5, 106.5))

            assertEquals(10.5, result.lat, 0.0)
            assertEquals(3.2, result.movedM!!, 0.0)
            val request = server.takeRequest()
            assertEquals("PUT", request.method)
            assertEquals("/api/v1/assets/poles/POLE-1/location", request.path)
            assertEquals("""{"lat":10.5,"lng":106.5}""", request.body.readUtf8())
        }
}
