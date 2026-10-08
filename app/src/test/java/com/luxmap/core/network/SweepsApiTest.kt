package com.luxmap.core.network

import com.luxmap.core.network.dto.ClipManifestDto
import com.luxmap.core.network.dto.CreateSweepRequestDto
import com.luxmap.core.network.dto.SubmitSweepRequestDto
import com.luxmap.core.network.dto.SweepManifestDto
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory

class SweepsApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: SweepsApi

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
                .create(SweepsApi::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `create posts work_order_id and client_op_id as given`() =
        runTest {
            server.enqueue(MockResponse().setBody("""{"sweep_id":"SWEEP-SERVER-1"}"""))

            val response =
                api.create(
                    CreateSweepRequestDto(
                        workOrderId = "WO-1",
                        clientOpId = "SESSION-1",
                        bootSessionId = "BOOT-1",
                        elapsedAnchorNs = "1",
                        utcAnchor = "2026-10-08T00:00:00Z",
                        utcUncertaintyMs = 50.0,
                        dataSource = "field",
                        startedElapsedNs = "1",
                    ),
                )

            assertEquals("SWEEP-SERVER-1", response.sweepId)
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/api/v1/sweeps", request.path)
            val body = request.body.readUtf8()
            assertEquals(true, body.contains(""""work_order_id":"WO-1""""))
            assertEquals(true, body.contains(""""client_op_id":"SESSION-1""""))
        }

    @Test
    fun `uploadClip sends the X-Content-SHA256 header and the path params`() =
        runTest {
            server.enqueue(MockResponse().setBody("""{"clip_no":0,"sha256":"abc"}"""))

            api.uploadClip(
                sweepId = "SWEEP-1",
                clipNo = 0,
                sha256 = "abc123",
                body = byteArrayOf(1, 2, 3).toRequestBody("video/mp4".toMediaType()),
            )

            val request = server.takeRequest()
            assertEquals("PUT", request.method)
            assertEquals("/api/v1/sweeps/SWEEP-1/clips/0", request.path)
            assertEquals("abc123", request.getHeader("X-Content-SHA256"))
        }

    @Test
    fun `uploadRaw does not require X-Content-SHA256`() =
        runTest {
            server.enqueue(MockResponse().setBody("""{"kind":"gps_track","sha256":"abc"}"""))

            api.uploadRaw(
                sweepId = "SWEEP-1",
                kind = "gps_track",
                body = "line\n".toRequestBody("application/x-ndjson".toMediaType()),
            )

            val request = server.takeRequest()
            assertEquals("/api/v1/sweeps/SWEEP-1/raw/gps_track", request.path)
            assertNull(request.getHeader("X-Content-SHA256"))
        }

    @Test
    fun `submit posts the manifest body`() =
        runTest {
            server.enqueue(MockResponse().setBody("""{"sweep_id":"SWEEP-1"}"""))

            api.submit(
                sweepId = "SWEEP-1",
                body =
                    SubmitSweepRequestDto(
                        clientOpId = "SUBMIT-1",
                        endedElapsedNs = "2",
                        manifest =
                            SweepManifestDto(
                                clips = listOf(ClipManifestDto(clipNo = 0, sha256 = "a")),
                                gpsHash = "g",
                                luxHash = "l",
                                configHash = "c",
                            ),
                    ),
            )

            val request = server.takeRequest()
            assertEquals("/api/v1/sweeps/SWEEP-1/submit", request.path)
            val body = request.body.readUtf8()
            assertEquals(true, body.contains(""""clip_no":0"""))
            assertEquals(true, body.contains(""""gps_hash":"g""""))
        }
}
