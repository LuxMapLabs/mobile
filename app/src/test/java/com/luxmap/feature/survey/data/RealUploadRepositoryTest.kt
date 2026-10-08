package com.luxmap.feature.survey.data

import app.cash.turbine.test
import com.luxmap.core.sync.SyncQueueDao
import com.luxmap.core.sync.SyncQueueManager
import com.luxmap.core.sync.SyncQueueProcessor
import com.luxmap.feature.survey.data.dao.SurveySessionDao
import com.luxmap.feature.survey.data.entity.LocalSurveySessionEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

private fun packagedSession(syncState: String? = null) =
    LocalSurveySessionEntity(
        sessionId = "SESSION-1",
        surveySweepId = "SWEEP-LOCAL-1",
        workOrderId = "WO-1",
        recordingState = "packaged",
        syncState = syncState,
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
        gpsTrackChecksumSha256 = "a",
        luxLogChecksumSha256 = "b",
        captureConfigChecksumSha256 = "c",
        createdAt = Instant.parse("2026-10-08T10:00:00Z"),
        updatedAt = Instant.parse("2026-10-08T10:30:00Z"),
    )

class RealUploadRepositoryTest {
    @Test
    fun `first submit enqueues the full chain and emits Done once everything succeeds`() =
        runTest {
            val sessionDao = mockk<SurveySessionDao>(relaxed = true)
            val syncQueueDao = mockk<SyncQueueDao>(relaxed = true)
            coEvery { sessionDao.sessionById("SESSION-1") } returns packagedSession()
            coEvery { sessionDao.segmentsFor("SESSION-1") } returns emptyList()
            val syncQueueManager = mockk<SyncQueueManager>(relaxed = true)
            val processor = mockk<SyncQueueProcessor>()
            coEvery { processor.processQueuedOps(any()) } returns false
            coEvery { syncQueueDao.statusOf(any()) } returns "done"
            val repository = RealUploadRepository(sessionDao, syncQueueDao, syncQueueManager, processor)

            repository.uploadSession("SESSION-1").test {
                val last = expectMostRecentItem()
                assertTrue(last is UploadProgress.Done)
                cancelAndIgnoreRemainingEvents()
            }
            coVerify(atLeast = 1) { syncQueueManager.enqueue(any(), any(), any(), any()) }
        }

    @Test
    fun `a retry does not enqueue again when syncState is already set`() =
        runTest {
            val sessionDao = mockk<SurveySessionDao>(relaxed = true)
            val syncQueueDao = mockk<SyncQueueDao>(relaxed = true)
            coEvery { sessionDao.sessionById("SESSION-1") } returns packagedSession(syncState = "failed")
            coEvery { sessionDao.segmentsFor("SESSION-1") } returns emptyList()
            val syncQueueManager = mockk<SyncQueueManager>(relaxed = true)
            val processor = mockk<SyncQueueProcessor>()
            coEvery { processor.processQueuedOps(any()) } returns false
            coEvery { syncQueueDao.statusOf(any()) } returns "failed"
            val repository = RealUploadRepository(sessionDao, syncQueueDao, syncQueueManager, processor)

            repository.uploadSession("SESSION-1").test {
                cancelAndIgnoreRemainingEvents()
            }
            coVerify(exactly = 0) { syncQueueManager.enqueue(any(), any(), any(), any()) }
        }
}
