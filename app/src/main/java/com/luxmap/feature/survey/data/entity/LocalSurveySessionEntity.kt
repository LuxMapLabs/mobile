package com.luxmap.feature.survey.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

// recordingState tracks capture lifecycle (recording/stopped/packaged); syncState is null until
// packaged, then follows P6.1 exactly (queued/syncing/conflict/failed/done) — the two are kept
// separate per review feedback so recording progress is never confused with upload progress.
// No road_segment_id: one session covers a whole survey_sweep, which can span many segments.
@Entity(tableName = "local_survey_session")
data class LocalSurveySessionEntity(
    @PrimaryKey val sessionId: String,
    val surveySweepId: String,
    // The work order (taskKind == "survey") this session belongs to - required because the real
    // upload flow needs it for POST /work-orders/{id}/start and POST /api/v1/sweeps. Added fm-40.
    val workOrderId: String,
    val recordingState: String,
    val syncState: String?,
    val startedAtUtc: Instant,
    val startedAtElapsedNs: Long,
    val endedAtUtc: Instant?,
    val durationSeconds: Long?,
    val distanceMeters: Double?,
    val gpsTrackFilePath: String?,
    val luxLogFilePath: String?,
    val frameTimestampLogFilePath: String?,
    val captureConfigFilePath: String?,
    val manifestFilePath: String?,
    val packageSchemaVersion: String,
    val timestampSourceRealtime: Boolean,
    val bleGapDetected: Boolean,
    // Null until "create_survey_sweep" succeeds; the real sweep_id the server assigned.
    val serverSweepId: String? = null,
    // Generated once on the first submit attempt, reused on every retry - regenerating it would
    // break the server's idempotency check on client_op_id (fm-40).
    val submitClientOpId: String? = null,
    val gpsTrackChecksumSha256: String? = null,
    val luxLogChecksumSha256: String? = null,
    val captureConfigChecksumSha256: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
)
