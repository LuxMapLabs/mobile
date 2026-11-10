package com.luxmap.feature.survey.data.sync

import com.luxmap.core.network.WorkOrdersApi
import com.luxmap.core.sync.SyncOpResult
import com.luxmap.feature.survey.data.dao.PolePhotoDao
import com.luxmap.feature.survey.data.dto.PolePhotoItemDto
import com.luxmap.feature.survey.data.dto.PolePhotoReviewRequestDto
import com.luxmap.feature.survey.data.entity.LocalPolePhotoEntity
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

private fun draftPhoto(
    serverPhotoId: String? = "PPH-000001",
    draftFinalCvState: String? = "off",
    draftReviewNote: String? = "Thấy đèn tắt khi kiểm tra",
) = LocalPolePhotoEntity(
    photoId = "PHOTO-1",
    workOrderId = "WO-1",
    poleId = "POLE-1",
    filePath = "/data/photo.jpg",
    capturedAt = Instant.parse("2026-10-16T10:00:00Z"),
    lat = 10.97,
    lng = 106.49,
    serverPhotoId = serverPhotoId,
    cvState = "on",
    draftFinalCvState = draftFinalCvState,
    draftReviewNote = draftReviewNote,
)

private fun reviewedItemDto() =
    PolePhotoItemDto(
        photoId = "PPH-000001",
        workOrderId = "WO-1",
        poleId = "POLE-1",
        dataSource = "pole_photo",
        capturedAt = "2026-10-16T10:00:00Z",
        lat = 10.97,
        lng = 106.49,
        uploadedBy = "U-1",
        uploadedAt = "2026-10-16T10:00:05Z",
        aiStatus = "available",
        cvState = "on",
        confidence = 0.92,
        modelVersion = "cv-v1",
        artifactVersion = "art-v1",
        analyzedAt = "2026-10-16T10:00:10Z",
        finalCvState = "off",
        reviewNote = "Thấy đèn tắt khi kiểm tra",
        reviewedBy = "U-2",
        reviewedAt = "2026-11-10T08:00:00Z",
    )

class SubmitPolePhotoReviewSyncHandlerTest {
    @Test
    fun `a successful send writes the confirmed fields and clears the draft`() =
        runTest {
            val api = mockk<WorkOrdersApi>()
            val dao = mockk<PolePhotoDao>(relaxed = true)
            coEvery { dao.byId("PHOTO-1") } returns draftPhoto()
            coEvery {
                api.reviewPolePhoto(
                    "WO-1",
                    "PPH-000001",
                    PolePhotoReviewRequestDto(finalCvState = "off", reviewNote = "Thấy đèn tắt khi kiểm tra"),
                )
            } returns reviewedItemDto()
            val handler = SubmitPolePhotoReviewSyncHandler(api, dao)

            val result = handler.handle(Json.encodeToString(PolePhotoSyncPayload("PHOTO-1")))

            assertTrue(result is SyncOpResult.Done)
            coVerify {
                dao.updateAiResult(
                    photoId = "PHOTO-1",
                    aiStatus = "available",
                    cvState = "on",
                    confidence = 0.92,
                    modelVersion = "cv-v1",
                    analyzedAt = Instant.parse("2026-10-16T10:00:10Z"),
                    finalCvState = "off",
                    reviewNote = "Thấy đèn tắt khi kiểm tra",
                    reviewedBy = "U-2",
                    reviewedAt = Instant.parse("2026-11-10T08:00:00Z"),
                    decision = null,
                    decisionNote = null,
                    decidedBy = null,
                    decidedAt = null,
                )
            }
            coVerify { dao.updateDraftReview("PHOTO-1", null, null) }
        }

    @Test
    fun `a missing local row returns Failed`() =
        runTest {
            val api = mockk<WorkOrdersApi>()
            val dao = mockk<PolePhotoDao>()
            coEvery { dao.byId("PHOTO-1") } returns null
            val handler = SubmitPolePhotoReviewSyncHandler(api, dao)

            val result = handler.handle(Json.encodeToString(PolePhotoSyncPayload("PHOTO-1")))

            assertTrue(result is SyncOpResult.Failed)
        }

    @Test
    fun `a row with no serverPhotoId returns Failed`() =
        runTest {
            val api = mockk<WorkOrdersApi>()
            val dao = mockk<PolePhotoDao>()
            coEvery { dao.byId("PHOTO-1") } returns draftPhoto(serverPhotoId = null)
            val handler = SubmitPolePhotoReviewSyncHandler(api, dao)

            val result = handler.handle(Json.encodeToString(PolePhotoSyncPayload("PHOTO-1")))

            assertTrue(result is SyncOpResult.Failed)
        }

    @Test
    fun `a row with no draft returns Done, not Failed`() =
        runTest {
            val api = mockk<WorkOrdersApi>()
            val dao = mockk<PolePhotoDao>()
            coEvery { dao.byId("PHOTO-1") } returns draftPhoto(draftFinalCvState = null)
            val handler = SubmitPolePhotoReviewSyncHandler(api, dao)

            val result = handler.handle(Json.encodeToString(PolePhotoSyncPayload("PHOTO-1")))

            assertTrue(result is SyncOpResult.Done)
        }

    @Test
    fun `a 409 already-decided error maps to Failed, not RetryLater`() =
        runTest {
            val api = mockk<WorkOrdersApi>()
            val dao = mockk<PolePhotoDao>()
            coEvery { dao.byId("PHOTO-1") } returns draftPhoto()
            coEvery { api.reviewPolePhoto(any(), any(), any()) } throws
                httpException(409, "POLE_PHOTO_ALREADY_DECIDED")
            val handler = SubmitPolePhotoReviewSyncHandler(api, dao)

            val result = handler.handle(Json.encodeToString(PolePhotoSyncPayload("PHOTO-1")))

            assertTrue(result is SyncOpResult.Failed)
        }

    @Test
    fun `a 500 maps to RetryLater`() =
        runTest {
            val api = mockk<WorkOrdersApi>()
            val dao = mockk<PolePhotoDao>()
            coEvery { dao.byId("PHOTO-1") } returns draftPhoto()
            coEvery { api.reviewPolePhoto(any(), any(), any()) } throws httpException(500)
            val handler = SubmitPolePhotoReviewSyncHandler(api, dao)

            val result = handler.handle(Json.encodeToString(PolePhotoSyncPayload("PHOTO-1")))

            assertTrue(result is SyncOpResult.RetryLater)
        }
}
