package com.luxmap.feature.home.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.luxmap.core.theme.LuxMapTheme
import com.luxmap.core.ui.components.assertTouchTargetIsAtLeast
import com.luxmap.feature.home.data.HomeData
import com.luxmap.feature.home.data.HomeMetrics
import com.luxmap.feature.home.data.WorkOrderCluster
import com.luxmap.feature.home.data.WorkOrderSummaryItem
import com.luxmap.feature.workorder.data.CompletionSyncState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomeLargeFontTest {
    @get:Rule val composeRule = createComposeRule()

    private fun show(
        username: String = "kysu1",
        fontScale: Float = 1.5f,
    ) {
        val item =
            WorkOrderSummaryItem(
                workOrderId = "WO-1042",
                woStatus = "assigned",
                dueDate = "2020-01-01",
                priorityScore = null,
                taskKind = "repair",
                communeId = "COM-001",
                title = "Sửa đèn tuyến đường liên xã Phước Hiệp đoạn qua chợ",
                scheduledDate = "2020-01-01",
                faultCount = 3,
            )
        val data =
            HomeData(
                metrics = HomeMetrics(1, 0, 1, null),
                clusters = listOf(WorkOrderCluster("COM-001", listOf(item))),
            )
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                LuxMapTheme {
                    Box(modifier = Modifier.width(360.dp)) {
                        HomeScreen(
                            uiState =
                                HomeUiState.Success(
                                    data = data,
                                    completionSyncStates = mapOf("WO-1042" to CompletionSyncState.PENDING),
                                ),
                            username = username,
                            onOpenWorkOrder = {},
                            onRetry = {},
                            unreadNotificationCount = 0,
                            onOpenNotifications = {},
                        )
                    }
                }
            }
        }
    }

    @Test
    fun selector_and_card_keep_48dp_targets_at_large_font_on_a_narrow_screen() {
        show()
        composeRule.onNodeWithText("Sửa chữa · 1").assertTouchTargetIsAtLeast(48.dp)
        composeRule.onNode(hasClickAction() and hasText("WO-1042", substring = true)).assertTouchTargetIsAtLeast(48.dp)
    }

    @Test
    fun the_top_bar_greeting_is_not_clipped_or_overlapping_the_content_at_large_font() {
        val email = "long.field.operator@example.com"
        show(username = email, fontScale = 2f)
        val greetingNode = composeRule.onNodeWithText("Xin chào, $email", substring = true, useUnmergedTree = true)
        val layouts = mutableListOf<TextLayoutResult>()
        greetingNode.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        assertTrue("greeting should wrap", layout.lineCount > 1)
        assertTrue("greeting text exceeds its layout", !layout.didOverflowHeight)
        val greeting = greetingNode.fetchSemanticsNode()
        val stats = composeRule.onNodeWithText("Tổng", useUnmergedTree = true).fetchSemanticsNode()
        val greetingBottom = greeting.positionInRoot.y + greeting.size.height
        assertTrue(
            "greeting clipped: visible ${greeting.boundsInRoot.height}px of ${greeting.size.height}px",
            greeting.boundsInRoot.height >= greeting.size.height - 1f,
        )
        assertTrue(
            "greeting bottom $greetingBottom overlaps content top ${stats.boundsInRoot.top}",
            greetingBottom <= stats.boundsInRoot.top + 1f,
        )
    }

    @Test
    fun code_sync_badge_and_due_date_stay_fully_visible_at_large_font() {
        show()
        composeRule
            .onNodeWithText("Sửa chữa · WO-1042", useUnmergedTree = true)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule
            .onNodeWithText("Đã nộp · chờ đồng bộ", useUnmergedTree = true)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule
            .onNodeWithText("hạn 01/01/2020", substring = true, useUnmergedTree = true)
            .performScrollTo()
            .assertIsDisplayed()
    }
}
