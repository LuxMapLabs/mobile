package com.luxmap.feature.workorder.ui.detail

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.luxmap.core.theme.LuxMapTheme
import com.luxmap.feature.workorder.data.WorkOrderDetail
import com.luxmap.feature.workorder.data.WorkOrderFaultDetail
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

private fun repairDetail(woStatus: String = "in_progress") =
    WorkOrderDetail(
        workOrderId = "WO-1042",
        title = "Sửa đèn tuyến A",
        woStatus = woStatus,
        taskKind = "repair",
        dueDate = "2026-10-10",
        scheduledDate = null,
        note = null,
        allowedActions = emptyList(),
        faults =
            listOf(
                WorkOrderFaultDetail(
                    faultId = "FAULT-1",
                    poleId = "POLE-0047",
                    lat = 10.97,
                    lng = 106.49,
                    faultType = "lamp_out",
                    faultStatus = "confirmed",
                    severity = "high",
                    inspectionOutcome = "fault_present",
                ),
            ),
    )

class WorkOrderDetailScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun loading_state_shows_progress_indicator() {
        composeRule.setContent {
            LuxMapTheme {
                WorkOrderDetailScreen(
                    uiState = WorkOrderDetailUiState.Loading,
                    onBack = {},
                    onStart = {},
                    onNavigateFault = { _, _ -> },
                    onComplete = {},
                )
            }
        }
        composeRule.onNodeWithTag("work_order_detail_loading").assertExists()
    }

    @Test
    fun a_repair_in_progress_shows_the_app_top_bar_title_and_every_lifecycle_step_label() {
        composeRule.setContent {
            LuxMapTheme {
                WorkOrderDetailScreen(
                    uiState = WorkOrderDetailUiState.Success(detail = repairDetail()),
                    onBack = {},
                    onStart = {},
                    onNavigateFault = { _, _ -> },
                    onComplete = {},
                )
            }
        }
        composeRule.onNodeWithText("Lệnh sửa chữa").assertExists()
        listOf("Khảo sát", "Duyệt #1", "Kiểm tra", "Duyệt #2").forEach { label ->
            composeRule.onNodeWithText(label).assertExists()
        }
        // "Sửa chữa" appears twice for a repair order - the kind badge and the current
        // lifecycle step both use the same real label.
        composeRule.onAllNodesWithText("Sửa chữa").assertCountEquals(2)
    }

    @Test
    fun a_cancelled_work_order_hides_the_lifecycle_tracker() {
        composeRule.setContent {
            LuxMapTheme {
                WorkOrderDetailScreen(
                    uiState = WorkOrderDetailUiState.Success(detail = repairDetail(woStatus = "cancelled")),
                    onBack = {},
                    onStart = {},
                    onNavigateFault = { _, _ -> },
                    onComplete = {},
                )
            }
        }
        composeRule.onNodeWithText("Đã huỷ").assertExists()
        composeRule.onNodeWithText("Đã nghiệm thu").assertDoesNotExist()
        composeRule.onNodeWithText("Sửa chữa").assertExists()
    }

    @Test
    fun a_review_note_shows_the_managers_request() {
        val detail = repairDetail().copy(reviewNote = "Chụp thêm ảnh góc rộng")
        composeRule.setContent {
            LuxMapTheme {
                WorkOrderDetailScreen(
                    uiState = WorkOrderDetailUiState.Success(detail = detail),
                    onBack = {},
                    onStart = {},
                    onNavigateFault = { _, _ -> },
                    onComplete = {},
                )
            }
        }
        composeRule.onNodeWithText("Manager yêu cầu bổ sung: Chụp thêm ảnh góc rộng").assertExists()
    }

    @Test
    fun a_done_work_order_with_no_allowed_actions_shows_the_disabled_submitted_hint() {
        val detail = repairDetail(woStatus = "done").copy(allowedActions = emptyList())
        composeRule.setContent {
            LuxMapTheme {
                WorkOrderDetailScreen(
                    uiState = WorkOrderDetailUiState.Success(detail = detail),
                    onBack = {},
                    onStart = {},
                    onNavigateFault = { _, _ -> },
                    onComplete = {},
                )
            }
        }
        composeRule.onNodeWithText("Đã nộp báo cáo").assertExists()
        composeRule.onNodeWithText("Chờ Kỹ sư bảo trì duyệt (cổng #2)").assertExists()
    }

    @Test
    fun tapping_dieu_huong_invokes_onNavigateFault_with_the_fault_coordinates() {
        var navigated: Pair<Double, Double>? = null
        composeRule.setContent {
            LuxMapTheme {
                WorkOrderDetailScreen(
                    uiState = WorkOrderDetailUiState.Success(detail = repairDetail()),
                    onBack = {},
                    onStart = {},
                    onNavigateFault = { lat, lng -> navigated = lat to lng },
                    onComplete = {},
                )
            }
        }
        composeRule.onNodeWithContentDescription("Điều hướng").performClick()
        assertEquals(10.97 to 106.49, navigated)
    }

    @Test
    fun empty_state_shows_the_no_work_order_message() {
        composeRule.setContent {
            LuxMapTheme {
                WorkOrderDetailScreen(
                    uiState = WorkOrderDetailUiState.Empty,
                    onBack = {},
                    onStart = {},
                    onNavigateFault = { _, _ -> },
                    onComplete = {},
                )
            }
        }
        composeRule.onNodeWithText("Không tìm thấy lệnh này").assertExists()
    }

    @Test
    fun error_state_shows_the_message() {
        composeRule.setContent {
            LuxMapTheme {
                WorkOrderDetailScreen(
                    uiState = WorkOrderDetailUiState.Error("Không tải được dữ liệu lệnh"),
                    onBack = {},
                    onStart = {},
                    onNavigateFault = { _, _ -> },
                    onComplete = {},
                )
            }
        }
        composeRule.onNodeWithText("Không tải được dữ liệu lệnh").assertExists()
    }
}
