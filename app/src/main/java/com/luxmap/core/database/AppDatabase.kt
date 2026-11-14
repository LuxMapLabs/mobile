package com.luxmap.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.luxmap.core.sync.SyncQueueDao
import com.luxmap.core.sync.SyncQueueEntity
import com.luxmap.feature.assetimport.data.dao.AssetImportDao
import com.luxmap.feature.assetimport.data.entity.LocalAssetImportPoleEntity
import com.luxmap.feature.assetimport.data.entity.LocalAssetImportTripEntity
import com.luxmap.feature.survey.data.dao.PoleListCacheDao
import com.luxmap.feature.survey.data.dao.PolePhotoDao
import com.luxmap.feature.survey.data.dao.SurveyPlanDao
import com.luxmap.feature.survey.data.dao.SurveyRouteCacheDao
import com.luxmap.feature.survey.data.dao.SurveySessionDao
import com.luxmap.feature.survey.data.entity.LocalAssignedSurveyRouteEntity
import com.luxmap.feature.survey.data.entity.LocalPoleListItemEntity
import com.luxmap.feature.survey.data.entity.LocalPolePhotoEntity
import com.luxmap.feature.survey.data.entity.LocalRoadSegmentEntity
import com.luxmap.feature.survey.data.entity.LocalSurveyPlanEntity
import com.luxmap.feature.survey.data.entity.LocalSurveySessionEntity
import com.luxmap.feature.survey.data.entity.LocalSurveyVideoSegmentEntity
import com.luxmap.feature.workorder.data.dao.WorkOrderCompletionDao
import com.luxmap.feature.workorder.data.entity.LocalWorkOrderCompletionEntity
import com.luxmap.feature.workorder.data.entity.LocalWorkOrderEvidenceEntity
import com.luxmap.feature.workorder.polegps.data.LocalPoleGpsMeasurementEntity
import com.luxmap.feature.workorder.polegps.data.LocalPoleGpsOrderEntity
import com.luxmap.feature.workorder.polegps.data.LocalWorkOrderAttachmentEntity
import com.luxmap.feature.workorder.polegps.data.PoleGpsDao

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
        LocalPolePhotoEntity::class,
        LocalPoleListItemEntity::class,
        LocalAssignedSurveyRouteEntity::class,
        LocalAssetImportTripEntity::class,
        LocalAssetImportPoleEntity::class,
        LocalPoleGpsOrderEntity::class,
        LocalWorkOrderAttachmentEntity::class,
        LocalPoleGpsMeasurementEntity::class,
    ],
    version = 18,
    exportSchema = false,
)
@TypeConverters(InstantConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun surveyPlanDao(): SurveyPlanDao

    abstract fun surveySessionDao(): SurveySessionDao

    abstract fun syncQueueDao(): SyncQueueDao

    abstract fun workOrderCompletionDao(): WorkOrderCompletionDao

    abstract fun polePhotoDao(): PolePhotoDao

    abstract fun poleListCacheDao(): PoleListCacheDao

    abstract fun surveyRouteCacheDao(): SurveyRouteCacheDao

    abstract fun assetImportDao(): AssetImportDao

    abstract fun poleGpsDao(): PoleGpsDao
}
