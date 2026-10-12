package com.luxmap.core.sync

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// UploadPreferences is backed by a real on-disk DataStore shared by every instance in this test
// process (`preferencesDataStore` is a top-level delegate), so a value left over from one test
// leaks into the next - reset in @After, not as the test body's own last statement, so an
// assertion failure above it still leaves the file clean for the next test.
class UploadPreferencesTest {
    @After
    fun tearDown() =
        runTest {
            UploadPreferences(ApplicationProvider.getApplicationContext()).setWifiOnlyUpload(false)
        }

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
        }
}
