package com.luxmap.di

import android.content.Context
import androidx.room.Room
import com.luxmap.core.database.AppDatabase
import com.luxmap.core.database.MIGRATION_16_17
import com.luxmap.core.database.MIGRATION_17_18
import com.luxmap.core.database.MIGRATION_18_19
import com.luxmap.core.database.MIGRATION_21_22
import com.luxmap.core.database.MIGRATION_22_23
import com.luxmap.core.database.MIGRATION_23_24
import com.luxmap.core.database.MIGRATION_24_25
import com.luxmap.core.database.MIGRATION_25_26
import com.luxmap.core.database.MIGRATION_26_27
import com.luxmap.core.database.MIGRATION_27_28
import com.luxmap.core.database.MIGRATION_28_29
import com.luxmap.core.database.MIGRATION_29_30
import com.luxmap.core.database.MIGRATION_30_31
import com.luxmap.core.database.MIGRATION_31_32
import com.luxmap.core.database.MIGRATION_32_33
import com.luxmap.core.offline.OfflineSnapshotDao
import com.luxmap.core.sync.SyncQueueDao
import com.luxmap.feature.history.data.dao.HistoryCacheDao
import com.luxmap.feature.survey.data.dao.LuxReadingDao
import com.luxmap.feature.survey.data.dao.PoleListCacheDao
import com.luxmap.feature.survey.data.dao.PolePhotoDao
import com.luxmap.feature.workorder.data.dao.WorkOrderCompletionDao
import com.luxmap.feature.workorder.data.dao.WorkOrderDraftDao
import com.luxmap.feature.workorder.polegps.data.PoleGpsDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
    ): AppDatabase =
        Room
            .databaseBuilder(context, AppDatabase::class.java, "luxmap.db")
            // Preserve local drafts and the offline upload queue; missing migrations fail closed.
            .addMigrations(
                MIGRATION_16_17,
                MIGRATION_17_18,
                MIGRATION_18_19,
                MIGRATION_21_22,
                MIGRATION_22_23,
                MIGRATION_23_24,
                MIGRATION_24_25,
                MIGRATION_25_26,
                MIGRATION_26_27,
                MIGRATION_27_28,
                MIGRATION_28_29,
                MIGRATION_29_30,
                MIGRATION_30_31,
                MIGRATION_31_32,
                MIGRATION_32_33,
            )
            .build()

    @Provides
    @Singleton
    fun provideSyncQueueDao(db: AppDatabase): SyncQueueDao = db.syncQueueDao()

    @Provides
    @Singleton
    fun provideWorkOrderDraftDao(db: AppDatabase): WorkOrderDraftDao = db.workOrderDraftDao()

    @Provides
    @Singleton
    fun provideWorkOrderCompletionDao(db: AppDatabase): WorkOrderCompletionDao = db.workOrderCompletionDao()

    @Provides
    @Singleton
    fun providePolePhotoDao(db: AppDatabase): PolePhotoDao = db.polePhotoDao()

    @Provides
    @Singleton
    fun providePoleListCacheDao(db: AppDatabase): PoleListCacheDao = db.poleListCacheDao()

    @Provides
    @Singleton
    fun providePoleGpsDao(db: AppDatabase): PoleGpsDao = db.poleGpsDao()

    @Provides
    @Singleton
    fun provideLuxReadingDao(db: AppDatabase): LuxReadingDao = db.luxReadingDao()

    @Provides
    @Singleton
    fun provideHistoryCacheDao(db: AppDatabase): HistoryCacheDao = db.historyCacheDao()

    @Provides
    @Singleton
    fun provideOfflineSnapshotDao(db: AppDatabase): OfflineSnapshotDao = db.offlineSnapshotDao()
}
