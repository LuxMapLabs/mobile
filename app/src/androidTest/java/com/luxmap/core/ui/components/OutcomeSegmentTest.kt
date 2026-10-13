package com.luxmap.core.ui.components

import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.luxmap.core.theme.LuxMapTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class OutcomeSegmentTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun shows_all_three_options() {
        composeRule.setContent { LuxMapTheme { OutcomeSegment(selected = null, onSelected = {}) } }
        composeRule.onNodeWithText("Có sự cố").assertExists()
        composeRule.onNodeWithText("Không có").assertExists()
        composeRule.onNodeWithText("Chưa xác định").assertExists()
    }

    @Test
    fun tapping_an_option_invokes_onSelected_with_the_wire_value() {
        var picked: String? = null
        composeRule.setContent { LuxMapTheme { OutcomeSegment(selected = null, onSelected = { picked = it }) } }
        composeRule.onNodeWithText("Không có").performClick()
        assertEquals("fault_absent", picked)
    }

    @Test
    fun the_selected_option_is_marked_selected_and_the_others_are_not() {
        composeRule.setContent { LuxMapTheme { OutcomeSegment(selected = "fault_absent", onSelected = {}) } }
        composeRule.onNodeWithText("Không có").assertIsSelected()
        composeRule.onNodeWithText("Có sự cố").assertIsNotSelected()
        composeRule.onNodeWithText("Chưa xác định").assertIsNotSelected()
    }
}
