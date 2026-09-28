package com.luxmap.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.luxmap.feature.survey.data.dao.SurveyPlanDao
import com.luxmap.feature.survey.data.entity.LocalRoadSegmentEntity
import com.luxmap.feature.survey.data.entity.LocalSurveyPlanEntity

// Entities are added here task by task as each feature's Room schema lands —
// keeping the list here is the single place that shows the whole local schema.
@Database(
    entities = [LocalSurveyPlanEntity::class, LocalRoadSegmentEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun surveyPlanDao(): SurveyPlanDao
}
