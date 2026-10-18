package com.luxmap.feature.survey.data

import android.content.Context
import com.luxmap.core.network.UserApi
import com.luxmap.core.network.WorkOrdersApi
import com.luxmap.core.network.dto.PagedResultDto
import com.luxmap.feature.auth.data.CurrentUserResponseDto
import com.luxmap.feature.survey.data.dao.SurveyRouteCacheDao
import com.luxmap.feature.survey.data.dao.SurveySessionDao
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
    )

class RealSurveyRepositoryTest {
    @Test
    fun `observeAssignedRoutes fetches and caches the routes on success`() =
        runTest {
            val userApi = mockk<UserApi>()
            val workOrdersApi = mockk<WorkOrdersApi>()
            val sessionDao = mockk<SurveySessionDao>()
            val routeCacheDao = mockk<SurveyRouteCacheDao>(relaxed = true)
            val context = mockk<Context>()
            coEvery { userApi.me() } returns currentUser()
            coEvery { workOrdersApi.list("U-1", "survey") } returns
                PagedResultDto(page = 1, pageSize = 200, total = 1, items = listOf(workOrderItemDto("WO-1")))
            val repository = RealSurveyRepository(userApi, workOrdersApi, sessionDao, routeCacheDao, context)

            val result = repository.observeAssignedRoutes().first()

            assertEquals(AssignedRoutesResult(listOf(route()), isStale = false), result)
            coVerify { routeCacheDao.replaceAll(listOf(routeEntity())) }
        }

    @Test
    fun `observeAssignedRoutes falls back to the cached routes marked stale when the fetch fails`() =
        runTest {
            val userApi = mockk<UserApi>()
            val workOrdersApi = mockk<WorkOrdersApi>()
            val sessionDao = mockk<SurveySessionDao>()
            val routeCacheDao = mockk<SurveyRouteCacheDao>()
            val context = mockk<Context>()
            coEvery { userApi.me() } throws IllegalStateException("network down")
            coEvery { routeCacheDao.all() } returns listOf(routeEntity())
            val repository = RealSurveyRepository(userApi, workOrdersApi, sessionDao, routeCacheDao, context)

            val result = repository.observeAssignedRoutes().first()

            assertEquals(AssignedRoutesResult(listOf(route()), isStale = true), result)
        }

    @Test
    fun `observeAssignedRoutes rethrows when the fetch fails and there is no cache`() =
        runTest {
            val userApi = mockk<UserApi>()
            val workOrdersApi = mockk<WorkOrdersApi>()
            val sessionDao = mockk<SurveySessionDao>()
            val routeCacheDao = mockk<SurveyRouteCacheDao>()
            val context = mockk<Context>()
            coEvery { userApi.me() } throws IllegalStateException("network down")
            coEvery { routeCacheDao.all() } returns emptyList()
            val repository = RealSurveyRepository(userApi, workOrdersApi, sessionDao, routeCacheDao, context)

            try {
                repository.observeAssignedRoutes().first()
                error("expected IllegalStateException")
            } catch (e: IllegalStateException) {
                assertEquals("network down", e.message)
            }
        }
}
