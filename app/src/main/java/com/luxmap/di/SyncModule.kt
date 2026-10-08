package com.luxmap.di

import com.luxmap.core.sync.SyncOpHandler
import com.luxmap.core.sync.SyncTrigger
import com.luxmap.core.sync.WorkManagerSyncTrigger
import com.luxmap.feature.survey.data.sync.CreateSurveySweepSyncHandler
import com.luxmap.feature.workorder.data.sync.CompleteWorkOrderSyncHandler
import com.luxmap.feature.workorder.data.sync.UploadWorkOrderEvidenceSyncHandler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dagger.multibindings.Multibinds

@Module
@InstallIn(SingletonComponent::class)
abstract class SyncModule {
    @Binds
    abstract fun bindSyncTrigger(impl: WorkManagerSyncTrigger): SyncTrigger

    @Multibinds
    abstract fun bindSyncOpHandlers(): Set<SyncOpHandler>

    @Binds
    @IntoSet
    abstract fun bindUploadWorkOrderEvidenceSyncHandler(impl: UploadWorkOrderEvidenceSyncHandler): SyncOpHandler

    @Binds
    @IntoSet
    abstract fun bindCompleteWorkOrderSyncHandler(impl: CompleteWorkOrderSyncHandler): SyncOpHandler

    @Binds
    @IntoSet
    abstract fun bindCreateSurveySweepSyncHandler(impl: CreateSurveySweepSyncHandler): SyncOpHandler
}
