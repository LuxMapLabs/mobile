package com.luxmap.feature.survey.ui.plan

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.luxmap.core.theme.LuxMapTheme
import com.luxmap.feature.survey.data.AssignedSurveyRoute
import com.luxmap.feature.survey.domain.usecase.SurveyReadinessResult
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SurveyPlanScreenTest {
    @get:Rule val composeRule = createComposeRule()

    private fun route() =
        AssignedSurveyRoute(
            workOrderId = "WO-1",
            title = "Khảo sát đường A",
            communeId = "COM-001",
            woStatus = "assigned",
            dueDate = null,
            scheduledDate = "2026-10-02",
        )

    private fun readyResult() =
        SurveyReadinessResult(
            cameraPermissionGranted = true,
            exposureLockSupported = true,
            timestampSourceRealtime = true,
            gpsAvailable = true,
            freeStorageBytes = 2_000_000_000L,
            requiredStorageBytes = 1_000_000_000L,
            batteryPercent = 80,
        )

    @Test
    fun loading_state_shows_progress_indicator() {
        composeRule.setContent {
            LuxMapTheme { SurveyPlanContent(state = SurveyPlanUiState.Loading, onBack = {}) }
        }
        composeRule.onNodeWithTag("survey_plan_loading").assertExists()
    }

    @Test
    fun success_state_shows_the_route_title_and_work_order_id() {
        composeRule.setContent {
            LuxMapTheme {
                SurveyPlanContent(state = SurveyPlanUiState.Success(route = route()), onBack = {})
            }
        }
        composeRule.onNodeWithText("Khảo sát đường A").assertExists()
        composeRule.onNodeWithText("WO-1").assertExists()
    }

    @Test
    fun tapping_back_invokes_onBack() {
        var backTapped = false
        composeRule.setContent {
            LuxMapTheme {
                SurveyPlanContent(state = SurveyPlanUiState.Success(route = route()), onBack = { backTapped = true })
            }
        }
        composeRule.onNodeWithContentDescription("Quay lại").performClick()
        assertTrue(backTapped)
    }

    @Test
    fun success_state_with_readiness_shows_the_bluetooth_permission_row() {
        composeRule.setContent {
            LuxMapTheme {
                SurveyPlanContent(
                    state = SurveyPlanUiState.Success(route = route(), readiness = readyResult()),
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("Quyền Bluetooth").assertExists()
    }

    @Test
    fun all_7_checklist_rows_exist_immediately_regardless_of_the_reveal_animation() {
        composeRule.setContent {
            LuxMapTheme {
                SurveyPlanContent(
                    state = SurveyPlanUiState.Success(route = route(), readiness = readyResult()),
                    onBack = {},
                )
            }
        }
        // No waitForIdle/clock advance here on purpose - the staggered reveal only changes each
        // row's PASS/FAIL/CHECKING status over time, it must never remove a row from the tree.
        composeRule.onNodeWithText("Quyền camera").assertExists()
        composeRule.onNodeWithText("Khoá exposure hỗ trợ").assertExists()
        composeRule.onNodeWithText("Đồng hồ cảm biến REALTIME").assertExists()
        composeRule.onNodeWithText("GPS").assertExists()
        composeRule.onNodeWithText("Quyền Bluetooth").assertExists()
        composeRule.onNodeWithText("Đủ dung lượng trống").assertExists()
        composeRule.onNodeWithText("Pin đủ (>= 20%)").assertExists()
    }

    @Test
    fun ready_state_enables_the_capture_cta_and_shows_the_full_pass_count() {
        composeRule.setContent {
            LuxMapTheme {
                SurveyPlanContent(
                    state = SurveyPlanUiState.Success(route = route(), readiness = readyResult()),
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("Vào chế độ khảo sát").assertIsEnabled()
        composeRule.onNodeWithText("7/7 đạt").assertExists()
    }

    @Test
    fun a_failed_readiness_check_disables_the_capture_cta() {
        composeRule.setContent {
            LuxMapTheme {
                SurveyPlanContent(
                    state =
                        SurveyPlanUiState.Success(
                            route = route(),
                            readiness = readyResult().copy(gpsAvailable = false),
                        ),
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("Vào chế độ khảo sát").assertIsNotEnabled()
    }

    @Test
    fun a_rejection_reason_shows_the_resurvey_banner() {
        composeRule.setContent {
            LuxMapTheme {
                SurveyPlanContent(
                    state = SurveyPlanUiState.Success(route = route(), rejectionReason = "Video bị mờ"),
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("Video bị mờ").assertExists()
    }

    @Test
    fun no_rejection_reason_means_no_banner() {
        composeRule.setContent {
            LuxMapTheme {
                SurveyPlanContent(state = SurveyPlanUiState.Success(route = route()), onBack = {})
            }
        }
        composeRule.onNodeWithText("Video bị mờ").assertDoesNotExist()
    }

    @Test
    fun empty_state_shows_the_no_routes_message() {
        composeRule.setContent {
            LuxMapTheme { SurveyPlanContent(state = SurveyPlanUiState.Empty, onBack = {}) }
        }
        composeRule.onNodeWithText("Không tìm thấy lệnh khảo sát này").assertExists()
    }

    @Test
    fun error_state_shows_the_message_and_a_retry_action() {
        composeRule.setContent {
            LuxMapTheme {
                SurveyPlanContent(state = SurveyPlanUiState.Error("Không tải được tuyến khảo sát"), onBack = {})
            }
        }
        composeRule.onNodeWithText("Không tải được tuyến khảo sát").assertExists()
        composeRule.onNodeWithText("Thử lại").assertExists()
    }
}
