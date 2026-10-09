package com.luxmap.feature.survey.data.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.luxmap.core.database.AppDatabase
import com.luxmap.feature.survey.data.entity.LocalSurveySessionEntity
import com.luxmap.feature.survey.data.entity.LocalSurveyVideoSegmentEntity
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class SurveySessionDaoTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: SurveySessionDao

    private fun session(
        id: String,
        recordingState: String = "recording",
    ) = LocalSurveySessionEntity(
        sessionId = id,
        surveySweepId = "SWEEP-1",
        workOrderId = "WO-1",
        recordingState = recordingState,
        syncState = null,
        startedAtUtc = Instant.parse("2026-09-28T20:00:00Z"),
        startedAtElapsedNs = 0L,
        endedAtUtc = null,
        durationSeconds = null,
        distanceMeters = null,
        gpsTrackFilePath = null,
        luxLogFilePath = null,
        frameTimestampLogFilePath = null,
        captureConfigFilePath = null,
        manifestFilePath = null,
        packageSchemaVersion = "v0",
        timestampSourceRealtime = true,
        bleGapDetected = false,
        createdAt = Instant.parse("2026-09-28T20:00:00Z"),
        updatedAt = Instant.parse("2026-09-28T20:00:00Z"),
    )

    @Before
    fun setUp() {
        database =
            Room
                .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        dao = database.surveySessionDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun sessionsInRecordingStateFindsASessionLeftMidRecording() =
        runTest {
            dao.insertSession(session("SESSION-1", recordingState = "recording"))
            dao.insertSession(session("SESSION-2", recordingState = "packaged"))

            val stuck = dao.sessionsInRecordingState()

            assertEquals(1, stuck.size)
            assertEquals("SESSION-1", stuck.first().sessionId)
        }

    @Test
    fun unfinalizedSegmentIsFoundByANullEndedAtElapsedNs() =
        runTest {
            dao.insertSession(session("SESSION-1"))
            dao.insertSegment(
                LocalSurveyVideoSegmentEntity(
                    segmentId = "SEG-0",
                    sessionId = "SESSION-1",
                    segmentIndex = 0,
                    filePath = "/data/segment_0.mp4",
                    startedAtElapsedNs = 0L,
                    endedAtElapsedNs = null,
                    sizeBytes = null,
                    checksumSha256 = null,
                ),
            )

            val unfinalized = dao.unfinalizedSegmentFor("SESSION-1")

            assertNotNull(unfinalized)
            assertEquals("SEG-0", unfinalized?.segmentId)
        }

    @Test
    fun noUnfinalizedSegmentOnceItHasBeenClosed() =
        runTest {
            dao.insertSession(session("SESSION-1"))
            dao.insertSegment(
                LocalSurveyVideoSegmentEntity(
                    segmentId = "SEG-0",
                    sessionId = "SESSION-1",
                    segmentIndex = 0,
                    filePath = "/data/segment_0.mp4",
                    startedAtElapsedNs = 0L,
                    endedAtElapsedNs = 180_000_000_000L,
                    sizeBytes = 4096L,
                    checksumSha256 = "abc",
                ),
            )

            assertNull(dao.unfinalizedSegmentFor("SESSION-1"))
        }

    @Test
    fun deleteSessionAndSegmentsRemovesBothTheSessionAndItsSegments() =
        runTest {
            dao.insertSession(session("SESSION-1"))
            dao.insertSegment(
                LocalSurveyVideoSegmentEntity(
                    segmentId = "SEG-0",
                    sessionId = "SESSION-1",
                    segmentIndex = 0,
                    filePath = "/data/segment_0.mp4",
                    startedAtElapsedNs = 0L,
                    endedAtElapsedNs = 180_000_000_000L,
                    sizeBytes = 4096L,
                    checksumSha256 = "abc",
                ),
            )

            dao.deleteSessionAndSegments("SESSION-1")

            assertNull(dao.sessionById("SESSION-1"))
            assertEquals(0, dao.segmentsFor("SESSION-1").size)
        }

    @Test
    fun claimForUploadOnlyLetsOneConcurrentCallerClaimASession() =
        runTest {
            dao.insertSession(session("SESSION-1"))

            val results =
                listOf(
                    async { dao.claimForUpload("SESSION-1", Instant.parse("2026-10-09T10:00:00Z")) },
                    async { dao.claimForUpload("SESSION-1", Instant.parse("2026-10-09T10:00:01Z")) },
                ).awaitAll()

            assertEquals(1, results.count { it == 1 })
            assertEquals(1, results.count { it == 0 })
            assertEquals("queued", dao.sessionById("SESSION-1")?.syncState)
        }
}
