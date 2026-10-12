package com.luxmap.core.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class SyncSchedulerTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().build())
    }

    @After
    fun tearDown() =
        runTest {
            UploadPreferences(context).setWifiOnlyUpload(false)
        }

    @Test
    fun schedulePeriodicSync_default_KEEP_does_not_replace_an_already_scheduled_job() =
        runTest {
            val prefs = UploadPreferences(context)
            val scheduler = SyncScheduler(context, prefs)
            scheduler.schedulePeriodicSync() // wifi-only off -> CONNECTED
            prefs.setWifiOnlyUpload(true)
            scheduler.schedulePeriodicSync() // KEEP must not touch the already-scheduled job

            val work =
                WorkManager.getInstance(context)
                    .getWorkInfosForUniqueWork(SyncScheduler.PERIODIC_SYNC_WORK_NAME)
                    .get()
                    .first()
            assertEquals(NetworkType.CONNECTED, work.constraints.requiredNetworkType)
        }

    @Test
    fun reschedulePeriodicSync_replaces_the_periodic_job_with_the_new_constraint() =
        runTest {
            val prefs = UploadPreferences(context)
            val scheduler = SyncScheduler(context, prefs)
            scheduler.schedulePeriodicSync() // wifi-only off -> CONNECTED
            prefs.setWifiOnlyUpload(true)
            scheduler.reschedulePeriodicSync() // REPLACE must pick up UNMETERED

            val work =
                WorkManager.getInstance(context)
                    .getWorkInfosForUniqueWork(SyncScheduler.PERIODIC_SYNC_WORK_NAME)
                    .get()
                    .first()
            assertEquals(NetworkType.UNMETERED, work.constraints.requiredNetworkType)
        }

    @Test
    fun reschedulePeriodicSync_cancels_a_pending_one_time_sync_left_with_a_stale_constraint() =
        runTest {
            val prefs = UploadPreferences(context)
            val scheduler = SyncScheduler(context, prefs)
            // Simulates a one-time trigger enqueued before the toggle flipped, still sitting in
            // WorkManager's unique-work slot under ExistingWorkPolicy.KEEP from
            // WorkManagerSyncTrigger - nothing but an explicit cancel can replace it.
            WorkManager.getInstance(context).enqueueUniqueWork(
                WorkManagerSyncTrigger.SYNC_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<SyncWorker>().build(),
            )

            scheduler.reschedulePeriodicSync()

            val work =
                WorkManager.getInstance(context)
                    .getWorkInfosForUniqueWork(WorkManagerSyncTrigger.SYNC_WORK_NAME)
                    .get()
                    .first()
            assertEquals(WorkInfo.State.CANCELLED, work.state)
        }
}
