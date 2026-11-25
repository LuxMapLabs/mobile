package com.luxmap.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.luxmap.core.sync.SyncQueueDao
import com.luxmap.core.sync.SyncQueueEntity
import com.luxmap.feature.survey.data.dao.LuxReadingDao
import com.luxmap.feature.survey.data.dao.PoleGpsOrderCacheDao
import com.luxmap.feature.survey.data.dao.PoleListCacheDao
import com.luxmap.feature.survey.data.dao.PolePhotoDao
import com.luxmap.feature.survey.data.dao.SurveyRouteCacheDao
import com.luxmap.feature.survey.data.entity.LocalAssignedPoleGpsOrderEntity
import com.luxmap.feature.survey.data.entity.LocalAssignedSurveyRouteEntity
import com.luxmap.feature.survey.data.entity.LocalLuxReadingEntity
import com.luxmap.feature.survey.data.entity.LocalPoleListItemEntity
import com.luxmap.feature.survey.data.entity.LocalPolePhotoEntity
import com.luxmap.feature.workorder.data.dao.WorkOrderCompletionDao
import com.luxmap.feature.workorder.data.dao.WorkOrderDraftDao
import com.luxmap.feature.workorder.data.entity.LocalWorkOrderCompletionEntity
import com.luxmap.feature.workorder.data.entity.LocalWorkOrderDraftEntity
import com.luxmap.feature.workorder.data.entity.LocalWorkOrderEvidenceEntity
import com.luxmap.feature.workorder.polegps.data.LocalPoleGpsMeasurementEntity
import com.luxmap.feature.workorder.polegps.data.LocalPoleGpsOrderEntity
import com.luxmap.feature.workorder.polegps.data.LocalPoleGpsRouteEntity
import com.luxmap.feature.workorder.polegps.data.LocalWorkOrderAttachmentEntity
import com.luxmap.feature.workorder.polegps.data.PoleGpsDao

// Entities are added here task by task as each feature's Room schema lands —
// keeping the list here is the single place that shows the whole local schema.
@Database(
    entities = [
        SyncQueueEntity::class,
        LocalWorkOrderDraftEntity::class,
        LocalAssignedPoleGpsOrderEntity::class,
        LocalWorkOrderEvidenceEntity::class,
        LocalWorkOrderCompletionEntity::class,
        LocalPolePhotoEntity::class,
        LocalPoleListItemEntity::class,
        LocalAssignedSurveyRouteEntity::class,
        LocalPoleGpsOrderEntity::class,
        LocalWorkOrderAttachmentEntity::class,
        LocalPoleGpsMeasurementEntity::class,
        LocalPoleGpsRouteEntity::class,
        LocalLuxReadingEntity::class,
    ],
    version = 28,
    exportSchema = false,
)
@TypeConverters(InstantConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun workOrderDraftDao(): WorkOrderDraftDao

    abstract fun syncQueueDao(): SyncQueueDao

    abstract fun workOrderCompletionDao(): WorkOrderCompletionDao

    abstract fun polePhotoDao(): PolePhotoDao

    abstract fun poleListCacheDao(): PoleListCacheDao

    abstract fun surveyRouteCacheDao(): SurveyRouteCacheDao

    abstract fun poleGpsOrderCacheDao(): PoleGpsOrderCacheDao

    abstract fun poleGpsDao(): PoleGpsDao

    abstract fun luxReadingDao(): LuxReadingDao
}
