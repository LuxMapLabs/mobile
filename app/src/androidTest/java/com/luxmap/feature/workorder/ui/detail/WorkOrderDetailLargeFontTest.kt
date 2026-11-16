package com.luxmap.feature.workorder.ui.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.luxmap.core.theme.LuxMapTheme
import com.luxmap.core.ui.components.assertTouchTargetIsAtLeast
import com.luxmap.feature.workorder.data.WorkOrderDetail
import com.luxmap.feature.workorder.data.WorkOrderFaultDetail
import org.junit.Rule
import org.junit.Test

private const val LARGE_FONT_SCALE = 1.5f

private fun detail(
    taskKind: String,
    woStatus: String,
    allowedActions: List<String>,
) = WorkOrderDetail(
    workOrderId = "WO-1042",
    title = "Sửa đèn tuyến đường liên xã Phước Hiệp đoạn qua chợ",
    woStatus = woStatus,
    taskKind = taskKind,
    dueDate = "2026-12-01",
    scheduledDate = null,
    note = null,
    allowedActions = allowedActions,
    faults =
        listOf(
            WorkOrderFaultDetail(
                faultId = "FAULT-1",
                poleId = "POLE-0047",
                lat = 10.97,
                lng = 106.49,
                faultType = "runtime_decline",
                faultStatus = "confirmed",
                severity = "urgent",
                inspectionOutcome = null,
            ),
        ),
)

class WorkOrderDetailLargeFontTest {
    @get:Rule val composeRule = createComposeRule()

    private fun setLargeFontContent(content: @Composable () -> Unit) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, LARGE_FONT_SCALE)) {
                LuxMapTheme {
                    Box(modifier = Modifier.width(360.dp)) {
                        content()
                    }
                }
            }
        }
    }

    private fun screen(detail: WorkOrderDetail) {
        setLargeFontContent {
            WorkOrderDetailScreen(
                uiState = WorkOrderDetailUiState.Success(detail = detail),
                onBack = {},
                onStart = {},
                onNavigateFault = { _, _ -> },
                onReportNoteChanged = {},
                onMaterialsUsedChanged = {},
                onFaultOutcomeSelected = { _, _ -> },
                onSubmit = {},
                onCaptureEvidence = {},
                onRetakeEvidence = {},
            )
        }
    }

    @Test
    fun both_bottom_bar_buttons_stay_visible_and_tappable_at_large_font_on_a_narrow_screen() {
        screen(
            detail(
                taskKind = "repair",
                woStatus = "assigned",
                allowedActions = listOf("start", "request_cancellation"),
            ),
        )
        composeRule.onNodeWithText("Yêu cầu hủy").assertIsDisplayed().assertTouchTargetIsAtLeast(48.dp)
        composeRule.onNodeWithText("Bắt đầu").assertIsDisplayed().assertTouchTargetIsAtLeast(48.dp)
    }

    @Test
    fun the_summary_card_stays_readable_at_large_font_on_a_narrow_screen() {
        screen(detail(taskKind = "repair", woStatus = "in_progress", allowedActions = emptyList()))
        composeRule.onNodeWithTag("work_order_summary").assertIsDisplayed()
        composeRule.onNodeWithText("Hạn xử lý").assertIsDisplayed()
        composeRule.onNodeWithText("Giai đoạn hiện tại").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun inspection_outcome_choices_keep_a_48dp_target_at_large_font_on_a_narrow_screen() {
        screen(detail(taskKind = "inspection", woStatus = "in_progress", allowedActions = listOf("complete")))
        listOf("Có sự cố", "Không có", "Chưa rõ").forEach { label ->
            composeRule.onAllNodesWithText(label).onFirst().performScrollTo().assertTouchTargetIsAtLeast(48.dp)
        }
    }
}
