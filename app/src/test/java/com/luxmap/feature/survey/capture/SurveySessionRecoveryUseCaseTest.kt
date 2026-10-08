package com.luxmap.feature.survey.capture

import com.luxmap.feature.survey.data.dao.SurveySessionDao
import com.luxmap.feature.survey.data.entity.LocalSurveySessionEntity
import com.luxmap.feature.survey.data.entity.LocalSurveyVideoSegmentEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.File
import java.io.IOException
import java.time.Instant

class SurveySessionRecoveryUseCaseTest {
    private fun stuckSession(sessionId: String = "SESSION-1") =
        LocalSurveySessionEntity(
            sessionId = sessionId,
            surveySweepId = "SWEEP-1",
            recordingState = "recording",
            syncState = null,
            startedAtUtc = Instant.parse("2026-09-28T20:00:00Z"),
            startedAtElapsedNs = 0L,
            endedAtUtc = null,
            durationSeconds = null,
            distanceMeters = null,
            gpsTrackFilePath = "/data/gps_track.ndjson",
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

    @Test
    fun `drops the unfinalized segment's row and file, then marks the session stopped before packaging`() =
        runTest {
            val danglingFile = File.createTempFile("segment_1", ".mp4")
            val dao = mockk<SurveySessionDao>(relaxed = true)
            coEvery { dao.sessionsInRecordingState() } returns listOf(stuckSession())
            coEvery { dao.unfinalizedSegmentFor("SESSION-1") } returns
                LocalSurveyVideoSegmentEntity("SEG-1", "SESSION-1", 1, danglingFile.absolutePath, 0L, null, null, null)
            val packager = mockk<PackageSurveySessionUseCase>()
            coEvery { packager.invoke("SESSION-1") } returns PackageResult.Success("/data/manifest.json")

            SurveySessionRecoveryUseCase(dao, packager).recoverAny()

            coVerify { dao.deleteSegment("SEG-1") }
            assertFalseFileExists(danglingFile)
            coVerify { dao.updateSession(match { it.sessionId == "SESSION-1" && it.recordingState == "stopped" }) }
            coVerify { packager.invoke("SESSION-1") }
        }

    @Test
    fun `handles a session with zero video segments without crashing`() =
        runTest {
            val dao = mockk<SurveySessionDao>(relaxed = true)
            coEvery { dao.sessionsInRecordingState() } returns listOf(stuckSession())
            coEvery { dao.unfinalizedSegmentFor("SESSION-1") } returns null
            val packager = mockk<PackageSurveySessionUseCase>()
            coEvery { packager.invoke("SESSION-1") } returns PackageResult.Success("/data/manifest.json")

            SurveySessionRecoveryUseCase(dao, packager).recoverAny()

            coVerify(exactly = 0) { dao.deleteSegment(any()) }
            coVerify { dao.updateSession(match { it.sessionId == "SESSION-1" && it.recordingState == "stopped" }) }
            coVerify { packager.invoke("SESSION-1") }
        }

    @Test
    fun `marks the session package_failed instead of packaged when packaging fails`() =
        runTest {
            val dao = mockk<SurveySessionDao>(relaxed = true)
            coEvery { dao.sessionsInRecordingState() } returns listOf(stuckSession())
            coEvery { dao.unfinalizedSegmentFor("SESSION-1") } returns null
            val packager = mockk<PackageSurveySessionUseCase>()
            coEvery { packager.invoke("SESSION-1") } returns PackageResult.Failure("missing file")

            SurveySessionRecoveryUseCase(dao, packager).recoverAny()

            coVerify {
                dao.updateSession(
                    match { it.sessionId == "SESSION-1" && it.recordingState == "package_failed" },
                )
            }
        }

    // dao.sessionsInRecordingState() can plausibly return more than one stuck session (e.g. two
    // capture flows were left open by a prior bug), and this whole use case runs from
    // LuxMapApp.onCreate() inside a scope with no exception handler. Without per-session isolation,
    // one session throwing (I/O error, SecurityException from File.delete(), etc.) would stop the
    // forEach loop, leave every later session stuck in "recording" forever, and crash the app on
    // every future startup, since recovery is exactly the code path meant to run after a crash.
    @Test
    fun `keeps recovering later sessions when an earlier one throws, and marks it package_failed`() =
        runTest {
            val dao = mockk<SurveySessionDao>(relaxed = true)
            coEvery { dao.sessionsInRecordingState() } returns
                listOf(stuckSession("SESSION-1"), stuckSession("SESSION-2"))
            coEvery { dao.unfinalizedSegmentFor("SESSION-1") } throws IOException("disk read error")
            coEvery { dao.unfinalizedSegmentFor("SESSION-2") } returns null
            val packager = mockk<PackageSurveySessionUseCase>()
            coEvery { packager.invoke("SESSION-2") } returns PackageResult.Success("/data/manifest.json")

            SurveySessionRecoveryUseCase(dao, packager).recoverAny()

            coVerify {
                dao.updateSession(
                    match { it.sessionId == "SESSION-1" && it.recordingState == "package_failed" },
                )
            }
            coVerify { dao.updateSession(match { it.sessionId == "SESSION-2" && it.recordingState == "stopped" }) }
            coVerify { packager.invoke("SESSION-2") }
        }

    private fun assertFalseFileExists(file: File) {
        org.junit.Assert.assertFalse(file.exists())
    }
}
