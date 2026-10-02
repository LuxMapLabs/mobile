package com.luxmap.feature.survey.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.luxmap.core.database.AppDatabase
import com.luxmap.feature.survey.data.entity.LocalSurveySessionEntity
import com.luxmap.feature.survey.data.entity.LocalSurveyVideoSegmentEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class FakeSurveyRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: FakeSurveyRepository
    private lateinit var sessionDir: File

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database =
            Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        repository = FakeSurveyRepository(database.surveySessionDao(), context)
        sessionDir = File(context.getExternalFilesDir(null), "survey/SESSION-1").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        sessionDir.deleteRecursively()
        database.close()
    }

    private fun insertSessionWithOneSegment() =
        runTest {
            database.surveySessionDao().insertSession(
                LocalSurveySessionEntity(
                    sessionId = "SESSION-1",
                    surveySweepId = "SWEEP-1",
                    recordingState = "packaged",
                    syncState = null,
                    startedAtUtc = Instant.parse("2026-10-02T20:00:00Z"),
                    startedAtElapsedNs = 0L,
                    endedAtUtc = Instant.parse("2026-10-02T20:03:00Z"),
                    durationSeconds = 180L,
                    distanceMeters = null,
                    gpsTrackFilePath = null,
                    luxLogFilePath = null,
                    headingLogFilePath = null,
                    frameTimestampLogFilePath = null,
                    captureConfigFilePath = null,
                    manifestFilePath = null,
                    packageSchemaVersion = "v0",
                    timestampSourceRealtime = true,
                    bleGapDetected = false,
                    createdAt = Instant.parse("2026-10-02T20:00:00Z"),
                    updatedAt = Instant.parse("2026-10-02T20:03:00Z"),
                ),
            )
            database.surveySessionDao().insertSegment(
                LocalSurveyVideoSegmentEntity(
                    segmentId = "SEG-0",
                    sessionId = "SESSION-1",
                    segmentIndex = 0,
                    filePath = File(sessionDir, "segment_0.mp4").absolutePath,
                    startedAtElapsedNs = 0L,
                    endedAtElapsedNs = 180_000_000_000L,
                    sizeBytes = 4096L,
                    checksumSha256 = "abc",
                ),
            )
        }

    @Test
    fun segmentFilePathsForReturnsPathsInSegmentOrder() =
        runTest {
            insertSessionWithOneSegment()

            val paths = repository.segmentFilePathsFor("SESSION-1")

            assertEquals(listOf(File(sessionDir, "segment_0.mp4").absolutePath), paths)
        }

    @Test
    fun discardSessionDeletesTheSessionDirectoryAndReturnsItsSurveySweepId() =
        runTest {
            insertSessionWithOneSegment()
            File(sessionDir, "segment_0.mp4").writeText("fake video bytes")
            assertTrue(sessionDir.exists())

            val surveySweepId = repository.discardSession("SESSION-1")

            assertEquals("SWEEP-1", surveySweepId)
            assertFalse(sessionDir.exists())
            assertNull(database.surveySessionDao().sessionById("SESSION-1"))
        }

    @Test
    fun discardSessionStillClearsRoomRowsWhenTheSessionDirectoryIsAlreadyGone() =
        runTest {
            insertSessionWithOneSegment()
            // Simulates a user who already wiped app storage manually - the directory is gone
            // but the Room rows are not. discardSession must not get stuck here.
            sessionDir.deleteRecursively()

            val surveySweepId = repository.discardSession("SESSION-1")

            assertEquals("SWEEP-1", surveySweepId)
            assertNull(database.surveySessionDao().sessionById("SESSION-1"))
        }
}
