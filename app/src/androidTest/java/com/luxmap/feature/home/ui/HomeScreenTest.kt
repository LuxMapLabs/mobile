package com.luxmap.feature.home.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.luxmap.core.theme.LuxMapTheme
import com.luxmap.feature.home.data.HomeData
import com.luxmap.feature.home.data.HomeMetrics
import com.luxmap.feature.home.data.WorkOrderCluster
import com.luxmap.feature.home.data.WorkOrderSummaryItem
import org.junit.Rule
import org.junit.Test

// Đúng 4 trạng thái bắt buộc theo CLAUDE.md (P9/A6) — mỗi trạng thái render đúng nội dung, không
// có màn trắng. NOTE: chưa chạy được trong môi trường làm task này (không có adb/emulator kết
// nối) — chỉ xác nhận compileDebugAndroidTestKotlin qua được, cần chạy connectedDebugAndroidTest
// thật trên máy có thiết bị/emulator trước khi coi bước này đã xác minh xong.
class HomeScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun loadingState_showsHeroHeader() {
        composeTestRule.setContent {
            LuxMapTheme {
                HomeScreen(uiState = HomeUiState.Loading, onOpenWorkOrder = {}, onStartSurvey = {}, onRetry = {})
            }
        }

        composeTestRule.onNodeWithText("Việc hôm nay").assertExists()
    }

    @Test
    fun successState_showsMetricsAndClusterLabel() {
        val data =
            HomeData(
                metrics = HomeMetrics(assignedCount = 3, inProgressCount = 1, overdueCount = 0, plannedSweepCount = 2),
                clusters =
                    listOf(
                        WorkOrderCluster(
                            clusterLabel = "Xã Đông Thịnh",
                            items =
                                listOf(
                                    WorkOrderSummaryItem(
                                        workOrderId = "WO-2031",
                                        woStatus = "assigned",
                                        dueDate = null,
                                        priorityScore = null,
                                        taskKind = "repair",
                                    ),
                                ),
                        ),
                    ),
            )

        composeTestRule.setContent {
            LuxMapTheme {
                HomeScreen(
                    uiState = HomeUiState.Success(data = data),
                    onOpenWorkOrder = {},
                    onStartSurvey = {},
                    onRetry = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Xã Đông Thịnh").assertExists()
        composeTestRule.onNodeWithText("WO-2031").assertExists()
    }

    @Test
    fun emptyState_showsEmptyMessage() {
        composeTestRule.setContent {
            LuxMapTheme {
                HomeScreen(uiState = HomeUiState.Empty, onOpenWorkOrder = {}, onStartSurvey = {}, onRetry = {})
            }
        }

        composeTestRule.onNodeWithText("Không có việc nào cho hôm nay").assertExists()
    }

    @Test
    fun errorState_showsErrorMessage() {
        composeTestRule.setContent {
            LuxMapTheme {
                HomeScreen(
                    uiState = HomeUiState.Error(message = "Không tải được dữ liệu"),
                    onOpenWorkOrder = {},
                    onStartSurvey = {},
                    onRetry = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Không tải được dữ liệu").assertExists()
    }
}
