package com.luxmap.feature.home.ui

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.luxmap.core.common.DateFormatUtils
import com.luxmap.core.theme.LuxMapTheme
import com.luxmap.feature.home.data.HomeData
import com.luxmap.feature.home.data.HomeMetrics
import com.luxmap.feature.home.data.WorkOrderCluster
import com.luxmap.feature.home.data.WorkOrderSummaryItem
import com.luxmap.feature.workorder.data.CompletionSyncState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

private fun item(
    id: String,
    kind: String = "repair",
    status: String = "assigned",
    title: String = "Lệnh $id",
    faultCount: Int = 0,
    scheduled: String? = null,
    due: String? = null,
) = WorkOrderSummaryItem(
    workOrderId = id,
    woStatus = status,
    dueDate = due,
    priorityScore = null,
    taskKind = kind,
    communeId = "COM-001",
    title = title,
    scheduledDate = scheduled,
    faultCount = faultCount,
)

private fun successWith(
    vararg items: WorkOrderSummaryItem,
    states: Map<String, CompletionSyncState> = emptyMap(),
) = HomeUiState.Success(
    data =
        HomeData(
            metrics =
                HomeMetrics(
                    assignedCount = items.size,
                    inProgressCount = 0,
                    overdueCount = 0,
                    plannedSweepCount = null,
                ),
            clusters = listOf(WorkOrderCluster(clusterLabel = "COM-001", items = items.toList())),
        ),
    completionSyncStates = states,
)

class HomeScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setContent(
        uiState: HomeUiState,
        username: String? = "kysu1",
        unread: Int = 0,
        onOpenNotifications: () -> Unit = {},
        onOpenWorkOrder: (String) -> Unit = {},
        onRetry: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            LuxMapTheme {
                HomeScreen(
                    uiState = uiState,
                    username = username,
                    onOpenWorkOrder = onOpenWorkOrder,
                    onRetry = onRetry,
                    unreadNotificationCount = unread,
                    onOpenNotifications = onOpenNotifications,
                )
            }
        }
    }

    @Test
    fun an_expired_schedule_shows_a_lock_instead_of_due_today() {
        val scheduled = LocalDate.now().minusDays(3).toString()
        setContent(uiState = successWith(item("WO-LOCK", scheduled = scheduled, due = LocalDate.now().toString())))
        composeTestRule.onNodeWithText(
            "Đã khoá · quá lịch ${DateFormatUtils.formatPlainDate(scheduled)}",
            useUnmergedTree = true,
        ).assertExists()
        composeTestRule.onNodeWithText("Hạn hôm nay", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun the_top_bar_is_the_same_in_loading_and_has_the_greeting() {
        setContent(uiState = HomeUiState.Loading)
        composeTestRule.onNodeWithText("Việc hôm nay").assertExists()
        composeTestRule.onNodeWithText("Xin chào, kysu1", substring = true).assertExists()
    }

    @Test
    fun the_bell_reads_its_unread_count_and_opens_notifications() {
        var opened = 0
        setContent(uiState = HomeUiState.Loading, unread = 3, onOpenNotifications = { opened++ })
        composeTestRule.onNodeWithContentDescription("Thông báo, 3 chưa đọc").performClick()
        assertEquals(1, opened)
    }

    @Test
    fun successState_showsRepairWorkOrdersInDefaultTab() {
        setContent(uiState = successWith(item("WO-2031"), item("WO-2019")))
        composeTestRule.onNodeWithText("WO-2031", substring = true).assertExists()
        composeTestRule.onNodeWithText("WO-2019", substring = true).assertExists()
    }

    @Test
    fun a_card_shows_title_fault_count_schedule_and_due_and_opens_the_order() {
        var opened: String? = null
        setContent(
            uiState =
                successWith(
                    item(
                        "WO-7",
                        title = "Sửa đèn chợ",
                        faultCount = 3,
                        scheduled = LocalDate.now().toString(),
                        due = "2030-01-01",
                    ),
                ),
            onOpenWorkOrder = { opened = it },
        )
        composeTestRule.onNodeWithText("Sửa đèn chợ", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithText("3 sự cố tại COM-001", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithText(
            "Làm: ${DateFormatUtils.formatPlainDate(LocalDate.now().toString())}",
            useUnmergedTree = true,
        ).assertExists()
        composeTestRule.onNodeWithText("Hạn 01/01/2030", useUnmergedTree = true).assertExists()
        composeTestRule.onNode(hasClickAction() and hasText("WO-7", substring = true)).performClick()
        assertEquals("WO-7", opened)
    }

    @Test
    fun talkback_reads_the_card_text_and_gets_the_open_action_label() {
        setContent(uiState = successWith(item("WO-7", title = "Sửa đèn chợ", due = "2030-01-01")))
        composeTestRule
            .onNode(hasClickAction() and hasText("WO-7", substring = true))
            .assert(hasText("Sửa đèn chợ", substring = true))
            .assert(hasText("Hạn 01/01/2030", substring = true))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription))
            .assert(
                SemanticsMatcher("open action label") {
                    it.config.getOrNull(SemanticsActions.OnClick)?.label == "Mở lệnh WO-7"
                },
            )
    }

    @Test
    fun a_card_without_dates_says_no_schedule() {
        setContent(uiState = successWith(item("WO-8")))
        composeTestRule.onNodeWithText("Chưa có lịch", useUnmergedTree = true).assertExists()
    }

    @Test
    fun sync_badges_follow_the_real_sync_state() {
        setContent(
            uiState =
                successWith(
                    item("WO-P"),
                    item("WO-F"),
                    item("WO-C"),
                    item("WO-S", status = "in_progress"),
                    states =
                        mapOf(
                            "WO-P" to CompletionSyncState.PENDING,
                            "WO-F" to CompletionSyncState.FAILED,
                            "WO-C" to CompletionSyncState.CONFLICT,
                        ),
                ),
        )
        composeTestRule.onNodeWithText("Đã nộp, chờ đồng bộ", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithText("Đồng bộ lỗi", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithText("Xung đột", useUnmergedTree = true).assertExists()
        composeTestRule.onNode(hasScrollAction()).performScrollToNode(hasText("Đang thực hiện"))
        composeTestRule.onNodeWithText("Đang thực hiện", useUnmergedTree = true).assertExists()
    }

    @Test
    fun the_kind_selector_is_a_radio_group_with_counts() {
        setContent(uiState = successWith(item("R1"), item("I1", kind = "inspection")))
        composeTestRule
            .onNodeWithText("Sửa chữa (1)")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            .assertIsSelected()
        composeTestRule.onNodeWithText("Kiểm tra (1)").assertIsNotSelected().performClick()
        composeTestRule.onNodeWithText("Kiểm tra (1)").assertIsSelected()
    }

    @Test
    fun the_three_stats_stay_on_one_row_on_a_narrow_screen() {
        setContent(uiState = successWith(item("R1", status = "in_progress"), item("R2")))
        val total = composeTestRule.onNodeWithText("Tổng", useUnmergedTree = true).fetchSemanticsNode()
        val inProgress = composeTestRule.onNodeWithText("Đang làm", useUnmergedTree = true).fetchSemanticsNode()
        assertEquals(total.boundsInRoot.top, inProgress.boundsInRoot.top, 1f)
        val overdue = composeTestRule.onNodeWithText("Quá hạn", useUnmergedTree = true).fetchSemanticsNode()
        assertEquals(inProgress.size.height.toFloat(), total.size.height.toFloat(), 1f)
        assertEquals(inProgress.size.height.toFloat(), overdue.size.height.toFloat(), 1f)
    }

    @Test
    fun empty_offers_refresh() {
        var retries = 0
        setContent(uiState = HomeUiState.Empty, onRetry = { retries++ })
        composeTestRule.onNodeWithText("Chưa có việc được giao").assertExists()
        composeTestRule.onNodeWithText("Làm mới").performClick()
        assertEquals(1, retries)
    }

    @Test
    fun a_network_error_uses_the_home_copy_and_retries() {
        var retries = 0
        setContent(
            uiState = HomeUiState.Error("java.net.SocketTimeoutException: timeout", isNetworkError = true),
            onRetry = { retries++ },
        )
        composeTestRule.onNodeWithText("Không tải được danh sách lệnh").assertExists()
        composeTestRule.onNodeWithText("Kiểm tra kết nối rồi thử lại.").assertExists()
        composeTestRule.onNodeWithText("SocketTimeoutException", substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("đã lưu", substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("Thử lại").performClick()
        assertEquals(1, retries)
    }

    @Test
    fun a_generic_error_uses_the_home_copy() {
        setContent(uiState = HomeUiState.Error("boom"))
        composeTestRule.onNodeWithText("Vui lòng thử lại sau ít phút.").assertExists()
        composeTestRule.onNodeWithText("Thử lại").assertExists()
    }

    @Test
    fun loading_is_announced_as_one_region() {
        setContent(uiState = HomeUiState.Loading)
        composeTestRule.onNodeWithContentDescription("Đang tải việc được giao").assertExists()
    }

    @Test
    fun an_empty_tab_shows_its_own_message() {
        setContent(uiState = successWith(item("R1")))
        composeTestRule.onNodeWithText("Kiểm tra (0)").performClick()
        composeTestRule.onNodeWithText("Không có nhiệm vụ kiểm tra").assertExists()
    }
}
