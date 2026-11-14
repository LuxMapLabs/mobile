package com.luxmap.di

import com.luxmap.feature.workorder.polegps.data.AttachmentDownloadScheduler
import com.luxmap.feature.workorder.polegps.data.FusedGpsFixSource
import com.luxmap.feature.workorder.polegps.data.GpsFixSource
import com.luxmap.feature.workorder.polegps.data.WorkManagerAttachmentDownloadScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class PoleGpsModule {
    @Binds
    abstract fun bindAttachmentDownloadScheduler(
        impl: WorkManagerAttachmentDownloadScheduler,
    ): AttachmentDownloadScheduler

    @Binds
    abstract fun bindGpsFixSource(impl: FusedGpsFixSource): GpsFixSource
}
