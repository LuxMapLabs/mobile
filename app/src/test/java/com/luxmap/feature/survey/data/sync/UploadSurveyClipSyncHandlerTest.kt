package com.luxmap.feature.survey.data.sync

import com.luxmap.core.network.SweepsApi
import com.luxmap.core.network.dto.SurveyClipResponseDto
import com.luxmap.core.sync.SyncOpResult
import com.luxmap.feature.survey.data.dao.SurveySessionDao
import com.luxmap.feature.survey.data.entity.LocalSurveyVideoSegmentEntity
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

// Each sync-handler test file defines its own copy of this fixture, same convention as
// PackageSurveySessionUseCaseTest's sessionWithFiles() - no shared test-helper file in this
// project, kept consistent here rather than introducing a new pattern.
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

class UploadSurveyClipSyncHandlerTest {
    @Test
    fun `uploads the clip file with its persisted checksum and returns Done`() =
        runTest {
            val clipFile = File.createTempFile("clip", ".mp4").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            val api = mockk<SweepsApi>()
            val dao = mockk<SurveySessionDao>(relaxed = true)
            coEvery { dao.sessionById("SESSION-1") } returns session(serverSweepId = "SWEEP-SERVER-1")
            coEvery { dao.segmentsFor("SESSION-1") } returns
                listOf(
                    LocalSurveyVideoSegmentEntity(
                        segmentId = "SEG-1",
                        sessionId = "SESSION-1",
                        segmentIndex = 0,
                        filePath = clipFile.absolutePath,
                        startedAtElapsedNs = 0L,
                        endedAtElapsedNs = 1L,
                        sizeBytes = 3L,
                        checksumSha256 = "abc123",
                    ),
                )
            coEvery { api.uploadClip("SWEEP-SERVER-1", 0, "abc123", any()) } returns
                SurveyClipResponseDto(clipNo = 0, sha256 = "abc123")
            val handler = UploadSurveyClipSyncHandler(api, dao)

            val result = handler.handle(Json.encodeToString(UploadSurveyClipPayload("SESSION-1", 0)))

            assertTrue(result is SyncOpResult.Done)
        }

    @Test
    fun `a 409 maps to Conflict, not Failed or RetryLater`() =
        runTest {
            val clipFile = File.createTempFile("clip", ".mp4").apply { writeBytes(byteArrayOf(1)) }
            val api = mockk<SweepsApi>()
            val dao = mockk<SurveySessionDao>(relaxed = true)
            coEvery { dao.sessionById("SESSION-1") } returns session(serverSweepId = "SWEEP-SERVER-1")
            coEvery { dao.segmentsFor("SESSION-1") } returns
                listOf(
                    LocalSurveyVideoSegmentEntity(
                        segmentId = "SEG-1",
                        sessionId = "SESSION-1",
                        segmentIndex = 0,
                        filePath = clipFile.absolutePath,
                        startedAtElapsedNs = 0L,
                        endedAtElapsedNs = 1L,
                        sizeBytes = 1L,
                        checksumSha256 = "abc",
                    ),
                )
            coEvery { api.uploadClip(any(), any(), any(), any()) } throws httpException(409)
            val handler = UploadSurveyClipSyncHandler(api, dao)

            val result = handler.handle(Json.encodeToString(UploadSurveyClipPayload("SESSION-1", 0)))

            assertTrue(result is SyncOpResult.Conflict)
        }
}
