package com.luxmap.core.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.luxmap.core.theme.LuxMapTheme
import org.junit.Rule
import org.junit.Test

class LifecycleTrackerTest {
    @get:Rule val composeRule = createComposeRule()

    private fun steps(currentDone: Boolean = false) =
        listOf(
            LifecycleStep("Mở", LifecycleStepState.DONE),
            LifecycleStep("Đã giao", LifecycleStepState.DONE),
            LifecycleStep("Kiểm tra", if (currentDone) LifecycleStepState.DONE else LifecycleStepState.CURRENT),
            LifecycleStep("Hoàn tất", LifecycleStepState.FUTURE),
            LifecycleStep("Đã nghiệm thu", LifecycleStepState.FUTURE),
        )

    @Test
    fun shows_the_current_step_as_a_step_count_and_label() {
        composeRule.setContent { LuxMapTheme { LifecycleTracker(steps = steps()) } }
        composeRule.onNodeWithText("Quy trình").assertExists()
        composeRule.onNodeWithText("Bước 3/5 · Kiểm tra").assertExists()
    }

    @Test
    fun shows_awaiting_review_once_the_current_step_is_done() {
        composeRule.setContent { LuxMapTheme { LifecycleTracker(steps = steps(currentDone = true)) } }
        composeRule.onNodeWithText("Chờ duyệt · Kiểm tra").assertExists()
    }
}
