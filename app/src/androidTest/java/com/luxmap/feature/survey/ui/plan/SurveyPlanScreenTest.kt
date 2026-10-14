package com.luxmap.feature.survey.ui.plan

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.luxmap.core.theme.LuxMapTheme
import com.luxmap.feature.survey.data.AssignedSurveyRoute
import com.luxmap.feature.survey.domain.usecase.SurveyReadinessResult
import org.junit.Assert.assertEquals
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
            LuxMapTheme { SurveyPlanContent(state = SurveyPlanUiState.Loading, onRouteSelected = {}) }
        }
        composeRule.onNodeWithTag("survey_plan_loading").assertExists()
    }

    @Test
    fun success_state_shows_the_route_name() {
        composeRule.setContent {
            LuxMapTheme {
                SurveyPlanContent(
                    state = SurveyPlanUiState.Success(routes = listOf(route())),
                    onRouteSelected = {},
                )
            }
        }
        composeRule.onNodeWithText("Khảo sát đường A").assertExists()
    }

    @Test
    fun success_state_shows_the_bluetooth_permission_row() {
        composeRule.setContent {
            LuxMapTheme {
                SurveyPlanContent(
                    state =
                        SurveyPlanUiState.Success(
                            routes = listOf(route()),
                            selectedWorkOrderId = route().workOrderId,
                            readiness = readyResult(),
                        ),
                    onRouteSelected = {},
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
                    state =
                        SurveyPlanUiState.Success(
                            routes = listOf(route()),
                            selectedWorkOrderId = route().workOrderId,
                            readiness = readyResult(),
                        ),
                    onRouteSelected = {},
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
    fun ready_state_enables_the_capture_cta() {
        composeRule.setContent {
            LuxMapTheme {
                SurveyPlanContent(
                    state =
                        SurveyPlanUiState.Success(
                            routes = listOf(route()),
                            selectedWorkOrderId = route().workOrderId,
                            readiness = readyResult(),
                        ),
                    onRouteSelected = {},
                )
            }
        }
        composeRule.onNodeWithText("Vào chế độ khảo sát").assertIsEnabled()
    }

    @Test
    fun a_failed_readiness_check_disables_the_capture_cta() {
        composeRule.setContent {
            LuxMapTheme {
                SurveyPlanContent(
                    state =
                        SurveyPlanUiState.Success(
                            routes = listOf(route()),
                            selectedWorkOrderId = route().workOrderId,
                            readiness = readyResult().copy(gpsAvailable = false),
                        ),
                    onRouteSelected = {},
                )
            }
        }
        composeRule.onNodeWithText("Vào chế độ khảo sát").assertIsNotEnabled()
    }

    @Test
    fun arriving_with_a_workOrderId_auto_selects_that_route() {
        var selected: String? = null
        composeRule.setContent {
            LuxMapTheme {
                SurveyPlanScreenContent(
                    workOrderId = "WO-1",
                    state = SurveyPlanUiState.Success(routes = listOf(route())),
                    onRouteSelected = { selected = it },
                )
            }
        }
        composeRule.waitForIdle()
        assertEquals("WO-1", selected)
    }

    @Test
    fun a_rejection_reason_shows_the_resurvey_banner() {
        composeRule.setContent {
            LuxMapTheme {
                SurveyPlanContent(
                    state = SurveyPlanUiState.Success(routes = listOf(route()), rejectionReason = "Video bị mờ"),
                    onRouteSelected = {},
                )
            }
        }
        composeRule.onNodeWithText("Video bị mờ").assertExists()
    }

    @Test
    fun no_rejection_reason_means_no_banner() {
        composeRule.setContent {
            LuxMapTheme {
                SurveyPlanContent(
                    state = SurveyPlanUiState.Success(routes = listOf(route())),
                    onRouteSelected = {},
                )
            }
        }
        composeRule.onNodeWithText("Video bị mờ").assertDoesNotExist()
    }

    @Test
    fun a_manual_tap_on_a_different_route_overrides_the_auto_selected_one() {
        val routeA = route()
        val routeB = route().copy(workOrderId = "WO-2")
        var selected: String? = null
        composeRule.setContent {
            var state by
                remember {
                    mutableStateOf<SurveyPlanUiState>(SurveyPlanUiState.Success(routes = listOf(routeA, routeB)))
                }
            LuxMapTheme {
                SurveyPlanScreenContent(
                    workOrderId = "WO-1",
                    state = state,
                    onRouteSelected = { id ->
                        selected = id
                        state = (state as SurveyPlanUiState.Success).copy(selectedWorkOrderId = id, readiness = null)
                    },
                )
            }
        }
        composeRule.waitForIdle() // auto-select lands on WO-1
        // Once WO-1 is selected it stops showing its own button, so this now targets WO-2's.
        composeRule.onNodeWithText("Kiểm tra sẵn sàng").performClick()
        composeRule.waitForIdle()
        assertEquals("WO-2", selected)
    }

    @Test
    fun empty_state_shows_the_no_routes_message() {
        composeRule.setContent {
            LuxMapTheme { SurveyPlanContent(state = SurveyPlanUiState.Empty, onRouteSelected = {}) }
        }
        // substring = true because the real Empty state text adds a contact hint after this part
        // (see SurveyPlanContent) - onNodeWithText needs an exact match otherwise.
        composeRule.onNodeWithText("Chưa có tuyến nào được phân công", substring = true).assertExists()
    }

    @Test
    fun error_state_shows_the_message_and_a_retry_action() {
        composeRule.setContent {
            LuxMapTheme {
                SurveyPlanContent(
                    state = SurveyPlanUiState.Error("Không tải được tuyến khảo sát"),
                    onRouteSelected = {},
                )
            }
        }
        composeRule.onNodeWithText("Không tải được tuyến khảo sát").assertExists()
        composeRule.onNodeWithText("Thử lại").assertExists()
    }
}
