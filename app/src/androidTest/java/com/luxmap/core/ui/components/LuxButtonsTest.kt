package com.luxmap.core.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.ui.unit.Density
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
    fun paired_buttons_match_in_width_and_height() {
        showActionPair(fontScale = 1f)
        val secondary = composeRule.onNodeWithText("Kiểm tra lại").fetchSemanticsNode().boundsInRoot
        val primary = composeRule.onNodeWithText("Nộp báo cáo").fetchSemanticsNode().boundsInRoot
        assertEquals(secondary.top, primary.top, 1f)
        assertEquals(secondary.height, primary.height, 1f)
        assertEquals(secondary.width, primary.width, 1f)
        assertTrue(secondary.right < primary.left)
    }

    @Test
    fun paired_buttons_stack_at_large_font() {
        showActionPair(fontScale = 2f)
        val secondary = composeRule.onNodeWithText("Kiểm tra lại").fetchSemanticsNode().boundsInRoot
        val primary = composeRule.onNodeWithText("Nộp báo cáo").fetchSemanticsNode().boundsInRoot
        assertEquals(secondary.left, primary.left, 1f)
        assertEquals(secondary.width, primary.width, 1f)
        assertTrue(secondary.bottom < primary.top)
    }

    private fun showActionPair(fontScale: Float) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                LuxMapTheme {
                    LuxActionPair(
                        modifier = Modifier.width(320.dp),
                        secondary = { LuxSecondaryButton(text = "Kiểm tra lại", onClick = {}, modifier = it) },
                        primary = { PrimaryButton(text = "Nộp báo cáo", onClick = {}, modifier = it) },
                    )
                }
            }
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
