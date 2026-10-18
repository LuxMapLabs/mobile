package com.luxmap.feature.notification.data

import com.luxmap.core.network.NotificationsApi
import com.luxmap.feature.notification.data.dto.NotificationItemDto
import com.luxmap.feature.notification.data.dto.NotificationPageDto
import com.luxmap.feature.notification.data.dto.UnreadCountDto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

private fun httpException(code: Int): HttpException =
    HttpException(Response.error<Any>(code, "".toResponseBody("application/json".toMediaType())))

private fun dto(
    id: String = "NTF-000001",
    type: String = "work_order_assigned",
    entityType: String = "work_order",
    entityId: String = "WO-1001",
    readAt: String? = null,
) = NotificationItemDto(
    notificationId = id,
    type = type,
    title = "Bạn được giao phiếu sửa chữa WO-1001",
    body = "WO-1001 — Thay bóng đèn. Hạn 20/10/2026.",
    entityType = entityType,
    entityId = entityId,
    createdAt = "2026-10-18T08:00:00Z",
    readAt = readAt,
)

class RealNotificationRepositoryTest {
    @Test
    fun `list maps the page DTO into the domain page in the same order`() =
        runTest {
            val api = mockk<NotificationsApi>()
            coEvery { api.list(unreadOnly = null, page = 1, pageSize = 50) } returns
                NotificationPageDto(page = 1, pageSize = 50, total = 1, unreadCount = 1, items = listOf(dto()))
            val repository = RealNotificationRepository(api)

            val result = repository.list()

            assertTrue(result.isSuccess)
            assertEquals(
                NotificationPage(
                    items =
                        listOf(
                            NotificationItem(
                                "NTF-000001",
                                "work_order_assigned",
                                "Bạn được giao phiếu sửa chữa WO-1001",
                                "WO-1001 — Thay bóng đèn. Hạn 20/10/2026.",
                                "work_order",
                                "WO-1001",
                                "2026-10-18T08:00:00Z",
                                null,
                            ),
                        ),
                    total = 1,
                    unreadCount = 1,
                ),
                result.getOrNull(),
            )
        }

    @Test
    fun `list passes unreadOnly through to the API`() =
        runTest {
            val api = mockk<NotificationsApi>()
            coEvery { api.list(unreadOnly = true, page = 1, pageSize = 50) } returns
                NotificationPageDto(page = 1, pageSize = 50, total = 0, unreadCount = 0, items = emptyList())
            val repository = RealNotificationRepository(api)

            repository.list(unreadOnly = true)

            coVerify { api.list(unreadOnly = true, page = 1, pageSize = 50) }
        }

    @Test
    fun `list returns failure when the backend rejects the request`() =
        runTest {
            val api = mockk<NotificationsApi>()
            coEvery { api.list(unreadOnly = null, page = 1, pageSize = 50) } throws httpException(500)
            val repository = RealNotificationRepository(api)

            assertTrue(repository.list().isFailure)
        }

    @Test
    fun `unreadCount returns the count from the dedicated endpoint`() =
        runTest {
            val api = mockk<NotificationsApi>()
            coEvery { api.unreadCount() } returns UnreadCountDto(unreadCount = 7)
            val repository = RealNotificationRepository(api)

            assertEquals(7, repository.unreadCount().getOrNull())
        }

    @Test
    fun `markRead calls the per-notification read endpoint`() =
        runTest {
            val api = mockk<NotificationsApi>(relaxed = true)
            val repository = RealNotificationRepository(api)

            val result = repository.markRead("NTF-000001")

            assertTrue(result.isSuccess)
            coVerify { api.markRead("NTF-000001") }
        }

    @Test
    fun `markAllRead calls the read-all endpoint`() =
        runTest {
            val api = mockk<NotificationsApi>(relaxed = true)
            val repository = RealNotificationRepository(api)

            val result = repository.markAllRead()

            assertTrue(result.isSuccess)
            coVerify { api.markAllRead() }
        }
}
