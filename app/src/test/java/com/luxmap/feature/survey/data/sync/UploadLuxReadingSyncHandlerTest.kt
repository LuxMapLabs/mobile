package com.luxmap.feature.survey.data.sync

import com.luxmap.core.network.LuxReadingsApi
import com.luxmap.core.sync.SyncOpResult
import com.luxmap.feature.survey.data.dao.LuxReadingDao
import com.luxmap.feature.survey.data.dto.CreateLuxReadingRequestDto
import com.luxmap.feature.survey.data.dto.LuxReadingDto
import com.luxmap.feature.survey.data.entity.LocalLuxReadingEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.time.Instant

class UploadLuxReadingSyncHandlerTest {
    private val api = mockk<LuxReadingsApi>()
    private val dao = mockk<LuxReadingDao>(relaxed = true)
    private val handler = UploadLuxReadingSyncHandler(api, dao)
    private val payload = """{"client_op_id":"L1"}"""

    private fun reading(serverLuxId: String? = null) =
        LocalLuxReadingEntity(
            clientOpId = "L1",
            workOrderId = "WO-1",
            poleId = "POLE-1",
            photoId = "PHOTO-1",
            measuredAt = Instant.parse("2026-11-23T12:42:00Z"),
            luxValue = 12.3,
            meterModel = "LuxMap_ESP32",
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
            meterModel = "LuxMap_ESP32",
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
                    meterModel = "LuxMap_ESP32",
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
}
