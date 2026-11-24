package com.luxmap.feature.survey.data.sync

import com.luxmap.core.network.LuxReadingsApi
import com.luxmap.core.sync.SyncOpResult
import com.luxmap.core.sync.SyncQueueDao
import com.luxmap.feature.survey.data.dao.LuxReadingDao
import com.luxmap.feature.survey.data.dao.PolePhotoDao
import com.luxmap.feature.survey.data.dto.CreateLuxReadingRequestDto
import com.luxmap.feature.survey.data.dto.LuxReadingDto
import com.luxmap.feature.survey.data.entity.LocalLuxReadingEntity
import com.luxmap.feature.survey.data.entity.LocalPolePhotoEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.time.Instant

class UploadLuxReadingSyncHandlerTest {
    private val api = mockk<LuxReadingsApi>()
    private val dao = mockk<LuxReadingDao>(relaxed = true)
    private val photoDao = mockk<PolePhotoDao>()
    private val syncQueueDao = mockk<SyncQueueDao>()
    private val handler = UploadLuxReadingSyncHandler(api, dao, photoDao, syncQueueDao)
    private val payload = """{"client_op_id":"L1"}"""

    @Before
    fun setUp() {
        coEvery { photoDao.byId(any()) } returns null
        coEvery { syncQueueDao.statusOf(any()) } returns null
    }

    private fun photo(serverPhotoId: String? = null) =
        LocalPolePhotoEntity(
            photoId = "PHOTO-1",
            workOrderId = "WO-1",
            poleId = "POLE-1",
            filePath = "/x.jpg",
            capturedAt = Instant.parse("2026-11-23T12:41:00Z"),
            lat = 10.0,
            lng = 106.0,
            serverPhotoId = serverPhotoId,
        )

    private fun reading(serverLuxId: String? = null) =
        LocalLuxReadingEntity(
            clientOpId = "L1",
            workOrderId = "WO-1",
            poleId = "POLE-1",
            photoId = "PHOTO-1",
            measuredAt = Instant.parse("2026-11-23T12:42:00Z"),
            luxValue = 12.3,
            meterModel = "ESP32C3_LUX",
            dataSource = "field",
            serverLuxId = serverLuxId,
            createdAt = Instant.parse("2026-11-23T12:42:00Z"),
        )

    private fun response() =
        LuxReadingDto(
            luxId = "LUX-0001",
            clientOpId = "L1",
            poleId = "POLE-1",
            measuredAt = "2026-11-23T12:42:00Z",
            luxValue = 12.3,
            dataSource = "field",
            meterModel = "ESP32C3_LUX",
        )

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

    @Test
    fun `sends the reading and stores the server id`() =
        runTest {
            coEvery { dao.byId("L1") } returns reading()
            val request = slot<CreateLuxReadingRequestDto>()
            coEvery { api.create(capture(request)) } returns response()

            val result = handler.handle(payload)

            assertEquals(SyncOpResult.Done, result)
            assertEquals(
                CreateLuxReadingRequestDto(
                    clientOpId = "L1",
                    poleId = "POLE-1",
                    measuredAt = "2026-11-23T12:42:00Z",
                    luxValue = 12.3,
                    dataSource = "field",
                    meterModel = "ESP32C3_LUX",
                ),
                request.captured,
            )
            coVerify { dao.updateServerLuxId("L1", "LUX-0001") }
        }

    @Test
    fun `skips a reading that is already synced`() =
        runTest {
            coEvery { dao.byId("L1") } returns reading(serverLuxId = "LUX-0001")

            assertEquals(SyncOpResult.Done, handler.handle(payload))
            coVerify(exactly = 0) { api.create(any()) }
        }

    @Test
    fun `skips a reading that no longer exists locally`() =
        runTest {
            coEvery { dao.byId("L1") } returns null

            assertEquals(SyncOpResult.Done, handler.handle(payload))
            coVerify(exactly = 0) { api.create(any()) }
        }

    @Test
    fun `fails permanently when the pole is not found`() =
        runTest {
            coEvery { dao.byId("L1") } returns reading()
            coEvery { api.create(any()) } throws httpException(404, "POLE_NOT_FOUND")

            assertEquals(SyncOpResult.Failed("POLE_NOT_FOUND"), handler.handle(payload))
            coVerify(exactly = 0) { dao.updateServerLuxId(any(), any()) }
        }

    @Test
    fun `fails permanently on a validation error`() =
        runTest {
            coEvery { dao.byId("L1") } returns reading()
            coEvery { api.create(any()) } throws httpException(400, "VALIDATION_FAILED")

            assertEquals(SyncOpResult.Failed("VALIDATION_FAILED"), handler.handle(payload))
        }

    @Test
    fun `retries later on a server error`() =
        runTest {
            coEvery { dao.byId("L1") } returns reading()
            coEvery { api.create(any()) } throws httpException(503)

            assertEquals(SyncOpResult.RetryLater, handler.handle(payload))
        }

    @Test
    fun `retries later when the network is down`() =
        runTest {
            coEvery { dao.byId("L1") } returns reading()
            coEvery { api.create(any()) } throws IOException("offline")

            assertEquals(SyncOpResult.RetryLater, handler.handle(payload))
        }

    @Test
    fun `sends the server photo id once the photo is uploaded`() =
        runTest {
            coEvery { dao.byId("L1") } returns reading()
            coEvery { photoDao.byId("PHOTO-1") } returns photo(serverPhotoId = "PPH-000123")
            val request = slot<CreateLuxReadingRequestDto>()
            coEvery { api.create(capture(request)) } returns response()

            assertEquals(SyncOpResult.Done, handler.handle(payload))
            assertEquals("PPH-000123", request.captured.photoId)
        }

    @Test
    fun `waits while the photo upload is still pending`() =
        runTest {
            coEvery { dao.byId("L1") } returns reading()
            coEvery { photoDao.byId("PHOTO-1") } returns photo()
            coEvery { syncQueueDao.statusOf("PHOTO-1") } returns "queued"

            assertEquals(SyncOpResult.WaitingForDependency, handler.handle(payload))
            coVerify(exactly = 0) { api.create(any()) }
        }

    @Test
    fun `sends without a photo id when the photo upload failed`() =
        runTest {
            coEvery { dao.byId("L1") } returns reading()
            coEvery { photoDao.byId("PHOTO-1") } returns photo()
            coEvery { syncQueueDao.statusOf("PHOTO-1") } returns "failed"
            val request = slot<CreateLuxReadingRequestDto>()
            coEvery { api.create(capture(request)) } returns response()

            assertEquals(SyncOpResult.Done, handler.handle(payload))
            assertNull(request.captured.photoId)
        }

    @Test
    fun `resends without a photo id when the server rejects the photo link`() =
        runTest {
            coEvery { dao.byId("L1") } returns reading()
            coEvery { photoDao.byId("PHOTO-1") } returns photo(serverPhotoId = "PPH-000123")
            val requests = mutableListOf<CreateLuxReadingRequestDto>()
            coEvery { api.create(capture(requests)) } throws
                httpException(409, "PHOTO_LUX_ALREADY_LINKED") andThen response()

            assertEquals(SyncOpResult.Done, handler.handle(payload))
            assertEquals(listOf("PPH-000123", null), requests.map { it.photoId })
            coVerify { dao.updateServerLuxId("L1", "LUX-0001") }
        }

    @Test
    fun `does not resend when the pole itself is not found`() =
        runTest {
            coEvery { dao.byId("L1") } returns reading()
            coEvery { photoDao.byId("PHOTO-1") } returns photo(serverPhotoId = "PPH-000123")
            coEvery { api.create(any()) } throws httpException(404, "POLE_NOT_FOUND")

            assertEquals(SyncOpResult.Failed("POLE_NOT_FOUND"), handler.handle(payload))
            coVerify(exactly = 1) { api.create(any()) }
        }
}
