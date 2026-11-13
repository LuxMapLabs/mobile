package com.luxmap.core.network

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

class WorkOrdersApiAttachmentTest {
    private lateinit var server: MockWebServer
    private lateinit var api: WorkOrdersApi

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        api = Retrofit.Builder().baseUrl(server.url("/api/v1/")).build().create(WorkOrdersApi::class.java)
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `download_url replaces the base path and returns the raw bytes`() =
        runTest {
            server.enqueue(MockResponse().setBody(Buffer().write(byteArrayOf(1, 2, 3))))
            val body = api.downloadAttachment("/api/v1/work-orders/WO-1/attachments/A-1")
            assertEquals(listOf<Byte>(1, 2, 3), body.bytes().toList())
            assertEquals("/api/v1/work-orders/WO-1/attachments/A-1", server.takeRequest().path)
        }
}
