package com.luxmap.feature.survey.data.sync

import com.luxmap.core.network.SweepsApi
import com.luxmap.core.network.dto.ClipManifestDto
import com.luxmap.core.network.dto.SubmitSweepRequestDto
import com.luxmap.core.network.dto.SweepManifestDto
import com.luxmap.core.network.errorCodeOrNull
import com.luxmap.core.sync.SyncOpHandler
import com.luxmap.core.sync.SyncOpResult
import com.luxmap.feature.survey.data.dao.SurveySessionDao
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

class SubmitSurveySweepSyncHandler
    @Inject
    constructor(
        private val api: SweepsApi,
        private val dao: SurveySessionDao,
    ) : SyncOpHandler {
        override val opType = "submit_survey_sweep"

        override suspend fun handle(
            payloadJson: String,
            onProgress: suspend (Long, Long) -> Unit,
        ): SyncOpResult {
            val payload = Json.decodeFromString<SubmitSurveySweepPayload>(payloadJson)
            val session = dao.sessionById(payload.sessionId) ?: return SyncOpResult.Failed("Local session row missing")
            val sweepId = session.serverSweepId ?: return SyncOpResult.RetryLater
            val gpsHash = session.gpsTrackChecksumSha256 ?: return SyncOpResult.Failed("Missing gps_track checksum")
            val luxHash = session.luxLogChecksumSha256 ?: return SyncOpResult.Failed("Missing lux_log checksum")
            val configHash =
                session.captureConfigChecksumSha256 ?: return SyncOpResult.Failed("Missing capture_config checksum")
            val segments = dao.segmentsFor(payload.sessionId)
            // A session can package with zero video segments (e.g. a crash right after start,
            // before any segment ever closed - see PackageSurveySessionUseCase). Submitting that
            // would compute endedElapsedNs as session.startedAtElapsedNs below, implying a
            // zero-duration sweep that never happened - fail loudly instead of sending that.
            if (segments.isEmpty()) return SyncOpResult.Failed("No video segments to submit")
            val missingClipChecksum = segments.any { it.checksumSha256 == null }
            if (missingClipChecksum) return SyncOpResult.Failed("A video segment has no checksum yet")

            // Generated once, reused on every retry - regenerating this on a retry would break the
            // server's idempotency check on client_op_id (Review Focus item).
            val submitClientOpId = session.submitClientOpId ?: UUID.randomUUID().toString()
            if (session.submitClientOpId == null) {
                dao.updateSession(session.copy(submitClientOpId = submitClientOpId, updatedAt = Instant.now()))
            }

            return try {
                api.submit(
                    sweepId = sweepId,
                    body =
                        SubmitSweepRequestDto(
                            clientOpId = submitClientOpId,
                            // LocalSurveySessionEntity has no endedAtElapsedNs column (only
                            // endedAtUtc: Instant?), so the real elapsed-nanos value at recording
                            // stop is read from the last video segment's endedAtElapsedNs instead,
                            // which IS persisted. segments is never empty here (checked above).
                            endedElapsedNs =
                                segments.maxOf { it.endedAtElapsedNs ?: it.startedAtElapsedNs }.toString(),
                            manifest =
                                SweepManifestDto(
                                    clips =
                                        segments
                                            .sortedBy { it.segmentIndex }
                                            .map {
                                                ClipManifestDto(clipNo = it.segmentIndex, sha256 = it.checksumSha256!!)
                                            },
                                    gpsHash = gpsHash,
                                    luxHash = luxHash,
                                    configHash = configHash,
                                ),
                        ),
                )
                dao.updateSession(session.copy(syncState = "done", updatedAt = Instant.now()))
                SyncOpResult.Done
            } catch (e: HttpException) {
                when (e.code()) {
                    409 -> SyncOpResult.Conflict(e.errorCodeOrNull() ?: "conflict")
                    400, 403, 404, 415 -> SyncOpResult.Failed(e.errorCodeOrNull() ?: "HTTP ${e.code()}")
                    else -> SyncOpResult.RetryLater
                }
            } catch (e: IOException) {
                SyncOpResult.RetryLater
            }
        }
    }
