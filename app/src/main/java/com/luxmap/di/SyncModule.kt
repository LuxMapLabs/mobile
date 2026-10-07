package com.luxmap.di

import com.luxmap.core.sync.SyncOpHandler
import com.luxmap.core.sync.SyncTrigger
import com.luxmap.core.sync.WorkManagerSyncTrigger
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds

@Module
@InstallIn(SingletonComponent::class)
abstract class SyncModule {
    @Binds
    abstract fun bindSyncTrigger(impl: WorkManagerSyncTrigger): SyncTrigger

    @Multibinds
    abstract fun bindSyncOpHandlers(): Set<SyncOpHandler>
}
