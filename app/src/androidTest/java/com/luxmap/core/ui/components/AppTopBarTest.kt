package com.luxmap.core.ui.components

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.luxmap.core.theme.LuxMapTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AppTopBarTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun shows_the_title_and_invokes_onBack_when_the_back_button_is_tapped() {
        var backTapped = false
        composeRule.setContent {
            LuxMapTheme { AppTopBar(title = "Lệnh kiểm tra", onBack = { backTapped = true }) }
        }
        composeRule.onNodeWithText("Lệnh kiểm tra").assertExists()
        composeRule.onNodeWithContentDescription("Quay lại").performClick()
        assertTrue(backTapped)
    }

    @Test
    fun shows_the_subtitle_when_provided() {
        composeRule.setContent {
            LuxMapTheme { AppTopBar(title = "Lệnh kiểm tra", subtitle = "WO-1042", onBack = {}) }
        }
        composeRule.onNodeWithText("WO-1042").assertExists()
    }

    @Test
    fun the_back_button_meets_the_minimum_touch_target() {
        composeRule.setContent {
            LuxMapTheme { AppTopBar(title = "Lệnh kiểm tra", onBack = {}) }
        }
        composeRule
            .onNodeWithContentDescription("Quay lại")
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
    }
}
