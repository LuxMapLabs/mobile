package com.luxmap.feature.survey.data.sync

import com.luxmap.core.network.WorkOrdersApi
import com.luxmap.core.sync.SyncOpResult
import com.luxmap.feature.survey.data.dao.PolePhotoDao
import com.luxmap.feature.survey.data.dto.PolePhotoUploadResponseDto
import com.luxmap.feature.survey.data.entity.LocalPolePhotoEntity
import io.mockk.coEvery
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
                PolePhotoUploadResponseDto(photoId = "PHOTO-1")
            val handler = UploadPolePhotoSyncHandler(api, dao)

            val result = handler.handle(Json.encodeToString(PolePhotoSyncPayload("PHOTO-1")))

            assertTrue(result is SyncOpResult.Done)
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
}
