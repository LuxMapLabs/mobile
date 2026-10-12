package com.luxmap.feature.survey.ui.capture

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.luxmap.core.theme.LuxMapTheme
import org.junit.Rule
import org.junit.Test

class CaptureScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun hud_row_shows_the_lux_value_when_present() {
        composeRule.setContent {
            LuxMapTheme {
                CaptureHudRow(gpsAccuracyMeters = 5f, latestLuxValue = 12.3f, freeStorageBytes = 500_000_000L)
            }
        }
        composeRule.onNodeWithText("12.3 lux").assertExists()
    }

    @Test
    fun hud_row_does_not_crash_with_every_value_null() {
        composeRule.setContent {
            LuxMapTheme { CaptureHudRow(gpsAccuracyMeters = null, latestLuxValue = null, freeStorageBytes = null) }
        }
        composeRule.onNodeWithText("Chưa có dữ liệu lux").assertExists()
    }
}
