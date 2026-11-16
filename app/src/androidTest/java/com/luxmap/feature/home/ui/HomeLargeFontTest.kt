package com.luxmap.feature.home.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.luxmap.core.theme.LuxMapTheme
import com.luxmap.core.ui.components.assertTouchTargetIsAtLeast
import com.luxmap.feature.home.data.HomeData
import com.luxmap.feature.home.data.HomeMetrics
import com.luxmap.feature.home.data.WorkOrderCluster
import com.luxmap.feature.home.data.WorkOrderSummaryItem
import com.luxmap.feature.workorder.data.CompletionSyncState
import org.junit.Rule
import org.junit.Test

class HomeLargeFontTest {
    @get:Rule val composeRule = createComposeRule()

    private fun show() {
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
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                LuxMapTheme {
                    Box(modifier = Modifier.width(360.dp)) {
                        HomeScreen(
                            uiState =
                                HomeUiState.Success(
                                    data = data,
                                    completionSyncStates = mapOf("WO-1042" to CompletionSyncState.PENDING),
                                ),
                            username = "kysu1",
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
        composeRule.onNodeWithContentDescription("Mở lệnh WO-1042").assertTouchTargetIsAtLeast(48.dp)
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
