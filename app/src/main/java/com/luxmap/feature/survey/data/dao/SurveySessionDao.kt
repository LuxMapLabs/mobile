package com.luxmap.feature.survey.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.luxmap.feature.survey.data.entity.LocalSurveySessionEntity
import com.luxmap.feature.survey.data.entity.LocalSurveyVideoSegmentEntity
import java.time.Instant

@Dao
interface SurveySessionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSession(session: LocalSurveySessionEntity)

    @Update
    suspend fun updateSession(session: LocalSurveySessionEntity)

    // Atomic conditional UPDATE, not a read-then-write from the caller - two callers racing to
    // upload the same session can both read syncState == null before either writes, but only
    // one of two concurrent calls to this single SQL statement can match the WHERE clause.
    @Query(
        "UPDATE local_survey_session SET syncState = 'queued', updatedAt = :updatedAt " +
            "WHERE sessionId = :sessionId AND syncState IS NULL",
    )
    suspend fun claimForUpload(
        sessionId: String,
        updatedAt: Instant,
    ): Int

    @Query("SELECT * FROM local_survey_session WHERE sessionId = :sessionId")
    suspend fun sessionById(sessionId: String): LocalSurveySessionEntity?

    @Query("SELECT * FROM local_survey_session WHERE recordingState = 'recording'")
    suspend fun sessionsInRecordingState(): List<LocalSurveySessionEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSegment(segment: LocalSurveyVideoSegmentEntity)

    @Update
    suspend fun updateSegment(segment: LocalSurveyVideoSegmentEntity)

    @Query("SELECT * FROM local_survey_video_segment WHERE sessionId = :sessionId ORDER BY segmentIndex ASC")
    suspend fun segmentsFor(sessionId: String): List<LocalSurveyVideoSegmentEntity>

    @Query("SELECT * FROM local_survey_video_segment WHERE sessionId = :sessionId AND endedAtElapsedNs IS NULL LIMIT 1")
    suspend fun unfinalizedSegmentFor(sessionId: String): LocalSurveyVideoSegmentEntity?

    @Query("DELETE FROM local_survey_video_segment WHERE segmentId = :segmentId")
    suspend fun deleteSegment(segmentId: String)

    @Query("DELETE FROM local_survey_video_segment WHERE sessionId = :sessionId")
    suspend fun deleteSegmentsForSession(sessionId: String)

    @Query("DELETE FROM local_survey_session WHERE sessionId = :sessionId")
    suspend fun deleteSession(sessionId: String)

    // Wraps both deletes in one transaction so a session row never outlives its segments or
    // the other way around, even if the process dies mid-call.
    @Transaction
    suspend fun deleteSessionAndSegments(sessionId: String) {
        deleteSegmentsForSession(sessionId)
        deleteSession(sessionId)
    }
}
