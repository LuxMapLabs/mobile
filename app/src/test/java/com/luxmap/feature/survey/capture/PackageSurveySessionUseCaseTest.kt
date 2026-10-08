package com.luxmap.feature.survey.capture

import com.luxmap.feature.survey.data.dao.SurveySessionDao
import com.luxmap.feature.survey.data.entity.LocalSurveySessionEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant

class PackageSurveySessionUseCaseTest {
    private fun sessionWithFiles(tempDir: File): LocalSurveySessionEntity {
        val gpsTrack = File(tempDir, "gps_track.ndjson").apply { writeText("{}\n") }
        return LocalSurveySessionEntity(
            sessionId = "SESSION-1",
            surveySweepId = "SWEEP-1",
            recordingState = "stopped",
            syncState = null,
            startedAtUtc = Instant.parse("2026-09-28T20:00:00Z"),
            startedAtElapsedNs = 0L,
            endedAtUtc = Instant.parse("2026-09-28T20:30:00Z"),
            durationSeconds = 1_800L,
            distanceMeters = 5_000.0,
            gpsTrackFilePath = gpsTrack.absolutePath,
            luxLogFilePath = null,
            frameTimestampLogFilePath = null,
            captureConfigFilePath = null,
            manifestFilePath = null,
            packageSchemaVersion = "v0",
            timestampSourceRealtime = true,
            bleGapDetected = false,
            createdAt = Instant.parse("2026-09-28T20:00:00Z"),
            updatedAt = Instant.parse("2026-09-28T20:30:00Z"),
        )
    }

    @Test
    fun `packages successfully and marks the session as packaged`() =
        runTest {
            val tempDir = createTempDir()
            val session = sessionWithFiles(tempDir)
            val dao = mockk<SurveySessionDao>(relaxed = true)
            coEvery { dao.sessionById("SESSION-1") } returns session
            coEvery { dao.segmentsFor("SESSION-1") } returns emptyList()
            val useCase = PackageSurveySessionUseCase(dao)

            val result = useCase.invoke("SESSION-1")

            assertTrue(result is PackageResult.Success)
            coVerify { dao.updateSession(match { it.recordingState == "packaged" }) }
        }

    @Test
    fun `fails loudly instead of writing a manifest when a referenced file is missing`() =
        runTest {
            val tempDir = createTempDir()
            val session =
                sessionWithFiles(tempDir).copy(luxLogFilePath = File(tempDir, "does_not_exist.ndjson").absolutePath)
            val dao = mockk<SurveySessionDao>(relaxed = true)
            coEvery { dao.sessionById("SESSION-1") } returns session
            coEvery { dao.segmentsFor("SESSION-1") } returns emptyList()
            val useCase = PackageSurveySessionUseCase(dao)

            val result = useCase.invoke("SESSION-1")

            assertTrue(result is PackageResult.Failure)
            coVerify(exactly = 0) { dao.updateSession(match { it.recordingState == "packaged" }) }
        }

    @Test
    fun `strips a truncated last line from an ndjson file before packaging it`() =
        runTest {
            val tempDir = createTempDir()
            val session = sessionWithFiles(tempDir)
            // Simulate a crash-recovered session (Task 16): the last line of gps_track.ndjson is
            // cut off mid-write.
            File(session.gpsTrackFilePath!!).writeText(
                """
                {"schema_version":"v0","file_role":"gps_track"}
                {"elapsed_realtime_ns":1,"lat":10.0}
                {"elapsed_realtime_ns":2,"lat":1
                """.trimIndent(),
            )
            val dao = mockk<SurveySessionDao>(relaxed = true)
            coEvery { dao.sessionById("SESSION-1") } returns session
            coEvery { dao.segmentsFor("SESSION-1") } returns emptyList()
            val useCase = PackageSurveySessionUseCase(dao)

            useCase.invoke("SESSION-1")

            val cleanedLines = File(session.gpsTrackFilePath!!).readLines()
            assertEquals(2, cleanedLines.size) // header + the one complete data line
            assertTrue(cleanedLines[1].endsWith("}"))
        }

    @Test
    fun `fails loudly instead of crashing when the session has zero referenced files`() =
        runTest {
            val tempDir = createTempDir()
            // Every file path field is null and there are no video segments either - this can
            // happen if a crash hits before any recorder or segment ever opens a file. The use
            // case must not call List.first() on an empty list here (see PackageSurveySessionUseCase).
            val session = sessionWithFiles(tempDir).copy(gpsTrackFilePath = null)
            val dao = mockk<SurveySessionDao>(relaxed = true)
            coEvery { dao.sessionById("SESSION-1") } returns session
            coEvery { dao.segmentsFor("SESSION-1") } returns emptyList()
            val useCase = PackageSurveySessionUseCase(dao)

            val result = useCase.invoke("SESSION-1")

            assertTrue(result is PackageResult.Failure)
            coVerify(exactly = 0) { dao.updateSession(match { it.recordingState == "packaged" }) }
        }
}
