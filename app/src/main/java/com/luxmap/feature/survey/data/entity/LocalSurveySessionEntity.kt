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
    val recordingState: String,
    val syncState: String?,
    val startedAtUtc: Instant,
    val startedAtElapsedNs: Long,
    val endedAtUtc: Instant?,
    val durationSeconds: Long?,
    val distanceMeters: Double?,
    val gpsTrackFilePath: String?,
    val luxLogFilePath: String?,
    val headingLogFilePath: String?,
    val frameTimestampLogFilePath: String?,
    val captureConfigFilePath: String?,
    val manifestFilePath: String?,
    val packageSchemaVersion: String,
    val timestampSourceRealtime: Boolean,
    val bleGapDetected: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
)
