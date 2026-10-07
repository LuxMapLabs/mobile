package com.luxmap.feature.workorder.data.sync

import com.luxmap.core.network.WorkOrdersApi
import com.luxmap.core.sync.SyncOpResult
import com.luxmap.feature.workorder.data.dao.WorkOrderCompletionDao
import com.luxmap.feature.workorder.data.dto.EvidenceItemDto
import com.luxmap.feature.workorder.data.entity.LocalWorkOrderEvidenceEntity
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
import java.io.File
import java.time.Instant

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

class UploadWorkOrderEvidenceSyncHandlerTest {
    @Test
    fun `a successful upload marks the local evidence row synced and returns Done`() =
        runTest {
            val tempFile = File.createTempFile("evidence", ".jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            val api = mockk<WorkOrdersApi>()
            val dao = mockk<WorkOrderCompletionDao>(relaxed = true)
            val evidence =
                LocalWorkOrderEvidenceEntity(
                    clientOpId = "OP-1",
                    workOrderId = "WO-1",
                    kind = "after",
                    filePath = tempFile.absolutePath,
                    capturedAt = Instant.parse("2026-10-06T10:00:00Z"),
                    lat = 10.97,
                    lng = 106.49,
                    uploadStatus = "pending",
                )
            coEvery { dao.evidenceByClientOpId("OP-1") } returns evidence
            coEvery { api.uploadEvidence(any(), any(), any(), any(), any(), any(), any()) } returns
                EvidenceItemDto("EV-1", "after", "2026-10-06T10:00:00Z", 10.97, 106.49, "t", "o")
            val handler = UploadWorkOrderEvidenceSyncHandler(api, dao)

            val result = handler.handle(Json.encodeToString(EvidenceSyncPayload("OP-1")))

            assertTrue(result is SyncOpResult.Done)
            coVerify { dao.updateEvidenceUploadStatus("OP-1", "synced") }
        }

    @Test
    fun `a 500 maps to RetryLater`() =
        runTest {
            val tempFile = File.createTempFile("evidence", ".jpg").apply { writeBytes(byteArrayOf(1)) }
            val api = mockk<WorkOrdersApi>()
            val dao = mockk<WorkOrderCompletionDao>(relaxed = true)
            val evidence =
                LocalWorkOrderEvidenceEntity(
                    clientOpId = "OP-1",
                    workOrderId = "WO-1",
                    kind = "after",
                    filePath = tempFile.absolutePath,
                    capturedAt = Instant.parse("2026-10-06T10:00:00Z"),
                    lat = 10.97,
                    lng = 106.49,
                    uploadStatus = "pending",
                )
            coEvery { dao.evidenceByClientOpId("OP-1") } returns evidence
            coEvery { api.uploadEvidence(any(), any(), any(), any(), any(), any(), any()) } throws httpException(500)
            val handler = UploadWorkOrderEvidenceSyncHandler(api, dao)

            val result = handler.handle(Json.encodeToString(EvidenceSyncPayload("OP-1")))

            assertTrue(result is SyncOpResult.RetryLater)
        }

    @Test
    fun `a 415 maps to Failed, not RetryLater`() =
        runTest {
            val tempFile = File.createTempFile("evidence", ".jpg").apply { writeBytes(byteArrayOf(1)) }
            val api = mockk<WorkOrdersApi>()
            val dao = mockk<WorkOrderCompletionDao>(relaxed = true)
            val evidence =
                LocalWorkOrderEvidenceEntity(
                    clientOpId = "OP-1",
                    workOrderId = "WO-1",
                    kind = "after",
                    filePath = tempFile.absolutePath,
                    capturedAt = Instant.parse("2026-10-06T10:00:00Z"),
                    lat = 10.97,
                    lng = 106.49,
                    uploadStatus = "pending",
                )
            coEvery { dao.evidenceByClientOpId("OP-1") } returns evidence
            coEvery { api.uploadEvidence(any(), any(), any(), any(), any(), any(), any()) } throws
                httpException(415, "UNSUPPORTED_IMAGE_FORMAT")
            val handler = UploadWorkOrderEvidenceSyncHandler(api, dao)

            val result = handler.handle(Json.encodeToString(EvidenceSyncPayload("OP-1")))

            assertTrue(result is SyncOpResult.Failed)
        }

    @Test
    fun `a 409 on evidence upload maps to Conflict, not RetryLater`() =
        runTest {
            val tempFile = File.createTempFile("evidence", ".jpg").apply { writeBytes(byteArrayOf(1)) }
            val api = mockk<WorkOrdersApi>()
            val dao = mockk<WorkOrderCompletionDao>(relaxed = true)
            val evidence =
                LocalWorkOrderEvidenceEntity(
                    clientOpId = "OP-1",
                    workOrderId = "WO-1",
                    kind = "after",
                    filePath = tempFile.absolutePath,
                    capturedAt = Instant.parse("2026-10-06T10:00:00Z"),
                    lat = 10.97,
                    lng = 106.49,
                    uploadStatus = "pending",
                )
            coEvery { dao.evidenceByClientOpId("OP-1") } returns evidence
            coEvery { api.uploadEvidence(any(), any(), any(), any(), any(), any(), any()) } throws
                httpException(409, "WORK_ORDER_NOT_IN_PROGRESS")
            val handler = UploadWorkOrderEvidenceSyncHandler(api, dao)

            val result = handler.handle(Json.encodeToString(EvidenceSyncPayload("OP-1")))

            assertTrue(result is SyncOpResult.Conflict)
        }
}
