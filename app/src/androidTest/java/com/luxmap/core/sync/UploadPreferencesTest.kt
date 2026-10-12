package com.luxmap.core.sync

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UploadPreferencesTest {
    @Test
    fun defaults_to_wifi_only_off() =
        runTest {
            val prefs = UploadPreferences(ApplicationProvider.getApplicationContext())
            assertEquals(false, prefs.observeWifiOnlyUpload().first())
        }

    @Test
    fun setWifiOnlyUpload_persists_the_value() =
        runTest {
            val prefs = UploadPreferences(ApplicationProvider.getApplicationContext())
            prefs.setWifiOnlyUpload(true)
            assertTrue(prefs.observeWifiOnlyUpload().first())
            prefs.setWifiOnlyUpload(false)
        }
}
