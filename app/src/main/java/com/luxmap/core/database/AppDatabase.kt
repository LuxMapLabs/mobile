package com.luxmap.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.luxmap.core.sync.SyncQueueDao
import com.luxmap.core.sync.SyncQueueEntity
import com.luxmap.feature.survey.data.dao.SurveyPlanDao
import com.luxmap.feature.survey.data.dao.SurveySessionDao
import com.luxmap.feature.survey.data.entity.LocalRoadSegmentEntity
import com.luxmap.feature.survey.data.entity.LocalSurveyPlanEntity
import com.luxmap.feature.survey.data.entity.LocalSurveySessionEntity
import com.luxmap.feature.survey.data.entity.LocalSurveyVideoSegmentEntity
import com.luxmap.feature.workorder.data.dao.WorkOrderCompletionDao
import com.luxmap.feature.workorder.data.entity.LocalWorkOrderCompletionEntity
import com.luxmap.feature.workorder.data.entity.LocalWorkOrderEvidenceEntity

// Entities are added here task by task as each feature's Room schema lands —
// keeping the list here is the single place that shows the whole local schema.
@Database(
    entities = [
        LocalSurveyPlanEntity::class,
        LocalRoadSegmentEntity::class,
        LocalSurveySessionEntity::class,
        LocalSurveyVideoSegmentEntity::class,
        SyncQueueEntity::class,
        LocalWorkOrderEvidenceEntity::class,
        LocalWorkOrderCompletionEntity::class,
    ],
    version = 5,
    exportSchema = false,
)
@TypeConverters(InstantConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun surveyPlanDao(): SurveyPlanDao

    abstract fun surveySessionDao(): SurveySessionDao

    abstract fun syncQueueDao(): SyncQueueDao

    abstract fun workOrderCompletionDao(): WorkOrderCompletionDao
}
