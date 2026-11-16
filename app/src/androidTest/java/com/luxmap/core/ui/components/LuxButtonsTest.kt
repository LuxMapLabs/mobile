package com.luxmap.core.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.luxmap.core.theme.LuxMapTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LuxButtonsTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun every_button_variant_meets_the_minimum_touch_height_and_is_clickable() {
        composeRule.setContent {
            LuxMapTheme {
                Column {
                    PrimaryButton(text = "Chính", onClick = {})
                    LuxSecondaryButton(text = "Phụ", onClick = {})
                    LuxTextButton(text = "Chữ", onClick = {})
                    LuxDestructiveButton(text = "Huỷ đặc", onClick = {})
                    LuxDestructiveButton(text = "Huỷ viền", onClick = {}, filled = false)
                }
            }
        }
        listOf("Chính", "Phụ", "Chữ", "Huỷ đặc", "Huỷ viền").forEach { label ->
            composeRule
                .onNodeWithText(label)
                .assertHasClickAction()
                .assertIsEnabled()
                .assertHeightIsAtLeast(48.dp)
        }
    }

    @Test
    fun a_disabled_secondary_button_is_not_enabled_and_does_not_fire() {
        var taps = 0
        composeRule.setContent {
            LuxMapTheme { LuxSecondaryButton(text = "Chỉ đường", onClick = { taps++ }, enabled = false) }
        }
        composeRule.onNodeWithText("Chỉ đường").assertIsNotEnabled().performClick()
        assertEquals(0, taps)
    }

    @Test
    fun a_loading_primary_button_ignores_taps_and_announces_it_is_busy() {
        var taps = 0
        composeRule.setContent {
            LuxMapTheme { PrimaryButton(text = "Nộp báo cáo", onClick = { taps++ }, isLoading = true) }
        }
        composeRule
            .onNode(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Đang xử lý"))
            .assertExists()
            .performClick()
        assertEquals(0, taps)
    }

    @Test
    fun an_enabled_destructive_button_fires_its_action() {
        var tapped = false
        composeRule.setContent {
            LuxMapTheme { LuxDestructiveButton(text = "Đăng xuất", onClick = { tapped = true }) }
        }
        composeRule.onNodeWithText("Đăng xuất").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Role))
        composeRule.onNodeWithText("Đăng xuất").performClick()
        assertTrue(tapped)
    }
}
