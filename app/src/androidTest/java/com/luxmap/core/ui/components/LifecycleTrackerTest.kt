package com.luxmap.core.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.luxmap.core.theme.LuxMapTheme
import org.junit.Rule
import org.junit.Test

class LifecycleTrackerTest {
    @get:Rule val composeRule = createComposeRule()

    private fun steps() =
        listOf(
            LifecycleStep("Mở", LifecycleStepState.DONE),
            LifecycleStep("Đã giao", LifecycleStepState.DONE),
            LifecycleStep("Kiểm tra", LifecycleStepState.CURRENT),
            LifecycleStep("Hoàn tất", LifecycleStepState.FUTURE),
            LifecycleStep("Đã nghiệm thu", LifecycleStepState.FUTURE),
        )

    @Test
    fun shows_all_step_labels_in_order() {
        composeRule.setContent { LuxMapTheme { LifecycleTracker(steps = steps()) } }
        listOf("Mở", "Đã giao", "Kiểm tra", "Hoàn tất", "Đã nghiệm thu").forEach { label ->
            composeRule.onNodeWithText(label).assertExists()
        }
    }

    @Test
    fun a_done_step_shows_the_check_icon_and_a_future_step_does_not() {
        composeRule.setContent { LuxMapTheme { LifecycleTracker(steps = steps()) } }
        composeRule.onNodeWithTag("lifecycle_check_0").assertExists()
        composeRule.onNodeWithTag("lifecycle_check_3").assertDoesNotExist()
    }
}
