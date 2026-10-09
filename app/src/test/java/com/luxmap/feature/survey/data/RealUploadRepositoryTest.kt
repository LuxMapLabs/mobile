package com.luxmap.feature.survey.data

import androidx.room.withTransaction
import app.cash.turbine.test
import com.luxmap.core.database.AppDatabase
import com.luxmap.core.sync.SyncQueueDao
import com.luxmap.core.sync.SyncQueueManager
import com.luxmap.core.sync.SyncQueueProcessor
import com.luxmap.feature.survey.data.dao.SurveySessionDao
import com.luxmap.feature.survey.data.entity.LocalSurveySessionEntity
import com.luxmap.feature.survey.data.entity.LocalSurveyVideoSegmentEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
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

private fun videoSegment(segmentIndex: Int) =
    LocalSurveyVideoSegmentEntity(
        segmentId = "SEG-$segmentIndex",
        sessionId = "SESSION-1",
        segmentIndex = segmentIndex,
        filePath = "/x/clip$segmentIndex.mp4",
        startedAtElapsedNs = 1L,
        endedAtElapsedNs = 2L,
        sizeBytes = 1_000L,
        checksumSha256 = "checksum$segmentIndex",
    )

class RealUploadRepositoryTest {
    // RealUploadRepository.uploadSession runs claimForUpload+enqueueChain through
    // AppDatabase.withTransaction, a top-level Room extension function - mockkStatic is the only
    // way to stub it on a plain mockk<AppDatabase>(), which has no real transaction machinery.
    @Before
    fun setUp() {
        mockkStatic("androidx.room.RoomDatabaseKt")
    }

    @After
    fun tearDown() {
        unmockkStatic("androidx.room.RoomDatabaseKt")
    }

    // The real withTransaction runs its block and returns its result (or rolls back and rethrows
    // on a failure) - this passthrough stub is the "happy path" shape every test below needs,
    // since none of them are testing withTransaction's own rollback mechanics (that is Room's
    // own contract, not this repository's code).
    private fun fakeDatabase(): AppDatabase {
        val database = mockk<AppDatabase>()
        // mockkStatic turns this call into a static RoomDatabaseKt.withTransaction(receiver, block,
        // continuation) invocation, so the receiver (the AppDatabase mock) is arg 0 and the block
        // is arg 1 - secondArg, not firstArg.
        coEvery { database.withTransaction(any<suspend () -> Boolean>()) } coAnswers {
            secondArg<suspend () -> Boolean>().invoke()
        }
        return database
    }

    @Test
    fun `first submit enqueues the full chain and emits Done once everything succeeds`() =
        runTest {
            val database = fakeDatabase()
            val sessionDao = mockk<SurveySessionDao>(relaxed = true)
            val syncQueueDao = mockk<SyncQueueDao>(relaxed = true)
            coEvery { sessionDao.sessionById("SESSION-1") } returns packagedSession()
            coEvery { sessionDao.segmentsFor("SESSION-1") } returns emptyList()
            coEvery { sessionDao.claimForUpload(any(), any()) } returns 1
            val syncQueueManager = mockk<SyncQueueManager>(relaxed = true)
            val processor = mockk<SyncQueueProcessor>()
            coEvery { processor.processQueuedOps(any()) } returns false
            coEvery { syncQueueDao.statusOf(any()) } returns "done"
            val repository = RealUploadRepository(database, sessionDao, syncQueueDao, syncQueueManager, processor)

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
            val database = fakeDatabase()
            val sessionDao = mockk<SurveySessionDao>(relaxed = true)
            val syncQueueDao = mockk<SyncQueueDao>(relaxed = true)
            coEvery { sessionDao.sessionById("SESSION-1") } returns packagedSession(syncState = "failed")
            coEvery { sessionDao.segmentsFor("SESSION-1") } returns emptyList()
            coEvery { sessionDao.claimForUpload(any(), any()) } returns 0
            val syncQueueManager = mockk<SyncQueueManager>(relaxed = true)
            val processor = mockk<SyncQueueProcessor>()
            coEvery { processor.processQueuedOps(any()) } returns false
            coEvery { syncQueueDao.statusOf(any()) } returns "failed"
            val repository = RealUploadRepository(database, sessionDao, syncQueueDao, syncQueueManager, processor)

            repository.uploadSession("SESSION-1").test {
                cancelAndIgnoreRemainingEvents()
            }
            coVerify(exactly = 0) { syncQueueManager.enqueue(any(), any(), any(), any()) }
        }

    @Test
    fun `a retry resets failed ops back to queued so the processor can pick them up again`() =
        runTest {
            val database = fakeDatabase()
            val sessionDao = mockk<SurveySessionDao>(relaxed = true)
            val syncQueueDao = mockk<SyncQueueDao>(relaxed = true)
            coEvery { sessionDao.sessionById("SESSION-1") } returns packagedSession(syncState = "failed")
            coEvery { sessionDao.segmentsFor("SESSION-1") } returns emptyList()
            coEvery { sessionDao.claimForUpload(any(), any()) } returns 0
            val syncQueueManager = mockk<SyncQueueManager>(relaxed = true)
            val processor = mockk<SyncQueueProcessor>()
            coEvery { processor.processQueuedOps(any()) } returns false
            coEvery { syncQueueDao.statusOf(any()) } returns "done"
            val repository = RealUploadRepository(database, sessionDao, syncQueueDao, syncQueueManager, processor)

            repository.uploadSession("SESSION-1").test {
                cancelAndIgnoreRemainingEvents()
            }

            coVerify(exactly = 1) {
                syncQueueDao.resetFailedOps(
                    listOf(
                        "SESSION-1:create_sweep",
                        "SESSION-1:raw:gps_track",
                        "SESSION-1:raw:lux_log",
                        "SESSION-1:raw:capture_config",
                        "SESSION-1:submit",
                    ),
                    any(),
                )
            }
        }

    @Test
    fun `each clip op depends on the immediately preceding op, not on create_sweep directly`() =
        runTest {
            val database = fakeDatabase()
            val sessionDao = mockk<SurveySessionDao>(relaxed = true)
            val syncQueueDao = mockk<SyncQueueDao>(relaxed = true)
            coEvery { sessionDao.sessionById("SESSION-1") } returns packagedSession()
            coEvery { sessionDao.segmentsFor("SESSION-1") } returns
                listOf(videoSegment(segmentIndex = 0), videoSegment(segmentIndex = 1))
            coEvery { sessionDao.claimForUpload(any(), any()) } returns 1
            val syncQueueManager = mockk<SyncQueueManager>(relaxed = true)
            val processor = mockk<SyncQueueProcessor>()
            coEvery { processor.processQueuedOps(any()) } returns false
            coEvery { syncQueueDao.statusOf(any()) } returns "done"
            val repository = RealUploadRepository(database, sessionDao, syncQueueDao, syncQueueManager, processor)

            repository.uploadSession("SESSION-1").test {
                cancelAndIgnoreRemainingEvents()
            }

            coVerify {
                syncQueueManager.enqueue(
                    opType = "upload_survey_clip",
                    payloadJson = any(),
                    clientOpId = "SESSION-1:clip:0",
                    dependsOnClientOpId = "SESSION-1:create_sweep",
                )
            }
            coVerify {
                syncQueueManager.enqueue(
                    opType = "upload_survey_clip",
                    payloadJson = any(),
                    clientOpId = "SESSION-1:clip:1",
                    dependsOnClientOpId = "SESSION-1:clip:0",
                )
            }
        }

    @Test
    fun `onRowProgress invocations from the processor are forwarded as InProgress`() =
        runTest {
            val database = fakeDatabase()
            val sessionDao = mockk<SurveySessionDao>(relaxed = true)
            val syncQueueDao = mockk<SyncQueueDao>(relaxed = true)
            coEvery { sessionDao.sessionById("SESSION-1") } returns packagedSession()
            coEvery { sessionDao.segmentsFor("SESSION-1") } returns emptyList()
            coEvery { sessionDao.claimForUpload(any(), any()) } returns 1
            val syncQueueManager = mockk<SyncQueueManager>(relaxed = true)
            val processor = mockk<SyncQueueProcessor>()
            coEvery { processor.processQueuedOps(any()) } coAnswers {
                val onRowProgress = firstArg<suspend (String, Long, Long) -> Unit>()
                onRowProgress("SESSION-1:create_sweep", 50L, 100L)
                false
            }
            coEvery { syncQueueDao.statusOf(any()) } returns "done"
            val repository = RealUploadRepository(database, sessionDao, syncQueueDao, syncQueueManager, processor)

            repository.uploadSession("SESSION-1").test {
                awaitItem() // initial InProgress(0, totalBytes) emitted before processing starts
                val progress = awaitItem()
                assertTrue(progress is UploadProgress.InProgress)
                assertTrue((progress as UploadProgress.InProgress).bytesSent == 50L)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `claim and enqueue run inside one withTransaction call`() =
        runTest {
            val database = fakeDatabase()
            val sessionDao = mockk<SurveySessionDao>(relaxed = true)
            val syncQueueDao = mockk<SyncQueueDao>(relaxed = true)
            coEvery { sessionDao.sessionById("SESSION-1") } returns packagedSession()
            coEvery { sessionDao.segmentsFor("SESSION-1") } returns emptyList()
            coEvery { sessionDao.claimForUpload(any(), any()) } returns 1
            val syncQueueManager = mockk<SyncQueueManager>(relaxed = true)
            val processor = mockk<SyncQueueProcessor>()
            coEvery { processor.processQueuedOps(any()) } returns false
            coEvery { syncQueueDao.statusOf(any()) } returns "done"
            val repository = RealUploadRepository(database, sessionDao, syncQueueDao, syncQueueManager, processor)

            repository.uploadSession("SESSION-1").test {
                cancelAndIgnoreRemainingEvents()
            }

            coVerify(exactly = 1) { database.withTransaction(any()) }
            coVerify(atLeast = 1) { syncQueueManager.enqueue(any(), any(), any(), any()) }
        }

    @Test
    fun `a failure partway through enqueueChain propagates out of the withTransaction block`() =
        runTest {
            val database = fakeDatabase()
            val sessionDao = mockk<SurveySessionDao>(relaxed = true)
            val syncQueueDao = mockk<SyncQueueDao>(relaxed = true)
            coEvery { sessionDao.sessionById("SESSION-1") } returns packagedSession()
            coEvery { sessionDao.segmentsFor("SESSION-1") } returns emptyList()
            coEvery { sessionDao.claimForUpload(any(), any()) } returns 1
            val syncQueueManager = mockk<SyncQueueManager>(relaxed = true)
            coEvery { syncQueueManager.enqueue(any(), any(), any(), any()) } throws
                IllegalStateException("simulated cancellation mid-enqueue")
            val processor = mockk<SyncQueueProcessor>()
            val repository = RealUploadRepository(database, sessionDao, syncQueueDao, syncQueueManager, processor)

            // A failure here is exactly what makes Room's real withTransaction roll back the
            // claimForUpload UPDATE too, instead of leaving a claimed session with an empty
            // chain forever stuck in the "already claimed, nothing to reset" else-branch.
            repository.uploadSession("SESSION-1").test {
                val error = awaitError()
                assertTrue(error is IllegalStateException)
            }
        }
}
