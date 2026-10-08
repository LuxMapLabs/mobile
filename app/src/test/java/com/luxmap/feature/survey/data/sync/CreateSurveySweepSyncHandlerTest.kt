package com.luxmap.feature.survey.data.sync

import com.luxmap.core.network.SweepsApi
import com.luxmap.core.network.dto.SweepResponseDto
import com.luxmap.core.sync.SyncOpResult
import com.luxmap.feature.survey.data.dao.SurveySessionDao
import com.luxmap.feature.survey.data.entity.LocalSurveySessionEntity
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

private fun httpException(code: Int) =
    HttpException(Response.error<Any>(code, "".toResponseBody("application/json".toMediaType())))

private fun session(serverSweepId: String? = null) =
    LocalSurveySessionEntity(
        sessionId = "SESSION-1",
        surveySweepId = "SWEEP-LOCAL-1",
        workOrderId = "WO-1",
        recordingState = "packaged",
        syncState = "queued",
        startedAtUtc = Instant.parse("2026-10-08T10:00:00Z"),
        startedAtElapsedNs = 1L,
        endedAtUtc = Instant.parse("2026-10-08T10:30:00Z"),
        durationSeconds = 1800L,
        distanceMeters = 5000.0,
        gpsTrackFilePath = "/x/gps_track.ndjson",
        luxLogFilePath = "/x/lux_log.ndjson",
        frameTimestampLogFilePath = null,
        captureConfigFilePath = "/x/capture_config.json",
        manifestFilePath = "/x/manifest.json",
        packageSchemaVersion = "v1",
        timestampSourceRealtime = true,
        bleGapDetected = false,
        serverSweepId = serverSweepId,
        submitClientOpId = null,
        gpsTrackChecksumSha256 = "a",
        luxLogChecksumSha256 = "b",
        captureConfigChecksumSha256 = "c",
        createdAt = Instant.parse("2026-10-08T10:00:00Z"),
        updatedAt = Instant.parse("2026-10-08T10:30:00Z"),
    )

class CreateSurveySweepSyncHandlerTest {
    @Test
    fun `creates a sweep and persists the server-assigned sweep_id`() =
        runTest {
            val api = mockk<SweepsApi>()
            val dao = mockk<SurveySessionDao>(relaxed = true)
            coEvery { dao.sessionById("SESSION-1") } returns session()
            coEvery { api.create(any()) } returns SweepResponseDto(sweepId = "SWEEP-SERVER-1")
            val handler = CreateSurveySweepSyncHandler(api, dao)

            val result = handler.handle(Json.encodeToString(CreateSurveySweepPayload("SESSION-1")))

            assertTrue(result is SyncOpResult.Done)
            coVerify { dao.updateSession(match { it.serverSweepId == "SWEEP-SERVER-1" }) }
        }

    @Test
    fun `a retry after serverSweepId is already set does not call create again`() =
        runTest {
            val api = mockk<SweepsApi>()
            val dao = mockk<SurveySessionDao>(relaxed = true)
            coEvery { dao.sessionById("SESSION-1") } returns session(serverSweepId = "SWEEP-SERVER-1")
            val handler = CreateSurveySweepSyncHandler(api, dao)

            val result = handler.handle(Json.encodeToString(CreateSurveySweepPayload("SESSION-1")))

            assertTrue(result is SyncOpResult.Done)
            coVerify(exactly = 0) { api.create(any()) }
        }

    @Test
    fun `a 500 maps to RetryLater`() =
        runTest {
            val api = mockk<SweepsApi>()
            val dao = mockk<SurveySessionDao>(relaxed = true)
            coEvery { dao.sessionById("SESSION-1") } returns session()
            coEvery { api.create(any()) } throws httpException(500)
            val handler = CreateSurveySweepSyncHandler(api, dao)

            val result = handler.handle(Json.encodeToString(CreateSurveySweepPayload("SESSION-1")))

            assertTrue(result is SyncOpResult.RetryLater)
        }
}
