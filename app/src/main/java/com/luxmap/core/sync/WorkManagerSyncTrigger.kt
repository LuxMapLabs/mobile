package com.luxmap.core.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkManagerSyncTrigger
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : SyncTrigger {
        private val workManager = WorkManager.getInstance(context)

        override fun triggerNow() {
            val request =
                OneTimeWorkRequestBuilder<SyncWorker>()
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build()
            workManager.enqueueUniqueWork(SYNC_WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }

        companion object {
            const val SYNC_WORK_NAME = "sync_queue"
        }
    }
