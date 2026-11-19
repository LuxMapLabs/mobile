package com.luxmap.feature.survey.data

import com.luxmap.core.network.UserApi
import com.luxmap.core.network.WorkOrdersApi
import com.luxmap.core.network.dto.PagedResultDto
import com.luxmap.core.security.TokenStore
import com.luxmap.feature.auth.data.CurrentUserResponseDto
import com.luxmap.feature.survey.data.dao.PoleGpsOrderCacheDao
import com.luxmap.feature.survey.data.dao.SurveyRouteCacheDao
import com.luxmap.feature.survey.data.entity.LocalAssignedPoleGpsOrderEntity
import com.luxmap.feature.survey.data.entity.LocalAssignedSurveyRouteEntity
import com.luxmap.feature.workorder.data.dto.WorkOrderItemDto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

private fun currentUser(userId: String = "U-1") =
    CurrentUserResponseDto(
        userId = userId,
        username = "nv0125",
        email = "nv0125@example.com",
        fullName = "Nguyễn Văn A",
        role = "field_engineer",
        communeIds = listOf("COM-001"),
    )

private fun workOrderItemDto(workOrderId: String) =
    WorkOrderItemDto(
        workOrderId = workOrderId,
        title = "Khảo sát đường A",
        communeId = "COM-001",
        taskKind = "survey",
        caseId = workOrderId,
        faultIds = emptyList(),
        woStatus = "assigned",
        createdAt = "2026-10-01T00:00:00Z",
        updatedAt = "2026-10-01T00:00:00Z",
        dueDate = null,
        scheduledDate = "2026-10-01",
    )

private fun route(workOrderId: String = "WO-1") =
    AssignedSurveyRoute(
        workOrderId = workOrderId,
        title = "Khảo sát đường A",
        communeId = "COM-001",
        woStatus = "assigned",
        dueDate = null,
        scheduledDate = "2026-10-01",
    )

private fun routeEntity(workOrderId: String = "WO-1") =
    LocalAssignedSurveyRouteEntity(
        workOrderId = workOrderId,
        title = "Khảo sát đường A",
        communeId = "COM-001",
        woStatus = "assigned",
        dueDate = null,
        scheduledDate = "2026-10-01",
        ownerUserId = "U-1",
    )

class RealSurveyRepositoryTest {
    private val gpsCache = mockk<PoleGpsOrderCacheDao>(relaxed = true)
    private val tokens = mockk<TokenStore>(relaxed = true)

    init {
        coEvery { tokens.cachedUserId() } returns "U-1"
        coEvery { tokens.identitySessionId() } returns "session"
        coEvery { tokens.saveCachedUserIdForSession(any(), "session") } returns true
    }

    @Test
    fun `live empty GPS response replaces only own GPS list`() =
        runTest {
            val users = mockk<UserApi>()
            val orders = mockk<WorkOrdersApi>()
            val surveys = mockk<SurveyRouteCacheDao>(relaxed = true)
            coEvery { users.me() } returns currentUser()
            coEvery { orders.list("U-1", "pole_gps_update") } returns PagedResultDto(1, 200, 0, emptyList())
            val result =
                RealSurveyRepository(users, orders, surveys, gpsCache, tokens)
                    .observeAssignedPoleGpsOrders().first()
            assertEquals(AssignedRoutesResult(emptyList(), false), result)
            coVerify(exactly = 1) { gpsCache.replaceAll("U-1", emptyList()) }
            coVerify(exactly = 0) { surveys.replaceAll(any()) }
            coVerify(exactly = 0) { surveys.deleteAll() }
        }

    @Test
    fun `GPS authorization failure never falls back to cache`() =
        runTest {
            val users = mockk<UserApi>()
            val error =
                retrofit2.HttpException(
                    retrofit2.Response.error<Any>(
                        403,
                        okhttp3.ResponseBody.create(null, "forbidden"),
                    ),
                )
            coEvery { users.me() } throws error
            try {
                RealSurveyRepository(users, mockk(), mockk(), gpsCache, tokens).observeAssignedPoleGpsOrders().first()
                error("expected HTTP error")
            } catch (e: retrofit2.HttpException) {
                assertEquals(403, e.code())
            }
            coVerify(exactly = 0) { gpsCache.all(any()) }
        }

    @Test
    fun `old auth response cannot publish list after account switch`() =
        runTest {
            val users = mockk<UserApi>()
            val orders = mockk<WorkOrdersApi>()
            coEvery { users.me() } returns currentUser("A")
            coEvery { tokens.saveCachedUserIdForSession("A", "session") } returns false
            try {
                RealSurveyRepository(users, orders, mockk(), gpsCache, tokens).observeAssignedPoleGpsOrders().first()
                error("expected session cancellation")
            } catch (_: kotlinx.coroutines.CancellationException) {
                coVerify(exactly = 0) { orders.list(any(), any(), any(), any(), any()) }
                coVerify(exactly = 0) { gpsCache.replaceAll(any(), any()) }
            }
        }

    @Test
    fun `GPS complete pagination replaces only current owner after final page`() =
        runTest {
            val users = mockk<UserApi>()
            val orders = mockk<WorkOrdersApi>()
            coEvery { users.me() } returns currentUser()
            coEvery { orders.list("U-1", "pole_gps_update", page = 1) } returns
                PagedResultDto(1, 1, 2, listOf(workOrderItemDto("WO-1")))
            coEvery { orders.list("U-1", "pole_gps_update", page = 2) } returns
                PagedResultDto(2, 1, 2, listOf(workOrderItemDto("WO-2")))
            val result =
                RealSurveyRepository(users, orders, mockk(relaxed = true), gpsCache, tokens)
                    .observeAssignedPoleGpsOrders().first()
            assertEquals(listOf("WO-1", "WO-2"), result.routes.map { it.workOrderId })
            coVerify(exactly = 1) { gpsCache.replaceAll("U-1", match { it.size == 2 }) }
        }

    @Test
    fun `GPS interrupted pagination retains previous cache`() =
        runTest {
            val users = mockk<UserApi>()
            val orders = mockk<WorkOrdersApi>()
            coEvery { users.me() } returns currentUser()
            coEvery { orders.list("U-1", "pole_gps_update", page = 1) } returns
                PagedResultDto(1, 1, 2, listOf(workOrderItemDto("WO-NEW")))
            coEvery { orders.list("U-1", "pole_gps_update", page = 2) } throws java.io.IOException("offline")
            coEvery { gpsCache.all("U-1") } returns
                listOf(
                    LocalAssignedPoleGpsOrderEntity("U-1", "WO-1", "old", "C", "assigned", null, null),
                )
            val result =
                RealSurveyRepository(users, orders, mockk(relaxed = true), gpsCache, tokens)
                    .observeAssignedPoleGpsOrders().first()
            assertEquals(true, result.isStale)
            assertEquals(listOf("WO-1"), result.routes.map { it.workOrderId })
            coVerify(exactly = 0) { gpsCache.replaceAll(any(), any()) }
        }

    @Test
    fun `GPS missing identity never reads another account cache`() =
        runTest {
            val users = mockk<UserApi>()
            coEvery { tokens.cachedUserId() } returns null
            coEvery { users.me() } throws java.io.IOException("offline")
            val result =
                RealSurveyRepository(users, mockk(), mockk(), gpsCache, tokens)
                    .observeAssignedPoleGpsOrders().first()
            assertEquals(AssignedRoutesResult(emptyList(), true), result)
            coVerify(exactly = 0) { gpsCache.all(any()) }
        }

    @Test
    fun `GPS auth me offline uses only saved owner cache`() =
        runTest {
            val users = mockk<UserApi>()
            val orders = mockk<WorkOrdersApi>()
            val surveys = mockk<SurveyRouteCacheDao>(relaxed = true)
            coEvery { tokens.cachedUserId() } returns "A"
            coEvery { users.me() } throws java.io.IOException("offline")
            coEvery { gpsCache.all("A") } returns
                listOf(
                    LocalAssignedPoleGpsOrderEntity(
                        "A", "WO-1", "Khảo sát đường A", "COM-001", "assigned", null, "2026-10-01",
                    ),
                )
            val result =
                RealSurveyRepository(users, orders, surveys, gpsCache, tokens)
                    .observeAssignedPoleGpsOrders().first()
            assertEquals(AssignedRoutesResult(listOf(route()), true), result)
        }

    @Test
    fun `observeAssignedRoutes fetches and caches the routes on success`() =
        runTest {
            val userApi = mockk<UserApi>()
            val workOrdersApi = mockk<WorkOrdersApi>()
            val routeCacheDao = mockk<SurveyRouteCacheDao>(relaxed = true)
            coEvery { userApi.me() } returns currentUser()
            coEvery { workOrdersApi.list("U-1", "survey") } returns
                PagedResultDto(page = 1, pageSize = 200, total = 1, items = listOf(workOrderItemDto("WO-1")))
            val repository = RealSurveyRepository(userApi, workOrdersApi, routeCacheDao, gpsCache, tokens)

            val result = repository.observeAssignedRoutes().first()

            assertEquals(AssignedRoutesResult(listOf(route()), isStale = false), result)
            coVerify { routeCacheDao.replaceAll(listOf(routeEntity())) }
        }

    @Test
    fun `observeAssignedRoutes falls back to the cached routes marked stale when the fetch fails`() =
        runTest {
            val userApi = mockk<UserApi>()
            val workOrdersApi = mockk<WorkOrdersApi>()
            val routeCacheDao = mockk<SurveyRouteCacheDao>()
            coEvery { userApi.me() } throws java.io.IOException("network down")
            coEvery { routeCacheDao.allForOwner("U-1") } returns listOf(routeEntity())
            val repository = RealSurveyRepository(userApi, workOrdersApi, routeCacheDao, gpsCache, tokens)

            val result = repository.observeAssignedRoutes().first()

            assertEquals(AssignedRoutesResult(listOf(route()), isStale = true), result)
        }

    @Test
    fun `observeAssignedRoutes rethrows when the fetch fails and there is no cache`() =
        runTest {
            val userApi = mockk<UserApi>()
            val workOrdersApi = mockk<WorkOrdersApi>()
            val routeCacheDao = mockk<SurveyRouteCacheDao>()
            coEvery { userApi.me() } throws java.io.IOException("network down")
            coEvery { routeCacheDao.allForOwner("U-1") } returns emptyList()
            val repository = RealSurveyRepository(userApi, workOrdersApi, routeCacheDao, gpsCache, tokens)

            try {
                repository.observeAssignedRoutes().first()
                error("expected IOException")
            } catch (e: java.io.IOException) {
                assertEquals("network down", e.message)
            }
        }

    @Test
    fun `observeAssignedPoleGpsOrders fetches pole_gps_update orders assigned to me`() =
        runTest {
            val userApi = mockk<UserApi>()
            val workOrdersApi = mockk<WorkOrdersApi>()
            val routeCacheDao = mockk<SurveyRouteCacheDao>(relaxed = true)
            coEvery { userApi.me() } returns currentUser()
            coEvery { workOrdersApi.list("U-1", "pole_gps_update") } returns
                PagedResultDto(page = 1, pageSize = 200, total = 1, items = listOf(workOrderItemDto("WO-1")))
            val repository = RealSurveyRepository(userApi, workOrdersApi, routeCacheDao, gpsCache, tokens)

            val result = repository.observeAssignedPoleGpsOrders().first()

            assertEquals(AssignedRoutesResult(listOf(route()), false), result)
        }

    @Test
    fun `observeAssignedPoleGpsOrders swallows a failure as an empty list, not an error`() =
        runTest {
            val userApi = mockk<UserApi>()
            val workOrdersApi = mockk<WorkOrdersApi>()
            val routeCacheDao = mockk<SurveyRouteCacheDao>()
            coEvery { userApi.me() } throws java.io.IOException("network down")
            val repository = RealSurveyRepository(userApi, workOrdersApi, routeCacheDao, gpsCache, tokens)

            val result = repository.observeAssignedPoleGpsOrders().first()

            assertEquals(AssignedRoutesResult(emptyList(), true), result)
        }

    @Test
    fun `incomplete GPS pagination keeps the prior cache visible as stale`() =
        runTest {
            val users = mockk<UserApi>()
            val orders = mockk<WorkOrdersApi>()
            val surveys = mockk<SurveyRouteCacheDao>(relaxed = true)
            coEvery { users.me() } returns currentUser()
            coEvery { orders.list("U-1", "pole_gps_update", page = 1) } returns
                PagedResultDto(1, 1, 2, listOf(workOrderItemDto("WO-1")))
            coEvery { orders.list("U-1", "pole_gps_update", page = 2) } returns
                PagedResultDto(2, 1, 2, emptyList())
            coEvery { gpsCache.all("U-1") } returns
                listOf(
                    LocalAssignedPoleGpsOrderEntity(
                        "U-1", "WO-1", "Khảo sát đường A", "COM-001", "assigned", null, "2026-10-01",
                    ),
                )
            val result =
                RealSurveyRepository(users, orders, surveys, gpsCache, tokens)
                    .observeAssignedPoleGpsOrders().first()
            assertEquals(AssignedRoutesResult(listOf(route()), true), result)
            coVerify(exactly = 0) { gpsCache.replaceAll(any(), any()) }
        }
}
