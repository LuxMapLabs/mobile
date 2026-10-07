package com.luxmap.feature.workorder.data.sync

import com.luxmap.core.network.WorkOrdersApi
import com.luxmap.core.sync.SyncOpResult
import com.luxmap.feature.workorder.data.dao.WorkOrderCompletionDao
import com.luxmap.feature.workorder.data.dto.WorkOrderDetailDto
import com.luxmap.feature.workorder.data.entity.LocalWorkOrderCompletionEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

private fun httpException(
    code: Int,
    errorCode: String? = null,
) = HttpException(
    Response.error<Any>(
        code,
        (if (errorCode == null) "" else """{"error":{"code":"$errorCode","message":"x"}}""")
            .toResponseBody("application/json".toMediaType()),
    ),
)

private fun completion(workOrderId: String = "WO-1") =
    LocalWorkOrderCompletionEntity(
        workOrderId = workOrderId,
        reportNote = "Đã thay bóng đèn",
        materialsUsed = null,
        faultOutcomesJson = null,
        clientOpId = "COMP-1",
        submitStatus = "pending",
    )

private fun detailDto(workOrderId: String = "WO-1") =
    WorkOrderDetailDto(
        workOrderId = workOrderId,
        title = "t",
        taskKind = "repair",
        woStatus = "done",
        dueDate = null,
        scheduledDate = null,
        note = null,
        allowedActions = emptyList(),
        faults = emptyList(),
    )

class CompleteWorkOrderSyncHandlerTest {
    @Test
    fun `a successful complete marks the local completion row synced and returns Done`() =
        runTest {
            val api = mockk<WorkOrdersApi>()
            val dao = mockk<WorkOrderCompletionDao>(relaxed = true)
            coEvery { dao.completionByWorkOrderId("WO-1") } returns completion()
            coEvery { api.complete("WO-1", any()) } returns detailDto()
            val handler = CompleteWorkOrderSyncHandler(api, dao)

            val result = handler.handle(Json.encodeToString(CompletionSyncPayload("WO-1")))

            assertTrue(result is SyncOpResult.Done)
            coVerify { dao.updateCompletionSubmitStatus("WO-1", "synced") }
        }

    @Test
    fun `409 AFTER_EVIDENCE_REQUIRED maps to RetryLater, not Conflict`() =
        runTest {
            val api = mockk<WorkOrdersApi>()
            val dao = mockk<WorkOrderCompletionDao>(relaxed = true)
            coEvery { dao.completionByWorkOrderId("WO-1") } returns completion()
            coEvery { api.complete("WO-1", any()) } throws httpException(409, "AFTER_EVIDENCE_REQUIRED")
            val handler = CompleteWorkOrderSyncHandler(api, dao)

            val result = handler.handle(Json.encodeToString(CompletionSyncPayload("WO-1")))

            assertTrue(result is SyncOpResult.RetryLater)
        }

    @Test
    fun `a 409 with a different code is a real state Conflict, not RetryLater`() =
        runTest {
            val api = mockk<WorkOrdersApi>()
            val dao = mockk<WorkOrderCompletionDao>(relaxed = true)
            coEvery { dao.completionByWorkOrderId("WO-1") } returns completion()
            coEvery { api.complete("WO-1", any()) } throws httpException(409, "ALREADY_COMPLETED")
            val handler = CompleteWorkOrderSyncHandler(api, dao)

            val result = handler.handle(Json.encodeToString(CompletionSyncPayload("WO-1")))

            assertTrue(result is SyncOpResult.Conflict)
        }

    @Test
    fun `a 400 maps to Failed`() =
        runTest {
            val api = mockk<WorkOrdersApi>()
            val dao = mockk<WorkOrderCompletionDao>(relaxed = true)
            coEvery { dao.completionByWorkOrderId("WO-1") } returns completion()
            coEvery { api.complete("WO-1", any()) } throws httpException(400, "VALIDATION_FAILED")
            val handler = CompleteWorkOrderSyncHandler(api, dao)

            val result = handler.handle(Json.encodeToString(CompletionSyncPayload("WO-1")))

            assertTrue(result is SyncOpResult.Failed)
        }
}
