package com.luxmap.feature.survey.capture

import com.luxmap.feature.survey.data.dao.SurveySessionDao
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import javax.inject.Inject

sealed interface PackageResult {
    data class Success(val manifestFilePath: String) : PackageResult

    data class Failure(val reason: String) : PackageResult
}

// Runs on "Stop recording" or after crash recovery (Task 16) closes out the remaining segments.
// Fails loudly rather than writing a manifest that references a file that is not actually there
// (spec section 14 Review Focus) - a missing file is a real data-loss event, not something to paper over.
// Deliberate v0 simplification vs spec section 8's example: the "device" block is not duplicated in
// manifest.json since capture_config.json (Task 17d) already carries it and both files are always
// packaged together - flagged here, not silently dropped, so a reviewer can override it later.
class PackageSurveySessionUseCase
    @Inject
    constructor(
        private val dao: SurveySessionDao,
    ) {
        suspend fun invoke(sessionId: String): PackageResult {
            val session = dao.sessionById(sessionId) ?: return PackageResult.Failure("Session $sessionId not found")
            val segments = dao.segmentsFor(sessionId)

            // (path, role, segmentIndex) - role/segmentIndex are what let the manifest tell a video
            // segment apart from the four log files (spec section 8's files[] entries).
            val referencedFiles =
                listOfNotNull(
                    session.gpsTrackFilePath?.let { Triple(it, "gps_track", null) },
                    session.luxLogFilePath?.let { Triple(it, "lux_log", null) },
                    session.frameTimestampLogFilePath?.let { Triple(it, "frame_timestamp_log", null) },
                    session.captureConfigFilePath?.let { Triple(it, "capture_config", null) },
                ) + segments.map { Triple(it.filePath, "video_segment", it.segmentIndex) }

            // A crash before any recorder ever opened a file (and before any video segment ever
            // opened) leaves every path field null and segments empty. In normal operation this
            // does not happen - the foreground service writes all five log/config paths together
            // in one insertSession call before recording starts - but calling .first() on an
            // empty list would still crash with NoSuchElementException, so guard it explicitly
            // instead of relying on that invariant holding forever.
            if (referencedFiles.isEmpty()) {
                return PackageResult.Failure("Session $sessionId has no referenced files to package")
            }

            val missing = referencedFiles.filterNot { (path, _, _) -> File(path).exists() }
            if (missing.isNotEmpty()) {
                return PackageResult.Failure("Missing file(s) at packaging time: ${missing.map { it.first }}")
            }

            referencedFiles.forEach { (path, _, _) -> cleanIfNdjson(File(path)) }

            val manifestFile = File(File(referencedFiles.first().first).parentFile, "manifest.json")
            manifestFile.writeText(
                buildManifestJson(
                    sessionId = session.sessionId,
                    surveySweepId = session.surveySweepId,
                    startedAtUtc = session.startedAtUtc.toString(),
                    endedAtUtc = session.endedAtUtc?.toString(),
                    files = referencedFiles,
                ),
            )

            dao.updateSession(
                session.copy(
                    recordingState = "packaged",
                    manifestFilePath = manifestFile.absolutePath,
                    updatedAt = Instant.now(),
                ),
            )
            return PackageResult.Success(manifestFile.absolutePath)
        }

        // A session recovered after a crash (Task 16) can carry a truncated last line in a log
        // file (spec section 12) - rewrite it here, once, using the same tolerant reader, instead of
        // shipping a torn JSON line in the package.
        private fun cleanIfNdjson(file: File) {
            if (!file.name.endsWith(".ndjson")) return
            val header = file.readLines().firstOrNull() ?: return
            val dataLines = NdjsonLogReader.readDataLines(file)
            file.writeText((listOf(header) + dataLines).joinToString("\n", postfix = "\n"))
        }

        private fun sha256Of(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { stream -> stream.copyTo(DigestOutputStream(digest)) }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }

        private fun buildManifestJson(
            sessionId: String,
            surveySweepId: String,
            startedAtUtc: String,
            endedAtUtc: String?,
            files: List<Triple<String, String, Int?>>,
        ): String {
            val filesJson =
                files.joinToString(",") { (path, role, segmentIndex) ->
                    val file = File(path)
                    val checksum = sha256Of(file)
                    val segmentField = if (segmentIndex != null) ""","segment_index":$segmentIndex""" else ""
                    """{"name":"${file.name}","role":"$role"$segmentField,""" +
                        """"checksum_sha256":"$checksum","size_bytes":${file.length()}}"""
                }
            val endedAtField = if (endedAtUtc != null) """"ended_at_utc":"$endedAtUtc"""" else """"ended_at_utc":null"""
            return """{"schema_version":"v0","session_id":"$sessionId","survey_sweep_id":"$surveySweepId",""" +
                """"started_at_utc":"$startedAtUtc",$endedAtField,"files":[$filesJson]}"""
        }
    }

private class DigestOutputStream(
    private val digest: MessageDigest,
) : java.io.OutputStream() {
    override fun write(b: Int) = digest.update(b.toByte())

    override fun write(
        b: ByteArray,
        off: Int,
        len: Int,
    ) = digest.update(b, off, len)
}
