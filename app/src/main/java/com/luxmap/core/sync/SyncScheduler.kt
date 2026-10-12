package com.luxmap.core.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncScheduler
    @Inject
    constructor(
        @ApplicationContext context: Context,
        private val uploadPreferences: UploadPreferences,
    ) {
        private val workManager = WorkManager.getInstance(context)

        // KEEP by default so an ordinary app launch never disturbs an in-flight periodic job -
        // only a real preference change (reschedulePeriodicSync) needs REPLACE to actually pick
        // up a new NetworkType, since WorkManager ignores a KEEP request against existing work.
        suspend fun schedulePeriodicSync(policy: ExistingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.KEEP) {
            val wifiOnly = uploadPreferences.observeWifiOnlyUpload().first()
            val networkType = if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
            val request =
                PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(networkType).build())
                    .build()
            workManager.enqueueUniquePeriodicWork(PERIODIC_SYNC_WORK_NAME, policy, request)
        }

        // A one-time trigger already enqueued with the old NetworkType (e.g. CONNECTED, waiting
        // out WorkManager's retry backoff) would otherwise keep ExistingWorkPolicy.KEEP from ever
        // letting the next enqueue()'s triggerNow() install the new constraint - it would just sit
        // there and eventually drain the queue over the connection the user just opted out of.
        // Cancelling it here is safe: any op it would have picked up is still in sync_queue, and
        // the next real enqueue() (or the periodic job, at worst) re-triggers it with the right
        // constraint.
        suspend fun reschedulePeriodicSync() {
            workManager.cancelUniqueWork(WorkManagerSyncTrigger.SYNC_WORK_NAME)
            schedulePeriodicSync(policy = ExistingPeriodicWorkPolicy.REPLACE)
        }

        companion object {
            const val PERIODIC_SYNC_WORK_NAME = "sync_queue_periodic"
        }
    }
