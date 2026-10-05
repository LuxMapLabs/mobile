package com.luxmap.feature.workorder.data

import app.cash.turbine.test
import com.luxmap.core.network.WorkOrdersApi
import com.luxmap.feature.workorder.data.dto.WorkOrderDetailDto
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

private const val WORK_ORDER_ID = "WO-1"

private fun httpException(code: Int): HttpException =
    HttpException(Response.error<Any>(code, "".toResponseBody("application/json".toMediaType())))

private fun detailDto() =
    WorkOrderDetailDto(
        workOrderId = WORK_ORDER_ID,
        title = "Sửa đèn tuyến A",
        taskKind = "repair",
        woStatus = "assigned",
        dueDate = null,
        scheduledDate = null,
        note = null,
        allowedActions = listOf("start"),
        faults = emptyList(),
    )

class RealWorkOrderDetailRepositoryTest {
    @Test
    fun `emits the mapped detail when the backend returns 200`() =
        runTest {
            val workOrdersApi = mockk<WorkOrdersApi>()
            coEvery { workOrdersApi.detail(WORK_ORDER_ID) } returns detailDto()
            val repository = RealWorkOrderDetailRepository(workOrdersApi)

            repository.observeWorkOrderDetail(WORK_ORDER_ID).test {
                val detail = awaitItem()
                assertEquals(WORK_ORDER_ID, detail?.workOrderId)
                awaitComplete()
            }
        }

    @Test
    fun `emits null on a 404, not an Error`() =
        runTest {
            val workOrdersApi = mockk<WorkOrdersApi>()
            coEvery { workOrdersApi.detail(WORK_ORDER_ID) } throws httpException(404)
            val repository = RealWorkOrderDetailRepository(workOrdersApi)

            repository.observeWorkOrderDetail(WORK_ORDER_ID).test {
                assertNull(awaitItem())
                awaitComplete()
            }
        }

    @Test
    fun `a non-404 HTTP error propagates instead of being treated as not-found`() =
        runTest {
            val workOrdersApi = mockk<WorkOrdersApi>()
            coEvery { workOrdersApi.detail(WORK_ORDER_ID) } throws httpException(500)
            val repository = RealWorkOrderDetailRepository(workOrdersApi)

            repository.observeWorkOrderDetail(WORK_ORDER_ID).test {
                val error = awaitError()
                assertTrue(error is HttpException)
            }
        }

    @Test
    fun `start returns success when the backend accepts it`() =
        runTest {
            val workOrdersApi = mockk<WorkOrdersApi>()
            coEvery { workOrdersApi.start(WORK_ORDER_ID) } returns Unit
            val repository = RealWorkOrderDetailRepository(workOrdersApi)

            assertTrue(repository.start(WORK_ORDER_ID).isSuccess)
        }

    @Test
    fun `start returns failure when the backend rejects it`() =
        runTest {
            val workOrdersApi = mockk<WorkOrdersApi>()
            coEvery { workOrdersApi.start(WORK_ORDER_ID) } throws httpException(409)
            val repository = RealWorkOrderDetailRepository(workOrdersApi)

            assertTrue(repository.start(WORK_ORDER_ID).isFailure)
        }
}
