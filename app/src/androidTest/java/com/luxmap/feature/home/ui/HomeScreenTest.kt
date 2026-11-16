package com.luxmap.feature.home.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.luxmap.core.theme.LuxMapTheme
import com.luxmap.feature.home.data.HomeData
import com.luxmap.feature.home.data.HomeMetrics
import com.luxmap.feature.home.data.WorkOrderCluster
import com.luxmap.feature.home.data.WorkOrderSummaryItem
import com.luxmap.feature.workorder.data.CompletionSyncState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

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
    fun emptyState_showsEmptyMessage() {
        setContent(uiState = HomeUiState.Empty)
        composeTestRule.onNodeWithText("Không có việc nào cho hôm nay").assertExists()
    }

    @Test
    fun errorState_showsErrorMessage() {
        setContent(uiState = HomeUiState.Error(message = "Không tải được dữ liệu"))
        composeTestRule.onNodeWithText("Không tải được dữ liệu").assertExists()
    }
}
