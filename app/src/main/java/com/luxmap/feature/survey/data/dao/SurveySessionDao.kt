package com.luxmap.feature.survey.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.luxmap.feature.survey.data.entity.LocalSurveySessionEntity
import com.luxmap.feature.survey.data.entity.LocalSurveyVideoSegmentEntity

@Dao
interface SurveySessionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSession(session: LocalSurveySessionEntity)

    @Update
    suspend fun updateSession(session: LocalSurveySessionEntity)

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
}
