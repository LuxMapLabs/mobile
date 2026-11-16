package com.luxmap.core.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.luxmap.core.theme.LuxMapTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LuxSelectionTest {
    @get:Rule val composeRule = createComposeRule()

    private val outcomes =
        listOf(
            LuxSegment("present", "Có sự cố"),
            LuxSegment("absent", "Không có"),
            LuxSegment("unknown", "Chưa rõ"),
        )

    @Test
    fun a_segmented_control_reports_selection_as_radio_buttons() {
        var picked: String? = null
        composeRule.setContent {
            LuxMapTheme {
                var selected by remember { mutableStateOf<String?>("present") }
                LuxSegmented(
                    segments = outcomes,
                    selected = selected,
                    onSelect = {
                        selected = it
                        picked = it
                    },
                )
            }
        }
        composeRule
            .onNodeWithText("Có sự cố")
            .assertIsSelected()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        composeRule.onNodeWithText("Không có").assertIsNotSelected().assertTouchTargetIsAtLeast(48.dp).performClick()
        assertEquals("absent", picked)
        composeRule.onNodeWithText("Không có").assertIsSelected()
        composeRule.onNodeWithText("Có sự cố").assertIsNotSelected()
    }

    @Test
    fun a_filter_chip_toggles_and_reads_its_count() {
        composeRule.setContent {
            LuxMapTheme {
                var selected by remember { mutableStateOf(false) }
                LuxFilterChip(label = "Chưa đọc", count = 3, selected = selected, onClick = { selected = !selected })
            }
        }
        composeRule
            .onNodeWithContentDescription("Chưa đọc, 3")
            .assertIsNotSelected()
            .assertTouchTargetIsAtLeast(48.dp)
            .performClick()
        composeRule.onNodeWithContentDescription("Chưa đọc, 3").assertIsSelected()
    }
}
