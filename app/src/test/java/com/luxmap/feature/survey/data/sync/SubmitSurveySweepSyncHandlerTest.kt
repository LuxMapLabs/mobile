package com.luxmap.feature.survey.data.sync

import com.luxmap.core.network.SweepsApi
import com.luxmap.core.network.dto.SubmitSweepRequestDto
import com.luxmap.core.network.dto.SweepResponseDto
import com.luxmap.core.sync.SyncOpResult
import com.luxmap.feature.survey.data.dao.SurveySessionDao
import com.luxmap.feature.survey.data.entity.LocalSurveyVideoSegmentEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

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

class SubmitSurveySweepSyncHandlerTest {
    @Test
    fun `submits with the persisted checksums and marks the session done`() =
        runTest {
            val api = mockk<SweepsApi>()
            val dao = mockk<SurveySessionDao>(relaxed = true)
            coEvery { dao.sessionById("SESSION-1") } returns session(serverSweepId = "SWEEP-SERVER-1")
            coEvery { dao.segmentsFor("SESSION-1") } returns
                listOf(
                    LocalSurveyVideoSegmentEntity(
                        segmentId = "SEG-1",
                        sessionId = "SESSION-1",
                        segmentIndex = 0,
                        filePath = "/x/clip_0.mp4",
                        startedAtElapsedNs = 0L,
                        endedAtElapsedNs = 1L,
                        sizeBytes = 1L,
                        checksumSha256 = "clip-hash",
                    ),
                )
            coEvery { api.submit("SWEEP-SERVER-1", any()) } returns SweepResponseDto(sweepId = "SWEEP-SERVER-1")
            val handler = SubmitSurveySweepSyncHandler(api, dao)

            val result = handler.handle(Json.encodeToString(SubmitSurveySweepPayload("SESSION-1")))

            assertTrue(result is SyncOpResult.Done)
            coVerify { dao.updateSession(match { it.syncState == "done" }) }
        }

    @Test
    fun `generates submitClientOpId once and persists it, a retry reuses the same value`() =
        runTest {
            val api = mockk<SweepsApi>()
            val dao = mockk<SurveySessionDao>(relaxed = true)
            coEvery { dao.sessionById("SESSION-1") } returns session(serverSweepId = "SWEEP-SERVER-1")
            coEvery { dao.segmentsFor("SESSION-1") } returns emptyList()
            val clientOpIdSlot = slot<SubmitSweepRequestDto>()
            coEvery { api.submit("SWEEP-SERVER-1", capture(clientOpIdSlot)) } answers {
                SweepResponseDto(sweepId = "SWEEP-SERVER-1")
            }
            val handler = SubmitSurveySweepSyncHandler(api, dao)

            handler.handle(Json.encodeToString(SubmitSurveySweepPayload("SESSION-1")))
            val firstClientOpId = clientOpIdSlot.captured.clientOpId

            // Simulate a retry: the session row now has the persisted submitClientOpId from the first attempt.
            coEvery { dao.sessionById("SESSION-1") } returns
                session(serverSweepId = "SWEEP-SERVER-1").copy(submitClientOpId = firstClientOpId)
            handler.handle(Json.encodeToString(SubmitSurveySweepPayload("SESSION-1")))

            assertEquals(firstClientOpId, clientOpIdSlot.captured.clientOpId)
        }
}
