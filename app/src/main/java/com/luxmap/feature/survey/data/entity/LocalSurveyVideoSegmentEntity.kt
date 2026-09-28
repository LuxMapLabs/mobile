package com.luxmap.feature.survey.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

// endedAtElapsedNs == null means this segment is still open — used by both crash recovery
// (Task 16) and the packaging use case (Task 15) to find a segment that never closed.
@Entity(tableName = "local_survey_video_segment")
data class LocalSurveyVideoSegmentEntity(
    @PrimaryKey val segmentId: String,
    val sessionId: String,
    val segmentIndex: Int,
    val filePath: String,
    val startedAtElapsedNs: Long,
    val endedAtElapsedNs: Long?,
    val sizeBytes: Long?,
    val checksumSha256: String?,
)
