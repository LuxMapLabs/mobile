package com.luxmap.core.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.NetworkType
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class WorkManagerSyncTriggerTest {
    @Before
    fun setUp() {
        val config = Configuration.Builder().build()
        WorkManagerTestInitHelper.initializeTestWorkManager(ApplicationProvider.getApplicationContext(), config)
    }

    // Reset in @After, not as the test body's own last statement, so an assertion failure above
    // it still leaves UploadPreferences' real on-disk DataStore clean for the next test (see the
    // same note in UploadPreferencesTest).
    @After
    fun tearDown() =
        runTest {
            UploadPreferences(ApplicationProvider.getApplicationContext()).setWifiOnlyUpload(false)
        }

    @Test
    fun triggerNow_uses_UNMETERED_when_wifi_only_is_on() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val prefs = UploadPreferences(context)
            prefs.setWifiOnlyUpload(true)
            val trigger = WorkManagerSyncTrigger(context, prefs)

            trigger.triggerNow()

            val work =
                WorkManager.getInstance(context)
                    .getWorkInfosForUniqueWork(WorkManagerSyncTrigger.SYNC_WORK_NAME)
                    .get()
                    .first()
            assertEquals(NetworkType.UNMETERED, work.constraints.requiredNetworkType)
        }

    @Test
    fun triggerNow_uses_CONNECTED_when_wifi_only_is_off() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val prefs = UploadPreferences(context)
            val trigger = WorkManagerSyncTrigger(context, prefs)

            trigger.triggerNow()

            val work =
                WorkManager.getInstance(context)
                    .getWorkInfosForUniqueWork(WorkManagerSyncTrigger.SYNC_WORK_NAME)
                    .get()
                    .first()
            assertEquals(NetworkType.CONNECTED, work.constraints.requiredNetworkType)
        }
}
