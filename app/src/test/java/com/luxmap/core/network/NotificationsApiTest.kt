package com.luxmap.core.network

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
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

// Exercises the real kotlinx.serialization decode path against realistic server JSON -
// RealNotificationRepositoryTest mocks this interface directly, so it never actually decodes a
// response body; this is what catches a @SerialName/field-name mismatch against the real
// NotificationContracts.cs shape. Same pattern as SweepsApiTest.
class NotificationsApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: NotificationsApi

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
                .create(NotificationsApi::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `list decodes a real page body, including an item with a type the app does not recognize yet`() =
        runTest {
            server.enqueue(
                MockResponse().setBody(
                    """
                    {
                      "page": 1,
                      "page_size": 50,
                      "total": 2,
                      "unread_count": 1,
                      "items": [
                        {
                          "notification_id": "NTF-000001",
                          "type": "work_order_assigned",
                          "title": "Bạn được giao phiếu sửa chữa WO-1001",
                          "body": "WO-1001 — Thay bóng đèn. Hạn 20/10/2026.",
                          "entity_type": "work_order",
                          "entity_id": "WO-1001",
                          "created_at": "2026-10-18T08:00:00Z",
                          "read_at": null
                        },
                        {
                          "notification_id": "NTF-000002",
                          "type": "some_future_type",
                          "title": "Loại thông báo mới",
                          "body": "Chưa biết loại này",
                          "entity_type": "some_future_entity",
                          "entity_id": "X-1",
                          "created_at": "2026-10-18T09:00:00Z",
                          "read_at": "2026-10-18T09:05:00Z"
                        }
                      ]
                    }
                    """.trimIndent(),
                ),
            )

            val page = api.list()

            assertEquals(1, page.page)
            assertEquals(50, page.pageSize)
            assertEquals(2, page.total)
            assertEquals(1, page.unreadCount)
            assertEquals(2, page.items.size)
            val known = page.items[0]
            assertEquals("NTF-000001", known.notificationId)
            assertEquals("work_order_assigned", known.type)
            assertEquals("work_order", known.entityType)
            assertEquals("WO-1001", known.entityId)
            assertNull(known.readAt)
            // The unrecognized type/entity_type still decode to plain strings without throwing -
            // mapping them to NotificationType.UNKNOWN happens later, at the UI layer.
            val unknown = page.items[1]
            assertEquals("some_future_type", unknown.type)
            assertEquals("some_future_entity", unknown.entityType)
            assertEquals("2026-10-18T09:05:00Z", unknown.readAt)

            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/api/v1/notifications?page=1&page_size=50", request.path)
        }

    @Test
    fun `list sends unread_only when requested`() =
        runTest {
            server.enqueue(
                MockResponse().setBody("""{"page":1,"page_size":50,"total":0,"unread_count":0,"items":[]}"""),
            )

            api.list(unreadOnly = true)

            val request = server.takeRequest()
            assertEquals("/api/v1/notifications?unread_only=true&page=1&page_size=50", request.path)
        }

    @Test
    fun `unreadCount decodes the badge count`() =
        runTest {
            server.enqueue(MockResponse().setBody("""{"unread_count":7}"""))

            val response = api.unreadCount()

            assertEquals(7, response.unreadCount)
            assertEquals("/api/v1/notifications/unread-count", server.takeRequest().path)
        }

    @Test
    fun `markRead posts to the per-notification path`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(204))

            api.markRead("NTF-000001")

            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/api/v1/notifications/NTF-000001/read", request.path)
        }

    @Test
    fun `markAllRead posts to the read-all path`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(204))

            api.markAllRead()

            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/api/v1/notifications/read-all", request.path)
        }
}
