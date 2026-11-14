package com.luxmap.feature.workorder.ui.detail

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.luxmap.core.theme.LuxMapTheme
import com.luxmap.feature.workorder.data.WorkOrderDetail
import com.luxmap.feature.workorder.data.WorkOrderFaultDetail
import com.luxmap.feature.workorder.data.entity.LocalWorkOrderCompletionEntity
import com.luxmap.feature.workorder.polegps.data.AttachmentStatus
import com.luxmap.feature.workorder.polegps.data.PoleGpsAttachment
import com.luxmap.feature.workorder.polegps.data.PoleReference
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

private fun repairDetail(
    woStatus: String = "in_progress",
    allowedActions: List<String> = emptyList(),
) = WorkOrderDetail(
    workOrderId = "WO-1042",
    title = "Sửa đèn tuyến A",
    woStatus = woStatus,
    taskKind = "repair",
    dueDate = "2026-10-10",
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
                faultType = "lamp_out",
                faultStatus = "confirmed",
                severity = "high",
                inspectionOutcome = "fault_present",
            ),
        ),
)

private fun inspectionDetail(faults: List<WorkOrderFaultDetail>) =
    repairDetail(allowedActions = listOf("complete")).copy(taskKind = "inspection", faults = faults)

private fun poleGpsDetail(
    allowedActions: List<String> = listOf("complete"),
    poleIds: List<String> = listOf("P-1", "P-2"),
) = repairDetail(
    allowedActions = allowedActions,
).copy(taskKind = "pole_gps_update", faults = emptyList(), poleIds = poleIds)

private fun fault(
    id: String,
    poleId: String,
) = WorkOrderFaultDetail(
    faultId = id,
    poleId = poleId,
    lat = 10.0,
    lng = 106.0,
    faultType = "lamp_out",
    faultStatus = "confirmed",
    severity = "high",
    inspectionOutcome = null,
)

class WorkOrderDetailScreenTest {
    @get:Rule val composeRule = createComposeRule()

    private fun setContent(
        uiState: WorkOrderDetailUiState,
        onStart: () -> Unit = {},
        onNavigateFault: (Double, Double) -> Unit = { _, _ -> },
        onReportNoteChanged: (String) -> Unit = {},
        onMaterialsUsedChanged: (String) -> Unit = {},
        onFaultOutcomeSelected: (String, String) -> Unit = { _, _ -> },
        onSubmit: () -> Unit = {},
        onCaptureEvidence: () -> Unit = {},
        onRetakeEvidence: () -> Unit = {},
        onOpenPdf: (String) -> Unit = {},
        onMeasurePole: (String) -> Unit = {},
        onRetryAttachment: (String) -> Unit = {},
    ) {
        composeRule.setContent {
            LuxMapTheme {
                WorkOrderDetailScreen(
                    uiState = uiState,
                    onBack = {},
                    onStart = onStart,
                    onNavigateFault = onNavigateFault,
                    onReportNoteChanged = onReportNoteChanged,
                    onMaterialsUsedChanged = onMaterialsUsedChanged,
                    onFaultOutcomeSelected = onFaultOutcomeSelected,
                    onSubmit = onSubmit,
                    onCaptureEvidence = onCaptureEvidence,
                    onRetakeEvidence = onRetakeEvidence,
                    onOpenPdf = onOpenPdf,
                    onMeasurePole = onMeasurePole,
                    onRetryAttachment = onRetryAttachment,
                )
            }
        }
    }

    @Test
    fun loading_state_shows_progress_indicator() {
        setContent(uiState = WorkOrderDetailUiState.Loading)
        composeRule.onNodeWithTag("work_order_detail_loading").assertExists()
    }

    @Test
    fun a_repair_in_progress_shows_the_app_top_bar_title_and_every_lifecycle_step_label() {
        setContent(uiState = WorkOrderDetailUiState.Success(detail = repairDetail()))
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
        setContent(uiState = WorkOrderDetailUiState.Success(detail = repairDetail(woStatus = "cancelled")))
        composeRule.onNodeWithText("Đã huỷ").assertExists()
        composeRule.onNodeWithText("Đã nghiệm thu").assertDoesNotExist()
        composeRule.onNodeWithText("Sửa chữa").assertExists()
    }

    @Test
    fun a_review_note_shows_the_managers_request() {
        val detail = repairDetail().copy(reviewNote = "Chụp thêm ảnh góc rộng")
        setContent(uiState = WorkOrderDetailUiState.Success(detail = detail))
        composeRule.onNodeWithText("Manager yêu cầu bổ sung: Chụp thêm ảnh góc rộng").assertExists()
    }

    // Client-only lock (not enforced server-side - allowed_actions still has "start" while
    // pending, see WorkOrderRules.cs), per explicit user decision 2026-11-11 overriding the
    // earlier "pending does not lock start" rule (CLAUDE.md WO-CANCEL-REQUEST, 2026-10-29): a
    // Manager rejecting the request after the schedule window closes leaves the work order
    // genuinely stuck, an accepted tradeoff for this change.
    @Test
    fun a_pending_cancellation_locks_the_start_button_and_hints_why() {
        val detail =
            repairDetail(woStatus = "assigned", allowedActions = listOf("start"))
                .copy(cancellationRequestStatus = "pending")
        setContent(uiState = WorkOrderDetailUiState.Success(detail = detail))
        composeRule.onNodeWithText("Đã gửi yêu cầu hủy lệnh · Chờ Kỹ sư bảo trì duyệt").assertExists()
        composeRule.onNodeWithText("Đang chờ duyệt yêu cầu hủy").assertExists()
        composeRule.onNodeWithText("Bắt đầu").assertIsNotEnabled()
    }

    @Test
    fun a_done_work_order_with_no_allowed_actions_shows_the_disabled_submitted_hint() {
        val detail = repairDetail(woStatus = "done").copy(allowedActions = emptyList())
        setContent(uiState = WorkOrderDetailUiState.Success(detail = detail))
        composeRule.onNodeWithText("Đã nộp báo cáo").assertExists()
        composeRule.onNodeWithText("Chờ Kỹ sư bảo trì duyệt (cổng #2)").assertExists()
    }

    @Test
    fun tapping_dieu_huong_invokes_onNavigateFault_with_the_fault_coordinates() {
        var navigated: Pair<Double, Double>? = null
        setContent(
            uiState = WorkOrderDetailUiState.Success(detail = repairDetail()),
            onNavigateFault = { lat, lng -> navigated = lat to lng },
        )
        composeRule.onNodeWithContentDescription("Điều hướng").performClick()
        assertEquals(10.97 to 106.49, navigated)
    }

    @Test
    fun empty_state_shows_the_no_work_order_message() {
        setContent(uiState = WorkOrderDetailUiState.Empty)
        composeRule.onNodeWithText("Không tìm thấy lệnh này").assertExists()
    }

    @Test
    fun error_state_shows_the_message() {
        setContent(uiState = WorkOrderDetailUiState.Error("Không tải được dữ liệu lệnh"))
        composeRule.onNodeWithText("Không tải được dữ liệu lệnh").assertExists()
    }

    @Test
    fun a_repair_with_complete_allowed_shows_the_add_tile_and_the_unmet_requirement() {
        val detail = repairDetail(allowedActions = listOf("complete"))
        setContent(uiState = WorkOrderDetailUiState.Success(detail = detail))
        composeRule.onNodeWithContentDescription("Chụp ảnh").assertExists()
        composeRule.onNodeWithText("Có ảnh sau sửa chữa").assertExists()
    }

    @Test
    fun a_repair_already_submitted_locally_shows_a_locked_submitted_cta_instead_of_the_submit_button() {
        val detail = repairDetail(allowedActions = listOf("complete"))
        val completion =
            LocalWorkOrderCompletionEntity(
                workOrderId = detail.workOrderId,
                reportNote = "Đã thay bóng đèn mới",
                materialsUsed = null,
                faultOutcomesJson = null,
                clientOpId = "CLIENT-OP-1",
                submitStatus = "pending",
            )
        setContent(uiState = WorkOrderDetailUiState.Success(detail = detail, localCompletion = completion))
        composeRule.onNodeWithText("Hoàn thành lệnh").assertDoesNotExist()
        composeRule.onNodeWithText("Đã nộp — Chờ đồng bộ").assertExists().assertIsNotEnabled()
    }

    @Test
    fun a_repair_returned_for_rework_shows_the_submit_button_instead_of_the_locked_cta() {
        // A manager returning the order puts "complete" back in allowedActions while the OLD
        // completion row is still "synced" in Room - the CTA must unlock, not stay stuck on
        // "Da nop" forever.
        val detail = repairDetail(allowedActions = listOf("complete")).copy(reviewNote = "Chụp lại ảnh rõ hơn")
        val completion =
            LocalWorkOrderCompletionEntity(
                workOrderId = detail.workOrderId,
                reportNote = "Đã thay bóng đèn mới",
                materialsUsed = null,
                faultOutcomesJson = null,
                clientOpId = "CLIENT-OP-1",
                submitStatus = "synced",
            )
        setContent(uiState = WorkOrderDetailUiState.Success(detail = detail, localCompletion = completion))
        composeRule.onNodeWithText("Đã nộp", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Hoàn thành lệnh").assertExists()
    }

    @Test
    fun an_inspection_shows_an_outcome_segment_per_fault_and_the_progress_hint() {
        val faults = listOf(fault("FAULT-1", "POLE-0047"), fault("FAULT-2", "POLE-0048"))
        val state =
            WorkOrderDetailUiState.Success(
                detail = inspectionDetail(faults),
                faultOutcomes = mapOf("FAULT-1" to "fault_present"),
            )
        setContent(uiState = state)
        composeRule.onNodeWithText("1/2 sự cố đã có kết quả").assertExists()
        composeRule.onNodeWithText("POLE-0047").assertExists()
        composeRule.onNodeWithText("POLE-0048").assertExists()
    }

    @Test
    fun selecting_an_outcome_invokes_onFaultOutcomeSelected_with_the_fault_and_wire_value() {
        var picked: Pair<String, String>? = null
        val faults = listOf(fault("FAULT-1", "POLE-0047"), fault("FAULT-2", "POLE-0048"))
        setContent(
            uiState = WorkOrderDetailUiState.Success(detail = inspectionDetail(faults)),
            onFaultOutcomeSelected = { faultId, outcome -> picked = faultId to outcome },
        )
        composeRule.onAllNodesWithText("Có sự cố")[0].performClick()
        assertEquals("FAULT-1" to "fault_present", picked)
    }

    @Test
    fun pole_with_no_cached_position_shows_chua_co() {
        setContent(
            uiState =
                WorkOrderDetailUiState.Success(
                    detail = poleGpsDetail(poleIds = listOf("P-1")),
                    poleReferences = listOf(PoleReference("P-1", null, null, null)),
                ),
        )
        composeRule.onNodeWithText("GPS cũ: Chưa có").assertExists()
    }

    @Test
    fun tapping_a_pdf_row_that_is_ready_opens_the_viewer() {
        var openedId: String? = null
        setContent(
            uiState =
                WorkOrderDetailUiState.Success(
                    detail = poleGpsDetail(),
                    poleAttachments =
                        listOf(
                            PoleGpsAttachment("A-1", "qr.pdf", 10L, AttachmentStatus.READY, "/f/qr.pdf"),
                        ),
                ),
            onOpenPdf = { openedId = it },
        )
        composeRule.onNodeWithText("qr.pdf").performClick()
        assertEquals("A-1", openedId)
    }

    @Test
    fun tapping_a_pole_row_opens_the_measure_screen_for_that_pole() {
        var measuredId: String? = null
        setContent(
            uiState =
                WorkOrderDetailUiState.Success(
                    detail = poleGpsDetail(poleIds = listOf("P-1")),
                    poleReferences = listOf(PoleReference("P-1", 4, 10.0, 106.0)),
                ),
            onMeasurePole = { measuredId = it },
        )
        composeRule.onNodeWithText("P-1").performClick()
        assertEquals("P-1", measuredId)
    }

    @Test
    fun submit_button_is_enabled_with_only_1_of_2_poles_measured() {
        setContent(
            uiState =
                WorkOrderDetailUiState.Success(
                    detail = poleGpsDetail(poleIds = listOf("P-1", "P-2")),
                    reportNote = "Đã đo 1 trong 2 cột đèn hôm nay",
                    poleReferences = listOf(PoleReference("P-1", 1, 10.0, 106.0), PoleReference("P-2", 2, 11.0, 107.0)),
                    poleMeasurements =
                        listOf(
                            com.luxmap.feature.workorder.polegps.data.PoleMeasurement(
                                "P-1",
                                10.0,
                                106.0,
                                3.0,
                                java.time.Instant.ofEpochSecond(1),
                                com.luxmap.feature.workorder.polegps.data.MeasurementSource.DEVICE,
                            ),
                        ),
                ),
        )
        composeRule.onNodeWithText("Nộp báo cáo").assertIsEnabled()
    }
}
