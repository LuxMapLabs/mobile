package com.luxmap.feature.workorder.data

import app.cash.turbine.test
import com.luxmap.core.network.WorkOrdersApi
import com.luxmap.core.offline.LocalOfflineSnapshotEntity
import com.luxmap.core.offline.OfflineSnapshotDao
import com.luxmap.core.sync.SyncQueueManager
import com.luxmap.feature.workorder.data.dto.WorkOrderDetailDto
import com.luxmap.feature.workorder.polegps.data.PoleGpsDetailSync
import io.mockk.coEvery
import io.mockk.coVerify
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
import java.io.IOException

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
    private val snapshots =
        object : OfflineSnapshotDao {
            val saved = mutableMapOf<String, LocalOfflineSnapshotEntity>()

            override suspend fun get(key: String) = saved[key]

            override suspend fun put(snapshot: LocalOfflineSnapshotEntity) {
                saved[snapshot.key] = snapshot
            }

            override suspend fun clear() = saved.clear()
        }
    private var startQueued = false
    private val queue =
        mockk<SyncQueueManager> {
            coEvery { pendingStartOf(WORK_ORDER_ID) } answers {
                SyncQueueManager.startOpId(WORK_ORDER_ID).takeIf { startQueued }
            }
            coEvery { enqueue(any(), any(), any(), any()) } answers { startQueued = true }
        }

    @Test
    fun `emits the mapped detail when the backend returns 200`() =
        runTest {
            val workOrdersApi = mockk<WorkOrdersApi>()
            val sync = mockk<PoleGpsDetailSync>(relaxed = true)
            coEvery { workOrdersApi.detail(WORK_ORDER_ID) } returns detailDto()
            val repository = RealWorkOrderDetailRepository(workOrdersApi, sync, snapshots, queue)

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
            val sync = mockk<PoleGpsDetailSync>(relaxed = true)
            coEvery { workOrdersApi.detail(WORK_ORDER_ID) } throws httpException(404)
            val repository = RealWorkOrderDetailRepository(workOrdersApi, sync, snapshots, queue)

            repository.observeWorkOrderDetail(WORK_ORDER_ID).test {
                assertNull(awaitItem())
                awaitComplete()
            }
        }

    @Test
    fun `a non-404 HTTP error propagates instead of being treated as not-found`() =
        runTest {
            val workOrdersApi = mockk<WorkOrdersApi>()
            val sync = mockk<PoleGpsDetailSync>(relaxed = true)
            coEvery { workOrdersApi.detail(WORK_ORDER_ID) } throws httpException(500)
            val repository = RealWorkOrderDetailRepository(workOrdersApi, sync, snapshots, queue)

            repository.observeWorkOrderDetail(WORK_ORDER_ID).test {
                val error = awaitError()
                assertTrue(error is HttpException)
            }
        }

    @Test
    fun `start returns the backend's updated detail when it accepts the request`() =
        runTest {
            val workOrdersApi = mockk<WorkOrdersApi>()
            val sync = mockk<PoleGpsDetailSync>(relaxed = true)
            coEvery { workOrdersApi.start(WORK_ORDER_ID) } returns detailDto().copy(woStatus = "in_progress")
            val repository = RealWorkOrderDetailRepository(workOrdersApi, sync, snapshots, queue)

            val result = repository.start(WORK_ORDER_ID)

            assertTrue(result.isSuccess)
            assertEquals("in_progress", result.getOrNull()?.woStatus)
        }

    @Test
    fun `start returns failure when the backend rejects it`() =
        runTest {
            val workOrdersApi = mockk<WorkOrdersApi>()
            val sync = mockk<PoleGpsDetailSync>(relaxed = true)
            coEvery { workOrdersApi.start(WORK_ORDER_ID) } throws httpException(409)
            val repository = RealWorkOrderDetailRepository(workOrdersApi, sync, snapshots, queue)

            assertTrue(repository.start(WORK_ORDER_ID).isFailure)
        }

    @Test
    fun `network failure falls back to the cached pole GPS order`() =
        runTest {
            val workOrdersApi = mockk<WorkOrdersApi>()
            val sync = mockk<PoleGpsDetailSync>(relaxed = true)
            val cached =
                WorkOrderDetailDto(
                    workOrderId = WORK_ORDER_ID,
                    title = "t",
                    taskKind = "pole_gps_update",
                    woStatus = "assigned",
                )
            coEvery { workOrdersApi.detail(WORK_ORDER_ID) } throws IOException("offline")
            coEvery { sync.cached(WORK_ORDER_ID) } returns cached
            val repository = RealWorkOrderDetailRepository(workOrdersApi, sync, snapshots, queue)

            repository.observeWorkOrderDetail(WORK_ORDER_ID).test {
                assertEquals(WORK_ORDER_ID, awaitItem()?.workOrderId)
                awaitComplete()
            }
        }

    @Test
    fun `network failure without a cache still surfaces the error`() =
        runTest {
            val workOrdersApi = mockk<WorkOrdersApi>()
            val sync = mockk<PoleGpsDetailSync>(relaxed = true)
            coEvery { workOrdersApi.detail(WORK_ORDER_ID) } throws IOException("offline")
            coEvery { sync.cached(WORK_ORDER_ID) } returns null
            val repository = RealWorkOrderDetailRepository(workOrdersApi, sync, snapshots, queue)

            repository.observeWorkOrderDetail(WORK_ORDER_ID).test {
                val error = awaitError()
                assertTrue(error is IOException)
            }
        }

    @Test
    fun `without signal a survey opened before is started on the device and its start is queued`() =
        runTest {
            val workOrdersApi = mockk<WorkOrdersApi>()
            val sync = mockk<PoleGpsDetailSync>(relaxed = true)
            coEvery { sync.cached(WORK_ORDER_ID) } returns null
            coEvery { workOrdersApi.detail(WORK_ORDER_ID) } returns detailDto()
            val repository = RealWorkOrderDetailRepository(workOrdersApi, sync, snapshots, queue)
            repository.observeWorkOrderDetail(WORK_ORDER_ID).test {
                awaitItem()
                awaitComplete()
            }
            coEvery { workOrdersApi.start(WORK_ORDER_ID) } throws IOException("offline")
            coEvery { workOrdersApi.detail(WORK_ORDER_ID) } throws IOException("offline")

            val started = repository.start(WORK_ORDER_ID).getOrThrow()

            assertEquals("in_progress", started.woStatus)
            assertTrue(started.startPendingSync)
            assertTrue("complete" in started.allowedActions && "start" !in started.allowedActions)
            coVerify {
                queue.enqueue(
                    opType = "start_work_order",
                    payloadJson = any(),
                    clientOpId = "start:$WORK_ORDER_ID",
                    dependsOnClientOpId = null,
                )
            }
            // Reopened offline later, the order still reads as started, from the saved copy.
            repository.observeWorkOrderDetail(WORK_ORDER_ID).test {
                assertEquals("in_progress", awaitItem()?.woStatus)
                awaitComplete()
            }
        }

    @Test
    fun `without signal an order never opened on this device still cannot be started`() =
        runTest {
            val workOrdersApi = mockk<WorkOrdersApi>()
            val sync = mockk<PoleGpsDetailSync>(relaxed = true)
            coEvery { sync.cached(WORK_ORDER_ID) } returns null
            coEvery { workOrdersApi.start(WORK_ORDER_ID) } throws IOException("offline")

            val result = RealWorkOrderDetailRepository(workOrdersApi, sync, snapshots, queue).start(WORK_ORDER_ID)

            assertTrue(result.exceptionOrNull() is IOException)
            coVerify(exactly = 0) { queue.enqueue(any(), any(), any(), any()) }
        }
}
