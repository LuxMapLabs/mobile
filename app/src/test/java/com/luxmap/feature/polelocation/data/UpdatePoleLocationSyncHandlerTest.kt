package com.luxmap.feature.polelocation.data

import com.luxmap.core.network.AssetsApi
import com.luxmap.core.sync.SyncOpResult
import com.luxmap.feature.polelocation.data.dto.PoleLocationResponseDto
import com.luxmap.feature.polelocation.data.dto.SetPoleLocationRequestDto
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

private fun row(status: String = PoleLocationUpdateStatus.QUEUED) =
    LocalPoleLocationUpdateEntity("POLE-1", "/f/a.jpg", 10.5, 106.5, Instant.ofEpochSecond(1), status, "OP-1")

private fun response() =
    PoleLocationResponseDto("POLE-1", 10.5, 106.5, movedM = 2.0, updatedAt = "2026-11-15T00:00:00Z")

class UpdatePoleLocationSyncHandlerTest {
    @Test
    fun `a successful PUT marks the row done and sends the stored lat lng`() =
        runTest {
            val api = mockk<AssetsApi>()
            val dao = mockk<PoleLocationUpdateDao>(relaxed = true)
            coEvery { dao.byPoleId("POLE-1") } returns row()
            coEvery { api.setPoleLocation("POLE-1", SetPoleLocationRequestDto(10.5, 106.5)) } returns response()
            val handler = UpdatePoleLocationSyncHandler(api, dao)

            val result = handler.handle(Json.encodeToString(PoleLocationSyncPayload("POLE-1")))

            assertTrue(result is SyncOpResult.Done)
            coVerify { dao.updateStatus("POLE-1", PoleLocationUpdateStatus.DONE) }
        }

    @Test
    fun `409 POLE_OFF_SEGMENT marks the row conflict, keeps the measured value`() =
        runTest {
            val api = mockk<AssetsApi>()
            val dao = mockk<PoleLocationUpdateDao>(relaxed = true)
            coEvery { dao.byPoleId("POLE-1") } returns row()
            coEvery { api.setPoleLocation(any(), any()) } throws httpException(409, "POLE_OFF_SEGMENT")
            val handler = UpdatePoleLocationSyncHandler(api, dao)

            val result = handler.handle(Json.encodeToString(PoleLocationSyncPayload("POLE-1")))

            assertTrue(result is SyncOpResult.Conflict)
            coVerify { dao.updateStatus("POLE-1", PoleLocationUpdateStatus.CONFLICT) }
        }

    @Test
    fun `a 400 marks the row failed`() =
        runTest {
            val api = mockk<AssetsApi>()
            val dao = mockk<PoleLocationUpdateDao>(relaxed = true)
            coEvery { dao.byPoleId("POLE-1") } returns row()
            coEvery { api.setPoleLocation(any(), any()) } throws httpException(400, "VALIDATION_FAILED")
            val handler = UpdatePoleLocationSyncHandler(api, dao)

            val result = handler.handle(Json.encodeToString(PoleLocationSyncPayload("POLE-1")))

            assertTrue(result is SyncOpResult.Failed)
            coVerify { dao.updateStatus("POLE-1", PoleLocationUpdateStatus.FAILED) }
        }

    @Test
    fun `a network error retries later and does not touch the row's status`() =
        runTest {
            val api = mockk<AssetsApi>()
            val dao = mockk<PoleLocationUpdateDao>(relaxed = true)
            coEvery { dao.byPoleId("POLE-1") } returns row()
            coEvery { api.setPoleLocation(any(), any()) } throws java.io.IOException("offline")
            val handler = UpdatePoleLocationSyncHandler(api, dao)

            val result = handler.handle(Json.encodeToString(PoleLocationSyncPayload("POLE-1")))

            assertTrue(result is SyncOpResult.RetryLater)
            coVerify(exactly = 0) { dao.updateStatus(any(), any()) }
        }

    @Test
    fun `the local row missing is a permanent Failed, not a crash`() =
        runTest {
            val api = mockk<AssetsApi>()
            val dao = mockk<PoleLocationUpdateDao>(relaxed = true)
            coEvery { dao.byPoleId("POLE-1") } returns null
            val handler = UpdatePoleLocationSyncHandler(api, dao)

            val result = handler.handle(Json.encodeToString(PoleLocationSyncPayload("POLE-1")))

            assertTrue(result is SyncOpResult.Failed)
        }
}
