package com.luxmap.feature.survey.data.sync

import com.luxmap.core.network.SweepsApi
import com.luxmap.core.network.dto.SurveyRawResponseDto
import com.luxmap.core.sync.SyncOpResult
import com.luxmap.feature.survey.data.dao.SurveySessionDao
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

private fun httpException(code: Int) =
    HttpException(Response.error<Any>(code, "".toResponseBody("application/json".toMediaType())))

// Same per-file fixture convention as UploadSurveyClipSyncHandlerTest - see its comment.
private fun session(serverSweepId: String? = null) =
    com.luxmap.feature.survey.data.entity.LocalSurveySessionEntity(
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

class UploadSurveyRawSyncHandlerTest {
    @Test
    fun `uploads gps_track with its persisted checksum and returns Done`() =
        runTest {
            val gpsFile = File.createTempFile("gps_track", ".ndjson").apply { writeText("line\n") }
            val api = mockk<SweepsApi>()
            val dao = mockk<SurveySessionDao>(relaxed = true)
            coEvery { dao.sessionById("SESSION-1") } returns
                session(serverSweepId = "SWEEP-SERVER-1").copy(gpsTrackFilePath = gpsFile.absolutePath)
            coEvery { api.uploadRaw("SWEEP-SERVER-1", "gps_track", any()) } returns
                SurveyRawResponseDto(kind = "gps_track", sha256 = "a")
            val handler = UploadSurveyRawSyncHandler(api, dao)

            val result = handler.handle(Json.encodeToString(UploadSurveyRawPayload("SESSION-1", "gps_track")))

            assertTrue(result is SyncOpResult.Done)
        }

    @Test
    fun `a 409 on raw upload maps to Conflict`() =
        runTest {
            val configFile = File.createTempFile("capture_config", ".json").apply { writeText("{}") }
            val api = mockk<SweepsApi>()
            val dao = mockk<SurveySessionDao>(relaxed = true)
            coEvery { dao.sessionById("SESSION-1") } returns
                session(serverSweepId = "SWEEP-SERVER-1").copy(captureConfigFilePath = configFile.absolutePath)
            coEvery { api.uploadRaw(any(), any(), any()) } throws httpException(409)
            val handler = UploadSurveyRawSyncHandler(api, dao)

            val result = handler.handle(Json.encodeToString(UploadSurveyRawPayload("SESSION-1", "capture_config")))

            assertTrue(result is SyncOpResult.Conflict)
        }
}
