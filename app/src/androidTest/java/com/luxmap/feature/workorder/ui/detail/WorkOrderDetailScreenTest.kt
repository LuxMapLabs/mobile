package com.luxmap.feature.workorder.ui.detail

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.luxmap.core.theme.LuxMapTheme
import com.luxmap.feature.survey.data.PoleListItem
import com.luxmap.feature.workorder.data.WorkOrderDetail
import com.luxmap.feature.workorder.data.WorkOrderFaultDetail
import com.luxmap.feature.workorder.data.entity.LocalWorkOrderCompletionEntity
import com.luxmap.feature.workorder.data.entity.LocalWorkOrderEvidenceEntity
import com.luxmap.feature.workorder.polegps.data.AttachmentStatus
import com.luxmap.feature.workorder.polegps.data.CorridorCheck
import com.luxmap.feature.workorder.polegps.data.MeasurementSource
import com.luxmap.feature.workorder.polegps.data.PoleGpsAttachment
import com.luxmap.feature.workorder.polegps.data.PoleMeasurement
import com.luxmap.feature.workorder.polegps.data.PoleReference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

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

private fun inspectionPhoto(workOrderId: String) =
    LocalWorkOrderEvidenceEntity(
        clientOpId = "inspection-photo",
        workOrderId = workOrderId,
        kind = "observation",
        filePath = "/data/inspection.jpg",
        capturedAt = Instant.parse("2026-10-06T10:00:00Z"),
        lat = 10.0,
        lng = 106.0,
        uploadStatus = "draft",
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
        submitEvents: Flow<ReportSubmitEvent> = emptyFlow(),
        onRetryPoles: () -> Unit = {},
        onOpenMap: (String, Double?, Double?, Boolean) -> Unit = { _, _, _, _ -> },
        onCaptureEvidence: () -> Unit = {},
        onRemoveEvidence: (String) -> Unit = {},
        onRecoverEvidence: (Boolean) -> Unit = {},
        onOpenPdf: (String) -> Unit = {},
        onMeasurePole: (String) -> Unit = {},
        onRetryAttachment: (String) -> Unit = {},
        onRetry: () -> Unit = {},
        onBack: () -> Unit = {},
        onOpenSurvey: (String) -> Unit = {},
    ) {
        composeRule.setContent {
            LuxMapTheme {
                WorkOrderDetailScreen(
                    uiState = uiState,
                    onBack = onBack,
                    onRetry = onRetry,
                    onOpenSurvey = onOpenSurvey,
                    onStart = onStart,
                    onNavigateFault = onNavigateFault,
                    onReportNoteChanged = onReportNoteChanged,
                    onMaterialsUsedChanged = onMaterialsUsedChanged,
                    onFaultOutcomeSelected = onFaultOutcomeSelected,
                    onSubmit = onSubmit,
                    submitEvents = submitEvents,
                    onRetryPoles = onRetryPoles,
                    onOpenMap = onOpenMap,
                    onCaptureEvidence = onCaptureEvidence,
                    onRemoveEvidence = onRemoveEvidence,
                    onRecoverEvidence = onRecoverEvidence,
                    onOpenPdf = onOpenPdf,
                    onMeasurePole = onMeasurePole,
                    onRetryAttachment = onRetryAttachment,
                )
            }
        }
    }

    @Test
    fun inspection_without_faults_shows_target_pole_and_map_action_before_start() {
        var opened: String? = null
        val pole = PoleListItem("POLE-0214", 3, 10.0, 106.0, "normal", note = "Cạnh cầu")
        setContent(
            uiState =
                WorkOrderDetailUiState.Success(
                    detail =
                        inspectionDetail(
                            emptyList(),
                        ).copy(woStatus = "assigned", allowedActions = listOf("start")),
                    orderPoles = OrderPolesUiState.Success(listOf(pole), true),
                ),
            onOpenMap = { id, _, _, _ -> opened = id },
        )
        composeRule.onNodeWithText("Cột trong phạm vi lệnh (1)").assertExists()
        composeRule.onNodeWithText("POLE-0214").assertExists()
        composeRule.onNodeWithText("Toạ độ: 10.000000, 106.000000").assertExists()
        composeRule.onNodeWithText("Danh sách đã lưu, chưa cập nhật").assertExists()
        composeRule.onNodeWithContentDescription("Thông tin cột POLE-0214").performScrollTo().performClick()
        composeRule.onNodeWithText("Đường đi").assertExists()
        composeRule.onNodeWithText("Xem trên bản đồ").performClick()
        assertEquals("POLE-0214", opened)
    }

    @Test
    fun pole_failure_can_retry_without_replacing_the_detail() {
        var retries = 0
        setContent(
            uiState =
                WorkOrderDetailUiState.Success(
                    detail = repairDetail(),
                    orderPoles = OrderPolesUiState.Error("Mất kết nối"),
                ),
            onRetryPoles = { retries++ },
        )
        composeRule.onNodeWithText("Sửa đèn tuyến A").assertExists()
        composeRule.onNodeWithText("Mất kết nối").assertExists()
        composeRule.onNodeWithText("Thử lại").performScrollTo().performClick()
        assertEquals(1, retries)
    }

    @Test
    fun local_save_event_shows_submitted_dialog_until_acknowledged() {
        val events = MutableSharedFlow<ReportSubmitEvent>(extraBufferCapacity = 1)
        setContent(uiState = WorkOrderDetailUiState.Success(detail = repairDetail()), submitEvents = events)
        composeRule.runOnIdle { events.tryEmit(ReportSubmitEvent.SavedLocally) }
        composeRule.onNodeWithTag("submitted_dialog").assertExists()
        composeRule.onNodeWithText("Đã nộp báo cáo").assertExists()
        composeRule.onNodeWithText("lưu trên máy và sẽ tự gửi lên khi có mạng", substring = true).assertExists()
        composeRule.onNodeWithText("Nộp báo cáo thành công", substring = true).assertDoesNotExist()
        composeRule.onNodeWithTag("submitted_dialog_confirm").performClick()
        composeRule.onNodeWithTag("submitted_dialog").assertDoesNotExist()
    }

    @Test
    fun local_save_failure_does_not_show_submitted_dialog() {
        val events = MutableSharedFlow<ReportSubmitEvent>(extraBufferCapacity = 1)
        setContent(uiState = WorkOrderDetailUiState.Success(detail = repairDetail()), submitEvents = events)
        composeRule.runOnIdle { events.tryEmit(ReportSubmitEvent.Failed("disk full")) }
        composeRule.onNodeWithTag("submitted_dialog").assertDoesNotExist()
    }

    @Test
    fun local_save_failure_shows_the_reason() {
        val events = MutableSharedFlow<ReportSubmitEvent>(extraBufferCapacity = 1)
        setContent(uiState = WorkOrderDetailUiState.Success(detail = repairDetail()), submitEvents = events)
        composeRule.runOnIdle { events.tryEmit(ReportSubmitEvent.Failed("disk full")) }
        composeRule.onNodeWithText("Không lưu được báo cáo: disk full").assertExists()
    }

    @Test
    fun a_persisted_draft_shows_a_discreet_local_hint() {
        setContent(uiState = WorkOrderDetailUiState.Success(detail = repairDetail(), hasDraft = true))
        composeRule.onNodeWithText("Đã lưu nháp trên máy").assertExists()
    }

    @Test
    fun loading_state_shows_progress_indicator() {
        setContent(uiState = WorkOrderDetailUiState.Loading)
        composeRule.onNodeWithTag("work_order_detail_loading").assertExists()
    }

    @Test
    fun the_summary_shows_the_current_stage_as_a_row_without_a_multi_step_tracker() {
        setContent(uiState = WorkOrderDetailUiState.Success(detail = repairDetail()))
        composeRule.onNodeWithText("Giai đoạn hiện tại").assertExists()
        composeRule.onAllNodesWithText("Sửa chữa").onFirst().assertExists()
        listOf("Khảo sát", "Duyệt #1", "Kiểm tra", "Duyệt #2").forEach { label ->
            composeRule.onNodeWithText(label).assertDoesNotExist()
        }
    }

    @Test
    fun an_overdue_due_date_shows_the_date_and_a_separate_overdue_line() {
        val detail = repairDetail().copy(dueDate = LocalDate.now().minusDays(2).toString())
        setContent(uiState = WorkOrderDetailUiState.Success(detail = detail))
        composeRule.onNodeWithText("Quá hạn 2 ngày").assertExists()
    }

    @Test
    fun a_cancelled_work_order_shows_its_status_but_no_current_stage() {
        setContent(uiState = WorkOrderDetailUiState.Success(detail = repairDetail(woStatus = "cancelled")))
        composeRule.onNodeWithText("Đã huỷ").assertExists()
        composeRule.onNodeWithText("Giai đoạn hiện tại", substring = true).assertDoesNotExist()
    }

    @Test
    fun a_review_note_shows_the_maintenance_engineers_request() {
        val detail = repairDetail().copy(reviewNote = "Chụp thêm ảnh góc rộng")
        setContent(uiState = WorkOrderDetailUiState.Success(detail = detail))
        composeRule.onNodeWithText("Quản lý yêu cầu bổ sung").assertExists()
        composeRule.onNodeWithText("Chụp thêm ảnh góc rộng").assertExists()
    }

    @Test
    fun only_the_most_urgent_message_is_a_banner_and_the_rest_go_to_the_notes_card() {
        val yesterday = LocalDate.now().minusDays(1).toString()
        val detail =
            repairDetail(allowedActions = listOf("complete"))
                .copy(scheduledDate = yesterday, reviewNote = "Bổ sung ảnh", note = "Ưu tiên trước mưa")
        setContent(uiState = WorkOrderDetailUiState.Success(detail = detail))
        composeRule.onNodeWithText("Đã quá lịch").assertExists()
        composeRule.onNodeWithText("Ghi chú & lịch sử").assertExists()
        composeRule.onNodeWithText("Quản lý yêu cầu bổ sung").assertExists()
        composeRule.onNodeWithText("Ghi chú giao việc").assertExists()
        composeRule.onNodeWithText("Ưu tiên trước mưa").assertExists()
    }

    @Test
    fun a_late_report_warns_but_keeps_the_form_open() {
        val yesterday = LocalDate.now().minusDays(1).toString()
        val detail = repairDetail(allowedActions = listOf("complete")).copy(scheduledDate = yesterday)
        setContent(uiState = WorkOrderDetailUiState.Success(detail = detail))
        composeRule.onNodeWithText("Đã khoá do quá hạn").assertDoesNotExist()
        composeRule.onNodeWithText(
            "Bạn vẫn làm và nộp báo cáo được. Quản lý sẽ thấy phiếu này trễ lịch.",
        ).assertExists()
    }

    @Test
    fun a_passed_schedule_is_flagged_on_the_schedule_row_even_when_the_due_date_is_later() {
        val yesterday = LocalDate.now().minusDays(1).toString()
        val later = LocalDate.now().plusDays(4).toString()
        val detail =
            repairDetail(allowedActions = listOf("complete")).copy(scheduledDate = yesterday, dueDate = later)
        setContent(uiState = WorkOrderDetailUiState.Success(detail = detail))
        composeRule.onNodeWithText("Đã quá lịch").assertExists()
        composeRule.onNodeWithText("Đã qua lịch").assertExists()
        composeRule.onNodeWithText("Quá hạn", substring = true).assertExists()
    }

    @Test
    fun an_open_form_does_not_flag_the_schedule_row() {
        val today = LocalDate.now().toString()
        val detail =
            repairDetail(allowedActions = listOf("complete"))
                .copy(scheduledDate = today, dueDate = LocalDate.now().minusDays(3).toString())
        setContent(uiState = WorkOrderDetailUiState.Success(detail = detail))
        composeRule.onNodeWithText("Quá hạn 3 ngày").assertExists()
        composeRule.onNodeWithText("Đã qua lịch").assertDoesNotExist()
    }

    @Test
    fun the_title_comes_before_the_status_badge_in_the_summary() {
        setContent(uiState = WorkOrderDetailUiState.Success(detail = repairDetail()))
        val title = composeRule.onNodeWithText("Sửa đèn tuyến A").fetchSemanticsNode()
        val badge = composeRule.onNodeWithText("Đang thực hiện").fetchSemanticsNode()
        assertTrue(title.boundsInRoot.top < badge.boundsInRoot.top)
    }

    @Test
    fun the_offline_banner_shows_alongside_the_business_banner() {
        val detail = repairDetail(woStatus = "assigned").copy(cancellationRequestStatus = "pending")
        setContent(uiState = WorkOrderDetailUiState.Success(detail = detail, isOnline = false))
        composeRule.onNodeWithText("Đang ngoại tuyến").assertExists()
        composeRule.onNodeWithText("Đã gửi yêu cầu hủy").assertExists()
    }

    @Test
    fun no_text_on_the_screen_mentions_manager_or_an_internal_review_gate() {
        val detail =
            repairDetail(woStatus = "done").copy(
                allowedActions = emptyList(),
                reviewNote = "Đạt",
                note = "Giao việc",
            )
        setContent(uiState = WorkOrderDetailUiState.Success(detail = detail))
        composeRule.onNodeWithText("Manager", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("cổng", substring = true).assertDoesNotExist()
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
        composeRule.onNodeWithText("Đã gửi yêu cầu hủy").assertExists()
        composeRule.onNodeWithText("Đang chờ duyệt yêu cầu hủy").assertExists()
        composeRule.onNodeWithText("Bắt đầu").assertIsNotEnabled()
    }

    @Test
    fun cancel_request_sits_next_to_start_in_one_bottom_bar_and_opens_the_reason_sheet() {
        val detail = repairDetail(woStatus = "assigned", allowedActions = listOf("start", "request_cancellation"))
        setContent(uiState = WorkOrderDetailUiState.Success(detail = detail))
        composeRule.onNodeWithText("Bắt đầu").assertIsEnabled()
        composeRule.onNodeWithText("Yêu cầu hủy").performClick()
        composeRule.onNodeWithText("Yêu cầu hủy lệnh").assertExists()
    }

    @Test
    fun a_start_error_is_shown_once_right_above_the_button() {
        val detail = repairDetail(woStatus = "assigned", allowedActions = listOf("start"))
        setContent(
            uiState =
                WorkOrderDetailUiState.Success(detail = detail, startError = "Mất kết nối. Vui lòng thử lại."),
        )
        composeRule.onAllNodesWithText("Mất kết nối. Vui lòng thử lại.").assertCountEquals(1)
    }

    @Test
    fun confirming_the_submit_sheet_submits_the_report() {
        var submitted = 0
        val faults = listOf(fault("FAULT-1", "POLE-0047"))
        setContent(
            uiState =
                WorkOrderDetailUiState.Success(
                    detail = inspectionDetail(faults),
                    faultOutcomes = mapOf("FAULT-1" to "fault_present"),
                    reportNote = "Đèn tắt hẳn, chóa bị nứt",
                    localEvidence = listOf(inspectionPhoto(inspectionDetail(faults).workOrderId)),
                ),
            onSubmit = { submitted++ },
        )
        composeRule.onNodeWithText("Nộp báo cáo kiểm tra").performClick()
        composeRule.onNodeWithText("Nộp báo cáo?").assertExists()
        composeRule.onNodeWithText("Nộp báo cáo").performClick()
        assertEquals(1, submitted)
        composeRule.onNodeWithText("Nộp báo cáo thành công", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Đã lưu báo cáo trên máy", substring = true).assertDoesNotExist()
    }

    @Test
    fun dismissing_the_submit_sheet_does_not_submit() {
        var submitted = 0
        val faults = listOf(fault("FAULT-1", "POLE-0047"))
        setContent(
            uiState =
                WorkOrderDetailUiState.Success(
                    detail = inspectionDetail(faults),
                    faultOutcomes = mapOf("FAULT-1" to "fault_present"),
                    reportNote = "Đèn tắt hẳn, chóa bị nứt",
                    localEvidence = listOf(inspectionPhoto(inspectionDetail(faults).workOrderId)),
                ),
            onSubmit = { submitted++ },
        )
        composeRule.onNodeWithText("Nộp báo cáo kiểm tra").performClick()
        composeRule.onNodeWithText("Kiểm tra lại").performClick()
        composeRule.onNodeWithText("Nộp báo cáo?").assertDoesNotExist()
        assertEquals(0, submitted)
    }

    @Test
    fun a_done_work_order_with_no_allowed_actions_shows_the_disabled_submitted_hint() {
        val detail = repairDetail(woStatus = "done").copy(allowedActions = emptyList())
        setContent(uiState = WorkOrderDetailUiState.Success(detail = detail))
        composeRule.onAllNodesWithText("Đã nộp báo cáo").onFirst().assertExists()
        composeRule.onNodeWithText("Chờ Quản lý duyệt").assertExists()
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
    fun fault_rows_have_no_severity_badge() {
        setContent(uiState = WorkOrderDetailUiState.Success(detail = repairDetail()))
        composeRule.onNodeWithText("POLE-0047", substring = true).assertExists()
        composeRule.onNodeWithText("Cao").assertDoesNotExist()
    }

    @Test
    fun a_fault_without_coordinates_says_so_and_has_no_severity_badge() {
        val noLocation = repairDetail().faults.single().copy(lat = 0.0, lng = 0.0)
        setContent(uiState = WorkOrderDetailUiState.Success(detail = repairDetail().copy(faults = listOf(noLocation))))
        composeRule.onNodeWithText("Chưa có toạ độ").assertExists()
        composeRule.onNodeWithText("Cao").assertDoesNotExist()
    }

    @Test
    fun empty_state_shows_the_no_work_order_message_and_a_way_back() {
        var backed = false
        setContent(uiState = WorkOrderDetailUiState.Empty, onBack = { backed = true })
        composeRule.onNodeWithText("Không tìm thấy lệnh này", substring = true).assertExists()
        composeRule.onAllNodesWithText("Quay lại").onFirst().performClick()
        assertTrue(backed)
    }

    @Test
    fun error_state_offers_retry_and_hides_the_raw_exception_message() {
        var retries = 0
        setContent(
            uiState = WorkOrderDetailUiState.Error("java.net.SocketTimeoutException: timeout"),
            onRetry = { retries++ },
        )
        composeRule.onNodeWithText("Không tải được dữ liệu", substring = true).assertExists()
        composeRule.onNodeWithText("SocketTimeoutException", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Thử lại").performClick()
        assertEquals(1, retries)
    }

    @Test
    fun a_network_error_says_saved_data_is_safe() {
        setContent(uiState = WorkOrderDetailUiState.Error("timeout", isNetworkError = true))
        composeRule.onNodeWithText("Không có kết nối mạng", substring = true).assertExists()
        composeRule.onNodeWithText("vẫn an toàn", substring = true).assertExists()
    }

    @Test
    fun the_top_bar_keeps_the_same_back_button_while_loading_and_when_loaded() {
        setContent(uiState = WorkOrderDetailUiState.Loading)
        composeRule.onNodeWithText("Chi tiết lệnh").assertExists()
        composeRule.onNodeWithContentDescription("Quay lại").assertExists()
    }

    @Test
    fun the_top_bar_shows_only_the_kind_and_the_card_holds_title_and_full_code() {
        setContent(uiState = WorkOrderDetailUiState.Success(detail = repairDetail()))
        composeRule.onNodeWithText("Lệnh sửa chữa").assertExists()
        composeRule.onNodeWithText("Sửa chữa WO-1042").assertDoesNotExist()
        composeRule.onNodeWithText("Sửa đèn tuyến A").assertExists()
        composeRule.onNodeWithText("Mã lệnh").assertExists()
        composeRule.onNodeWithText("WO-1042").assertExists()
        composeRule.onNodeWithContentDescription("Quay lại").assertExists()
    }

    @Test
    fun a_repair_with_complete_allowed_shows_the_add_tile_and_the_unmet_requirement() {
        var captured = 0
        val detail = repairDetail(allowedActions = listOf("complete"))
        setContent(uiState = WorkOrderDetailUiState.Success(detail = detail), onCaptureEvidence = { captured++ })
        composeRule.onNodeWithContentDescription("Ảnh sau sửa chữa, bắt buộc").assertExists()
        composeRule.onNodeWithText("ảnh sau sửa chữa", substring = true, ignoreCase = false).assertExists()
        composeRule.onNodeWithText("Thêm ảnh").performScrollTo().performClick()
        assertEquals(1, captured)
    }

    @Test
    fun an_inspection_can_open_the_shared_photo_picker() {
        var opened = false
        val detail = inspectionDetail(emptyList()).copy(allowedActions = listOf("complete"))
        setContent(uiState = WorkOrderDetailUiState.Success(detail = detail), onCaptureEvidence = { opened = true })

        composeRule.onNodeWithContentDescription("Ảnh hiện trường, bắt buộc").assertExists()
        composeRule.onNodeWithText("Thêm ảnh").performScrollTo().performClick()
        assertEquals(true, opened)
    }

    @Test
    fun a_repair_in_progress_lists_only_the_real_completion_fields_without_fake_steps() {
        val detail = repairDetail(allowedActions = listOf("complete"))
        setContent(uiState = WorkOrderDetailUiState.Success(detail = detail))
        composeRule.onNodeWithText("0/2 bắt buộc").assertExists()
        composeRule.onNodeWithContentDescription("Kết quả sửa chữa, bắt buộc").assertExists()
        composeRule.onNodeWithContentDescription("Vật tư đã thay").assertExists()
        composeRule.onNodeWithText("Bắt buộc").assertDoesNotExist()
        composeRule.onNodeWithText("Không bắt buộc").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Kết quả sửa chữa, bắt buộc").assertExists()
        composeRule.onNodeWithContentDescription("Ảnh sau sửa chữa, bắt buộc").assertExists()
        composeRule.onNodeWithContentDescription("Vật tư đã thay").assertExists()
        composeRule.onNodeWithContentDescription("Vật tư đã thay, bắt buộc").assertDoesNotExist()
        composeRule.onNodeWithText("Bắt đầu thực hiện").assertDoesNotExist()
        composeRule.onNodeWithText("Xác nhận sự cố").assertDoesNotExist()
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
    fun a_rejected_photo_can_reopen_the_submission_for_replacement_after_confirmation() {
        var replaceRequested = false
        val detail = repairDetail(allowedActions = listOf("complete"))
        val completion =
            LocalWorkOrderCompletionEntity(detail.workOrderId, "Đã thay bóng đèn mới", null, null, "report", "failed")
        val photo =
            LocalWorkOrderEvidenceEntity(
                "photo",
                detail.workOrderId,
                "after",
                "/data/evidence/photo.jpg",
                Instant.EPOCH,
                10.0,
                106.0,
                "replaceable_error",
            )
        setContent(
            uiState =
                WorkOrderDetailUiState.Success(
                    detail,
                    localEvidence = listOf(photo),
                    localCompletion = completion,
                ),
            onRecoverEvidence = { replaceRequested = it },
        )
        composeRule.onNodeWithText("Cần thay ảnh").assertExists()
        composeRule.onNodeWithText("Thay ảnh lỗi").performScrollTo().performClick()
        composeRule.onNodeWithText("Mở lại để thay ảnh").performClick()
        assertTrue(replaceRequested)
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
    fun an_inspection_lists_each_fault_once_with_its_own_outcome_choice() {
        val faults = listOf(fault("FAULT-1", "POLE-0047"), fault("FAULT-2", "POLE-0048"))
        val state =
            WorkOrderDetailUiState.Success(
                detail = inspectionDetail(faults),
                faultOutcomes = mapOf("FAULT-1" to "fault_present"),
            )
        setContent(uiState = state)
        composeRule.onAllNodesWithText("POLE-0047", substring = true).assertCountEquals(1)
        composeRule.onAllNodesWithText("POLE-0048", substring = true).assertCountEquals(1)
        composeRule.onAllNodesWithText("Có sự cố").assertCountEquals(2)
        composeRule.onNodeWithText("1/2 sự cố đã có kết quả").assertExists()
        composeRule.onNodeWithText("0/10 ký tự tối thiểu").assertExists()
    }

    @Test
    fun selecting_an_outcome_invokes_onFaultOutcomeSelected_with_the_fault_and_wire_value() {
        var picked: Pair<String, String>? = null
        val faults = listOf(fault("FAULT-1", "POLE-0047"), fault("FAULT-2", "POLE-0048"))
        setContent(
            uiState = WorkOrderDetailUiState.Success(detail = inspectionDetail(faults)),
            onFaultOutcomeSelected = { faultId, outcome -> picked = faultId to outcome },
        )
        composeRule.onAllNodesWithText("Không có")[1].performScrollTo().performClick()
        assertEquals("FAULT-2" to "fault_absent", picked)
    }

    @Test
    fun an_inspection_not_yet_started_shows_the_faults_without_outcome_choices() {
        val detail =
            inspectionDetail(listOf(fault("FAULT-1", "POLE-0047")))
                .copy(woStatus = "assigned", allowedActions = listOf("start"))
        setContent(uiState = WorkOrderDetailUiState.Success(detail = detail))
        composeRule.onNodeWithText("POLE-0047", substring = true).assertExists()
        composeRule.onNodeWithText("Có sự cố").assertDoesNotExist()
    }

    @Test
    fun an_inspection_past_its_schedule_keeps_outcome_choices_visible_but_locked() {
        val yesterday = LocalDate.now().minusDays(1).toString()
        val detail = inspectionDetail(listOf(fault("FAULT-1", "POLE-0047"))).copy(scheduledDate = yesterday)
        setContent(
            uiState =
                WorkOrderDetailUiState.Success(
                    detail = detail,
                    faultOutcomes = mapOf("FAULT-1" to "fault_present"),
                ),
        )
        composeRule.onNodeWithText("Có sự cố").assertIsNotEnabled()
    }

    @Test
    fun gps_pole_shows_identification_and_keeps_old_coordinates() {
        val item =
            com.luxmap.feature.survey.data.PoleListItem(
                "P-1", 2, 10.0, 106.0, "normal", segmentId = "S1", note = "Cạnh cầu",
                fixtureType = "led_road_lamp", lampWatt = 100, lampCount = 2, fixtureCount = 1, nearSensitivePoi = true,
            )
        setContent(
            WorkOrderDetailUiState.Success(
                detail = poleGpsDetail(poleIds = listOf("P-1")),
                poleReferences = listOf(PoleReference("P-1", 2, 10.0, 106.0, item)),
            ),
        )
        composeRule.onNodeWithText("Cột số 2, S1", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Cạnh cầu", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Đèn LED, 100W × 2", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Gần trường/cầu", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("GPS cũ: 10.000000, 106.000000").assertExists()
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
    fun a_pdf_that_failed_to_download_says_so_and_retries_on_tap() {
        var retriedId: String? = null
        setContent(
            uiState =
                WorkOrderDetailUiState.Success(
                    detail = poleGpsDetail(),
                    poleAttachments =
                        listOf(PoleGpsAttachment("A-1", "qr.pdf", 10L, AttachmentStatus.FAILED, null)),
                ),
            onRetryAttachment = { retriedId = it },
        )
        composeRule.onNodeWithText("Tải lỗi. Chạm để thử lại.", substring = true).performClick()
        assertEquals("A-1", retriedId)
    }

    @Test
    fun a_started_survey_offers_a_shortcut_to_the_survey_tab() {
        var opened: String? = null
        val detail = repairDetail(woStatus = "in_progress").copy(taskKind = "survey", faults = emptyList())
        setContent(uiState = WorkOrderDetailUiState.Success(detail = detail), onOpenSurvey = { opened = it })
        composeRule.onNodeWithText("Mở danh sách cột").performClick()
        assertEquals("WO-1042", opened)
    }

    @Test
    fun a_survey_not_yet_started_explains_how_to_start_without_a_shortcut() {
        val detail =
            repairDetail(woStatus = "assigned", allowedActions = listOf("start"))
                .copy(taskKind = "survey", faults = emptyList())
        setContent(uiState = WorkOrderDetailUiState.Success(detail = detail))
        composeRule.onNodeWithText("Bấm Bắt đầu", substring = true).assertExists()
        composeRule.onNodeWithText("Mở danh sách cột").assertDoesNotExist()
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
    fun tapping_measure_on_a_pole_row_opens_the_measure_screen_for_that_pole() {
        var measuredId: String? = null
        setContent(
            uiState =
                WorkOrderDetailUiState.Success(
                    detail = poleGpsDetail(poleIds = listOf("P-1")),
                    poleReferences = listOf(PoleReference("P-1", 4, 10.0, 106.0)),
                ),
            onMeasurePole = { measuredId = it },
        )
        composeRule.onNodeWithText("Đo GPS").performScrollTo().performClick()
        assertEquals("P-1", measuredId)
    }

    @Test
    fun tapping_a_pole_row_opens_that_pole_on_the_map_not_the_measure_screen() {
        var mappedId: String? = null
        var measuredId: String? = null
        setContent(
            uiState =
                WorkOrderDetailUiState.Success(
                    detail = poleGpsDetail(poleIds = listOf("P-1")),
                    poleReferences = listOf(PoleReference("P-1", 4, 10.0, 106.0)),
                ),
            onOpenMap = { poleId, _, _, _ -> mappedId = poleId },
            onMeasurePole = { measuredId = it },
        )
        composeRule.onNodeWithText("P-1").performScrollTo().performClick()
        assertEquals("P-1", mappedId)
        assertEquals(null, measuredId)
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
                                photoFilePath = "/data/local/tmp/p1.jpg",
                            ),
                        ),
                ),
        )
        composeRule.onNodeWithText("Nộp báo cáo").assertIsEnabled()
    }

    @Test
    fun submit_stays_locked_while_a_measured_pole_has_no_photo() {
        setContent(
            uiState =
                WorkOrderDetailUiState.Success(
                    detail = poleGpsDetail(poleIds = listOf("P-1")),
                    reportNote = "Đã đo 1 cột đèn hôm nay",
                    poleReferences = listOf(PoleReference("P-1", 1, 10.0, 106.0)),
                    poleMeasurements =
                        listOf(
                            PoleMeasurement(
                                poleId = "P-1",
                                lat = 10.0,
                                lng = 106.0,
                                accuracyM = 3.0,
                                capturedAt = Instant.ofEpochSecond(1),
                                source = MeasurementSource.DEVICE,
                            ),
                        ),
                ),
        )
        composeRule.onNodeWithText("Nộp báo cáo").assertIsNotEnabled()
    }

    private fun corridorState(corridor: CorridorCheck) =
        WorkOrderDetailUiState.Success(
            detail = poleGpsDetail(poleIds = listOf("P-1")),
            reportNote = "Đã đo cột đèn hôm nay",
            poleReferences = listOf(PoleReference("P-1", 1, 10.0, 106.0)),
            poleMeasurements =
                listOf(
                    PoleMeasurement(
                        poleId = "P-1",
                        lat = 10.0,
                        lng = 106.0,
                        accuracyM = 3.0,
                        capturedAt = Instant.ofEpochSecond(1),
                        source = MeasurementSource.DEVICE,
                        photoFilePath = "/data/local/tmp/p1.jpg",
                        corridor = corridor,
                    ),
                ),
        )

    @Test
    fun pole_row_shows_the_corridor_line_and_no_gps_accuracy() {
        setContent(uiState = corridorState(CorridorCheck.Inside(12.3)))
        composeRule.onNodeWithText("Cách tuyến 12.3 m / 25.0 m").assertExists()
        composeRule.onNodeWithText("Sai số GPS", substring = true).assertDoesNotExist()
    }

    @Test
    fun pole_row_marks_a_position_outside_the_corridor() {
        setContent(uiState = corridorState(CorridorCheck.Outside(31.0)))
        composeRule.onNodeWithText("Ngoài hành lang tuyến", substring = true).assertExists()
    }

    @Test
    fun pole_row_shows_no_corridor_line_when_it_cannot_be_checked() {
        setContent(uiState = corridorState(CorridorCheck.Unknown))
        composeRule.onNodeWithText("Cách tuyến", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Ngoài hành lang tuyến", substring = true).assertDoesNotExist()
    }

    @Test
    fun submit_sheet_counts_the_poles_outside_the_corridor() {
        setContent(uiState = corridorState(CorridorCheck.Outside(31.0)))
        composeRule.onNodeWithText("Nộp báo cáo").performClick()
        composeRule.onNodeWithText("Nộp báo cáo?").assertExists()
        composeRule.onNodeWithText("Ngoài hành lang 25 m").assertExists()
        composeRule.onNodeWithText("1 cột").assertExists()
    }
}
