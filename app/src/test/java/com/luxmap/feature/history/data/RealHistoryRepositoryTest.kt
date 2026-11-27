package com.luxmap.feature.history.data

import com.luxmap.core.network.UserApi
import com.luxmap.core.network.WorkOrdersApi
import com.luxmap.core.network.dto.PagedResultDto
import com.luxmap.core.security.TokenStore
import com.luxmap.feature.auth.data.CurrentUserResponseDto
import com.luxmap.feature.history.data.dao.HistoryCacheDao
import com.luxmap.feature.history.data.entity.LocalHistoryItemEntity
import com.luxmap.feature.workorder.data.dto.WorkOrderItemDto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

private const val CLOSED = "done,verified,cancelled"

private fun currentUser() =
    CurrentUserResponseDto(
        userId = "U-1",
        username = "nv0125",
        email = "nv0125@example.com",
        fullName = "Nguyễn Văn A",
        role = "field_engineer",
        communeIds = listOf("COM-001"),
    )

private fun dto(
    id: String,
    kind: String = "repair",
    status: String = "verified",
) = WorkOrderItemDto(
    workOrderId = id,
    title = "Lệnh $id",
    communeId = "COM-001",
    taskKind = kind,
    caseId = id,
    faultIds = emptyList(),
    woStatus = status,
    createdAt = "2026-11-01T00:00:00Z",
    updatedAt = "2026-11-20T08:00:00Z",
    scheduledDate = "2026-11-19",
)

private fun entity(id: String) =
    LocalHistoryItemEntity(
        ownerUserId = "U-1",
        workOrderId = id,
        title = "Lệnh $id",
        taskKind = "repair",
        woStatus = "verified",
        communeId = "COM-001",
        updatedAt = "2026-11-20T08:00:00Z",
        scheduledDate = "2026-11-19",
        position = 0,
    )

class RealHistoryRepositoryTest {
    private val users = mockk<UserApi>()
    private val orders = mockk<WorkOrdersApi>()
    private val cache = mockk<HistoryCacheDao>(relaxed = true)
    private val tokens = mockk<TokenStore>(relaxed = true)
    private val repository = RealHistoryRepository(users, orders, cache, tokens)

    init {
        coEvery { tokens.cachedUserId() } returns "U-1"
        coEvery { tokens.identitySessionId() } returns "session"
        coEvery { tokens.saveCachedUserIdForSession("U-1", "session") } returns true
        coEvery { users.me() } returns currentUser()
    }

    @Test
    fun `fetches closed work orders of every kind for the current user and caches them in order`() =
        runTest {
            coEvery { orders.list("U-1", null, CLOSED) } returns
                PagedResultDto(1, 200, 2, listOf(dto("WO-2", "survey", "cancelled"), dto("WO-1")))

            val result = repository.observeHistory().first()

            assertEquals(listOf("WO-2", "WO-1"), result.items.map { it.workOrderId })
            assertEquals("survey", result.items.first().taskKind)
            assertEquals(false, result.isStale)
            coVerify { cache.replaceAll("U-1", match { rows -> rows.map { it.position } == listOf(0, 1) }) }
        }

    @Test
    fun `empty server list is a fresh empty result and clears the cache`() =
        runTest {
            coEvery { orders.list("U-1", null, CLOSED) } returns PagedResultDto(1, 200, 0, emptyList())

            val result = repository.observeHistory().first()

            assertEquals(HistoryResult(emptyList(), isStale = false), result)
            coVerify { cache.replaceAll("U-1", emptyList()) }
        }

    @Test
    fun `network failure falls back to the cached list marked stale`() =
        runTest {
            coEvery { orders.list("U-1", null, CLOSED) } throws IOException("offline")
            coEvery { cache.all("U-1") } returns listOf(entity("WO-1"))

            val result = repository.observeHistory().first()

            assertEquals(listOf("WO-1"), result.items.map { it.workOrderId })
            assertEquals(true, result.isStale)
        }

    @Test
    fun `network failure with an empty cache surfaces the error`() =
        runTest {
            coEvery { orders.list("U-1", null, CLOSED) } throws IOException("offline")
            coEvery { cache.all("U-1") } returns emptyList()

            val error = runCatching { repository.observeHistory().first() }.exceptionOrNull()

            assertTrue(error is IOException)
        }

    @Test
    fun `auth failure is never answered from the cache`() =
        runTest {
            val unauthorized = HttpException(Response.error<Any>(401, "".toResponseBody()))
            coEvery { orders.list("U-1", null, CLOSED) } throws unauthorized
            coEvery { cache.all("U-1") } returns listOf(entity("WO-1"))

            val error = runCatching { repository.observeHistory().first() }.exceptionOrNull()

            assertTrue(error is HttpException)
            coVerify(exactly = 0) { cache.all(any()) }
        }
}
