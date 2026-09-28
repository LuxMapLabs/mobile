package com.luxmap.feature.survey.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.luxmap.feature.survey.data.entity.LocalRoadSegmentEntity
import com.luxmap.feature.survey.data.entity.LocalSurveyPlanEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SurveyPlanDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlans(plans: List<LocalSurveyPlanEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRoadSegments(segments: List<LocalRoadSegmentEntity>)

    @Query("SELECT * FROM local_survey_plan")
    fun observePlans(): Flow<List<LocalSurveyPlanEntity>>

    @Query("SELECT * FROM local_road_segment WHERE surveySweepId = :surveySweepId")
    suspend fun roadSegmentsFor(surveySweepId: String): List<LocalRoadSegmentEntity>
}
