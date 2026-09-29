package com.luxmap.feature.home.data

import app.cash.turbine.test
import com.luxmap.core.network.UserApi
import com.luxmap.core.network.WorkOrdersApi
import com.luxmap.core.network.dto.PagedResultDto
import com.luxmap.feature.auth.data.CurrentUserResponseDto
import com.luxmap.feature.workorder.data.dto.WorkOrderItemDto
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

private const val USER_ID = "USER-1"

class RealHomeRepositoryTest {
    private fun repository(items: List<WorkOrderItemDto>): RealHomeRepository {
        val userApi = mockk<UserApi>()
        coEvery { userApi.me() } returns
            CurrentUserResponseDto(
                userId = USER_ID,
                username = "fe1",
                email = "fe1@luxmap.vn",
                fullName = "Field Engineer 1",
                role = "field_engineer",
                communeIds = listOf("COM-001"),
            )
        val workOrdersApi = mockk<WorkOrdersApi>()
        coEvery { workOrdersApi.list(assignedTo = USER_ID, page = 1, pageSize = 200) } returns
            PagedResultDto(page = 1, pageSize = 200, total = items.size, items = items)
        return RealHomeRepository(userApi, workOrdersApi)
    }

    private fun workOrder(
        id: String,
        communeId: String = "COM-001",
        woStatus: String = "assigned",
        dueDate: String? = null,
        priorityScore: Double? = null,
    ) = WorkOrderItemDto(
        workOrderId = id,
        title = "Fix lamp",
        communeId = communeId,
        taskKind = "repair",
        caseId = id,
        faultIds = listOf("FAULT-1"),
        woStatus = woStatus,
        priorityScore = priorityScore,
        createdAt = "2026-09-01T00:00:00Z",
        updatedAt = "2026-09-01T00:00:00Z",
        dueDate = dueDate,
    )

    @Test
    fun `counts assigned and in-progress by wo_status`() =
        runTest {
            val items =
                listOf(
                    workOrder("WO-1", woStatus = "assigned"),
                    workOrder("WO-2", woStatus = "assigned"),
                    workOrder("WO-3", woStatus = "in_progress"),
                    workOrder("WO-4", woStatus = "done"),
                )
            repository(items).observeHomeData().test {
                val data = awaitItem()
                assertEquals(2, data.metrics.assignedCount)
                assertEquals(1, data.metrics.inProgressCount)
                awaitComplete()
            }
        }

    @Test
    fun `a work order past its due date is overdue, one that is not is not`() =
        runTest {
            val today = LocalDate.now()
            val items =
                listOf(
                    workOrder("WO-PAST", woStatus = "assigned", dueDate = today.minusDays(1).toString()),
                    workOrder("WO-FUTURE", woStatus = "assigned", dueDate = today.plusDays(5).toString()),
                    workOrder("WO-NO-DUE", woStatus = "assigned", dueDate = null),
                )
            repository(items).observeHomeData().test {
                assertEquals(1, awaitItem().metrics.overdueCount)
                awaitComplete()
            }
        }

    @Test
    fun `a finished work order is never overdue even with a past due date`() =
        runTest {
            val pastDue = LocalDate.now().minusDays(30).toString()
            val items =
                listOf(
                    workOrder("WO-DONE", woStatus = "done", dueDate = pastDue),
                    workOrder("WO-VERIFIED", woStatus = "verified", dueDate = pastDue),
                    workOrder("WO-CANCELLED", woStatus = "cancelled", dueDate = pastDue),
                )
            repository(items).observeHomeData().test {
                assertEquals(0, awaitItem().metrics.overdueCount)
                awaitComplete()
            }
        }

    @Test
    fun `planned sweep count is always null - no backend source yet`() =
        runTest {
            repository(listOf(workOrder("WO-1"))).observeHomeData().test {
                assertNull(awaitItem().metrics.plannedSweepCount)
                awaitComplete()
            }
        }

    @Test
    fun `groups items into clusters by raw commune_id`() =
        runTest {
            val items =
                listOf(
                    workOrder("WO-1", communeId = "COM-001"),
                    workOrder("WO-2", communeId = "COM-002"),
                    workOrder("WO-3", communeId = "COM-001"),
                )
            repository(items).observeHomeData().test {
                val clusters = awaitItem().clusters
                assertEquals(2, clusters.size)
                val com001 = clusters.first { it.clusterLabel == "COM-001" }
                assertEquals(listOf("WO-1", "WO-3"), com001.items.map { it.workOrderId })
                awaitComplete()
            }
        }

    @Test
    fun `maps wo_status, due_date and priority_score into the summary item`() =
        runTest {
            val items =
                listOf(workOrder("WO-1", woStatus = "in_progress", dueDate = "2026-10-01", priorityScore = 87.5))
            repository(items).observeHomeData().test {
                val item = awaitItem().clusters.single().items.single()
                assertEquals("WO-1", item.workOrderId)
                assertEquals("in_progress", item.woStatus)
                assertEquals("2026-10-01", item.dueDate)
                assertTrue(item.priorityScore == 87.5)
                awaitComplete()
            }
        }
}
