package com.luxmap.feature.workorder.ui.completion

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.luxmap.core.theme.LuxMapTheme
import com.luxmap.feature.workorder.data.WorkOrderDetail
import com.luxmap.feature.workorder.data.WorkOrderFaultDetail
import com.luxmap.feature.workorder.data.entity.LocalWorkOrderEvidenceEntity
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.Instant

private fun repairDetail() =
    WorkOrderDetail(
        workOrderId = "WO-1042",
        title = "Sửa đèn tuyến A",
        woStatus = "in_progress",
        taskKind = "repair",
        dueDate = null,
        scheduledDate = null,
        note = null,
        allowedActions = emptyList(),
        faults = emptyList(),
    )

private fun inspectionDetail() =
    repairDetail().copy(
        taskKind = "inspection",
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
                    inspectionOutcome = null,
                ),
                WorkOrderFaultDetail(
                    faultId = "FAULT-2",
                    poleId = "POLE-0048",
                    lat = 10.98,
                    lng = 106.50,
                    faultType = "lamp_dim",
                    faultStatus = "confirmed",
                    severity = "low",
                    inspectionOutcome = null,
                ),
            ),
    )

private fun successState(
    detail: WorkOrderDetail,
    faultOutcomes: Map<String, String> = emptyMap(),
    hasEvidence: Boolean = false,
) = WorkOrderCompletionUiState.Success(
    detail = detail,
    localEvidence =
        if (hasEvidence) {
            LocalWorkOrderEvidenceEntity(
                clientOpId = "CLIENT-OP-1",
                workOrderId = detail.workOrderId,
                kind = "after",
                filePath = "/tmp/evidence.jpg",
                capturedAt = Instant.parse("2026-10-13T00:00:00Z"),
                lat = 10.97,
                lng = 106.49,
                uploadStatus = "pending",
            )
        } else {
            null
        },
    localCompletion = null,
    isOnline = true,
    reportNote = "",
    materialsUsed = "",
    faultOutcomes = faultOutcomes,
)

class WorkOrderCompletionScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun loading_state_shows_progress_indicator() {
        composeRule.setContent {
            LuxMapTheme {
                WorkOrderCompletionScreen(
                    uiState = WorkOrderCompletionUiState.Loading,
                    onBack = {},
                    onReportNoteChanged = {},
                    onMaterialsUsedChanged = {},
                    onFaultOutcomeSelected = { _, _ -> },
                    onSubmit = {},
                    onCaptureEvidence = {},
                    onRetakeEvidence = {},
                )
            }
        }
        composeRule.onNodeWithTag("work_order_completion_loading").assertExists()
    }

    @Test
    fun a_repair_without_evidence_shows_the_add_tile_and_the_unmet_requirement() {
        composeRule.setContent {
            LuxMapTheme {
                WorkOrderCompletionScreen(
                    uiState = successState(repairDetail()),
                    onBack = {},
                    onReportNoteChanged = {},
                    onMaterialsUsedChanged = {},
                    onFaultOutcomeSelected = { _, _ -> },
                    onSubmit = {},
                    onCaptureEvidence = {},
                    onRetakeEvidence = {},
                )
            }
        }
        composeRule.onNodeWithContentDescription("Chụp ảnh sau").assertExists()
        composeRule.onNodeWithText("Có ảnh sau sửa chữa").assertExists()
    }

    @Test
    fun an_inspection_shows_an_outcome_segment_per_fault_and_the_progress_hint() {
        composeRule.setContent {
            LuxMapTheme {
                WorkOrderCompletionScreen(
                    uiState = successState(inspectionDetail(), faultOutcomes = mapOf("FAULT-1" to "fault_present")),
                    onBack = {},
                    onReportNoteChanged = {},
                    onMaterialsUsedChanged = {},
                    onFaultOutcomeSelected = { _, _ -> },
                    onSubmit = {},
                    onCaptureEvidence = {},
                    onRetakeEvidence = {},
                )
            }
        }
        composeRule.onNodeWithText("1/2 cột đã ghi nhận").assertExists()
        composeRule.onNodeWithText("POLE-0047").assertExists()
        composeRule.onNodeWithText("POLE-0048").assertExists()
    }

    @Test
    fun selecting_an_outcome_invokes_onFaultOutcomeSelected_with_the_fault_and_wire_value() {
        var picked: Pair<String, String>? = null
        composeRule.setContent {
            LuxMapTheme {
                WorkOrderCompletionScreen(
                    uiState = successState(inspectionDetail()),
                    onBack = {},
                    onReportNoteChanged = {},
                    onMaterialsUsedChanged = {},
                    onFaultOutcomeSelected = { faultId, outcome -> picked = faultId to outcome },
                    onSubmit = {},
                    onCaptureEvidence = {},
                    onRetakeEvidence = {},
                )
            }
        }
        composeRule.onAllNodesWithText("Có sự cố")[0].performClick()
        assertEquals("FAULT-1" to "fault_present", picked)
    }

    @Test
    fun empty_state_shows_the_no_work_order_message() {
        composeRule.setContent {
            LuxMapTheme {
                WorkOrderCompletionScreen(
                    uiState = WorkOrderCompletionUiState.Empty,
                    onBack = {},
                    onReportNoteChanged = {},
                    onMaterialsUsedChanged = {},
                    onFaultOutcomeSelected = { _, _ -> },
                    onSubmit = {},
                    onCaptureEvidence = {},
                    onRetakeEvidence = {},
                )
            }
        }
        composeRule.onNodeWithText("Không tìm thấy lệnh này").assertExists()
    }

    @Test
    fun error_state_shows_the_message() {
        composeRule.setContent {
            LuxMapTheme {
                WorkOrderCompletionScreen(
                    uiState = WorkOrderCompletionUiState.Error("Không tải được dữ liệu lệnh"),
                    onBack = {},
                    onReportNoteChanged = {},
                    onMaterialsUsedChanged = {},
                    onFaultOutcomeSelected = { _, _ -> },
                    onSubmit = {},
                    onCaptureEvidence = {},
                    onRetakeEvidence = {},
                )
            }
        }
        composeRule.onNodeWithText("Không tải được dữ liệu lệnh").assertExists()
    }
}
