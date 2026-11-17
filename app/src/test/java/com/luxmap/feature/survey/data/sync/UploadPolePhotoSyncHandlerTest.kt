package com.luxmap.feature.survey.data.sync

import com.luxmap.core.network.WorkOrdersApi
import com.luxmap.core.sync.SyncOpResult
import com.luxmap.feature.survey.data.dao.PolePhotoDao
import com.luxmap.feature.survey.data.dto.PolePhotoUploadResponseDto
import com.luxmap.feature.survey.data.entity.LocalPolePhotoEntity
import io.mockk.coEvery
import io.mockk.coJustRun
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

private fun photoEntity(filePath: String) =
    LocalPolePhotoEntity(
        photoId = "PHOTO-1",
        workOrderId = "WO-1",
        poleId = "POLE-1",
        filePath = filePath,
        capturedAt = Instant.parse("2026-10-16T10:00:00Z"),
        lat = 10.97,
        lng = 106.49,
    )

class UploadPolePhotoSyncHandlerTest {
    @Test
    fun `a successful upload returns Done`() =
        runTest {
            val tempFile = File.createTempFile("pole-photo", ".jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            val api = mockk<WorkOrdersApi>()
            val dao = mockk<PolePhotoDao>()
            coEvery { dao.byId("PHOTO-1") } returns photoEntity(tempFile.absolutePath)
            coEvery { api.uploadPolePhoto(any(), any(), any(), any(), any(), any(), any()) } returns
                PolePhotoUploadResponseDto(photoId = "PPH-000001")
            coJustRun { dao.updateServerPhotoId("PHOTO-1", "PPH-000001") }
            val handler = UploadPolePhotoSyncHandler(api, dao)

            val result = handler.handle(Json.encodeToString(PolePhotoSyncPayload("PHOTO-1")))

            assertTrue(result is SyncOpResult.Done)
            coVerify { dao.updateServerPhotoId("PHOTO-1", "PPH-000001") }
        }

    @Test
    fun `a missing local row returns Failed`() =
        runTest {
            val api = mockk<WorkOrdersApi>()
            val dao = mockk<PolePhotoDao>()
            coEvery { dao.byId("PHOTO-1") } returns null
            val handler = UploadPolePhotoSyncHandler(api, dao)

            val result = handler.handle(Json.encodeToString(PolePhotoSyncPayload("PHOTO-1")))

            assertTrue(result is SyncOpResult.Failed)
        }

    @Test
    fun `a 500 maps to RetryLater`() =
        runTest {
            val tempFile = File.createTempFile("pole-photo", ".jpg").apply { writeBytes(byteArrayOf(1)) }
            val api = mockk<WorkOrdersApi>()
            val dao = mockk<PolePhotoDao>()
            coEvery { dao.byId("PHOTO-1") } returns photoEntity(tempFile.absolutePath)
            coEvery { api.uploadPolePhoto(any(), any(), any(), any(), any(), any(), any()) } throws httpException(500)
            val handler = UploadPolePhotoSyncHandler(api, dao)

            val result = handler.handle(Json.encodeToString(PolePhotoSyncPayload("PHOTO-1")))

            assertTrue(result is SyncOpResult.RetryLater)
        }

    @Test
    fun `a 409 idempotency conflict maps to Conflict, not RetryLater`() =
        runTest {
            val tempFile = File.createTempFile("pole-photo", ".jpg").apply { writeBytes(byteArrayOf(1)) }
            val api = mockk<WorkOrdersApi>()
            val dao = mockk<PolePhotoDao>()
            coEvery { dao.byId("PHOTO-1") } returns photoEntity(tempFile.absolutePath)
            coEvery { api.uploadPolePhoto(any(), any(), any(), any(), any(), any(), any()) } throws
                httpException(409, "IDEMPOTENCY_CONFLICT")
            val handler = UploadPolePhotoSyncHandler(api, dao)

            val result = handler.handle(Json.encodeToString(PolePhotoSyncPayload("PHOTO-1")))

            assertTrue(result is SyncOpResult.Conflict)
        }

    @Test
    fun `a 404 maps to Failed, not RetryLater`() =
        runTest {
            val tempFile = File.createTempFile("pole-photo", ".jpg").apply { writeBytes(byteArrayOf(1)) }
            val api = mockk<WorkOrdersApi>()
            val dao = mockk<PolePhotoDao>()
            coEvery { dao.byId("PHOTO-1") } returns photoEntity(tempFile.absolutePath)
            coEvery { api.uploadPolePhoto(any(), any(), any(), any(), any(), any(), any()) } throws
                httpException(404, "POLE_NOT_FOUND")
            val handler = UploadPolePhotoSyncHandler(api, dao)

            val result = handler.handle(Json.encodeToString(PolePhotoSyncPayload("PHOTO-1")))

            assertTrue(result is SyncOpResult.Failed)
        }

    @Test
    fun `an upload response with an ai result is written to room right away`() =
        runTest {
            val tempFile = File.createTempFile("pole-photo", ".tmp").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            val api = mockk<WorkOrdersApi>()
            val dao = mockk<PolePhotoDao>()
            coEvery { dao.byId("PHOTO-1") } returns photoEntity(tempFile.absolutePath)
            coEvery { api.uploadPolePhoto(any(), any(), any(), any(), any(), any(), any()) } returns
                PolePhotoUploadResponseDto(
                    photoId = "PPH-000001",
                    aiStatus = "available",
                    cvState = "off",
                    confidence = 0.91,
                    modelVersion = "cv-1",
                    analyzedAt = "2026-11-17T03:00:00Z",
                )
            coJustRun { dao.updateServerPhotoId("PHOTO-1", "PPH-000001") }
            coJustRun { dao.updateUploadAiResult(any(), any(), any(), any(), any(), any()) }
            val handler = UploadPolePhotoSyncHandler(api, dao)

            val result = handler.handle(Json.encodeToString(PolePhotoSyncPayload("PHOTO-1")))

            assertTrue(result is SyncOpResult.Done)
            coVerify {
                dao.updateUploadAiResult(
                    "PHOTO-1",
                    "available",
                    "off",
                    0.91,
                    "cv-1",
                    Instant.parse("2026-11-17T03:00:00Z"),
                )
            }
        }

    @Test
    fun `a malformed analyzed_at still finishes the upload`() =
        runTest {
            val tempFile = File.createTempFile("pole", ".tmp").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            val api = mockk<WorkOrdersApi>()
            val dao = mockk<PolePhotoDao>()
            coEvery { dao.byId("PHOTO-1") } returns photoEntity(tempFile.absolutePath)
            coEvery { api.uploadPolePhoto(any(), any(), any(), any(), any(), any(), any()) } returns
                PolePhotoUploadResponseDto(
                    photoId = "PPH-000001",
                    aiStatus = "available",
                    cvState = "on",
                    analyzedAt = "yesterday",
                )
            coJustRun { dao.updateServerPhotoId("PHOTO-1", "PPH-000001") }
            coJustRun { dao.updateUploadAiResult(any(), any(), any(), any(), any(), any()) }
            val handler = UploadPolePhotoSyncHandler(api, dao)

            val result = handler.handle(Json.encodeToString(PolePhotoSyncPayload("PHOTO-1")))

            assertTrue(result is SyncOpResult.Done)
            coVerify { dao.updateUploadAiResult("PHOTO-1", "available", "on", null, null, null) }
        }
}
