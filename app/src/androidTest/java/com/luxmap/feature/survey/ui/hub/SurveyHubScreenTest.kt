package com.luxmap.feature.survey.ui.hub

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.luxmap.core.theme.LuxMapTheme
import com.luxmap.core.theme.SyncStatus
import com.luxmap.feature.survey.data.AssignedSurveyRoute
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SurveyHubScreenTest {
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

    private fun uploadingSession() =
        UploadingSurveySession(
            sessionId = "SESSION-1",
            surveySweepId = "SWEEP-2",
            syncStatus = SyncStatus.QUEUED_ONLINE,
        )

    @Test
    fun loading_state_shows_hero_header() {
        composeRule.setContent {
            LuxMapTheme { SurveyHubScreen(state = SurveyHubUiState.Loading, onCheckReadiness = {}) }
        }
        composeRule.onNodeWithText("Khảo sát").assertExists()
    }

    @Test
    fun success_state_with_a_route_shows_the_route_name_and_the_assigned_routes_header() {
        composeRule.setContent {
            LuxMapTheme {
                SurveyHubScreen(
                    state = SurveyHubUiState.Success(assignedRoutes = listOf(route())),
                    onCheckReadiness = {},
                )
            }
        }
        // "Tuyến được giao" by itself is ambiguous - the hero's metric cell has the exact same
        // label text, so this checks the section's own count badge instead ("1 tuyến").
        composeRule.onNodeWithText("Khảo sát đường A").assertExists()
        composeRule.onNodeWithText("1 tuyến").assertExists()
    }

    @Test
    fun an_assigned_route_shows_bat_dau_tuyen_and_disables_capture_pole_photos() {
        composeRule.setContent {
            LuxMapTheme {
                SurveyHubScreen(
                    state = SurveyHubUiState.Success(assignedRoutes = listOf(route())),
                    onCheckReadiness = {},
                )
            }
        }
        composeRule.onNodeWithText("Bắt đầu tuyến").assertIsEnabled()
        composeRule.onNodeWithText("Chụp ảnh cột đèn").assertIsNotEnabled()
    }

    @Test
    fun tapping_bat_dau_tuyen_invokes_onStartRoute_with_the_work_order_id() {
        var started: String? = null
        composeRule.setContent {
            LuxMapTheme {
                SurveyHubScreen(
                    state = SurveyHubUiState.Success(assignedRoutes = listOf(route())),
                    onCheckReadiness = {},
                    onStartRoute = { started = it },
                )
            }
        }
        composeRule.onNodeWithText("Bắt đầu tuyến").performClick()
        assertEquals("WO-1", started)
    }

    @Test
    fun an_in_progress_route_hides_bat_dau_tuyen_and_enables_capture_pole_photos() {
        composeRule.setContent {
            LuxMapTheme {
                SurveyHubScreen(
                    state = SurveyHubUiState.Success(assignedRoutes = listOf(route().copy(woStatus = "in_progress"))),
                    onCheckReadiness = {},
                )
            }
        }
        composeRule.onNodeWithText("Bắt đầu tuyến").assertDoesNotExist()
        composeRule.onNodeWithText("Chụp ảnh cột đèn").assertIsEnabled()
    }

    @Test
    fun a_start_error_shows_an_inline_banner() {
        composeRule.setContent {
            LuxMapTheme {
                SurveyHubScreen(
                    state =
                        SurveyHubUiState.Success(
                            assignedRoutes = listOf(route()),
                            startError = "Không bắt đầu được tuyến này",
                        ),
                    onCheckReadiness = {},
                )
            }
        }
        composeRule.onNodeWithText("Không bắt đầu được tuyến này").assertExists()
    }

    @Test
    fun success_state_with_no_assigned_routes_hides_the_assigned_routes_header() {
        composeRule.setContent {
            LuxMapTheme {
                SurveyHubScreen(
                    state =
                        SurveyHubUiState.Success(
                            assignedRoutes = emptyList(),
                            uploadingSessions = listOf(uploadingSession()),
                        ),
                    onCheckReadiness = {},
                )
            }
        }
        composeRule.onNodeWithText("Kiểm tra sẵn sàng").assertDoesNotExist()
        composeRule.onNodeWithText("Chờ tải lên · SWEEP-2").assertExists()
    }

    @Test
    fun empty_state_shows_the_no_routes_message() {
        composeRule.setContent {
            LuxMapTheme { SurveyHubScreen(state = SurveyHubUiState.Empty, onCheckReadiness = {}) }
        }
        composeRule.onNodeWithText("Chưa có tuyến nào được phân công", substring = true).assertExists()
    }

    @Test
    fun error_state_shows_the_message_and_a_retry_action() {
        composeRule.setContent {
            LuxMapTheme {
                SurveyHubScreen(
                    state = SurveyHubUiState.Error("Không tải được Khảo sát"),
                    onCheckReadiness = {},
                    onRetry = {},
                )
            }
        }
        composeRule.onNodeWithText("Không tải được Khảo sát").assertExists()
        composeRule.onNodeWithText("Thử lại").assertExists()
    }

    @Test
    fun tapping_Xem_va_xac_nhan_on_a_pending_ai_card_invokes_onReviewAiConfirmation() {
        var reviewed: String? = null
        composeRule.setContent {
            LuxMapTheme {
                SurveyHubScreen(
                    state =
                        SurveyHubUiState.Success(
                            assignedRoutes = emptyList(),
                            pendingAiConfirmation =
                                listOf(
                                    LocalPendingAiConfirmation(
                                        surveySweepId = "SWEEP-2029",
                                        normalCount = 11,
                                        dimCount = 2,
                                        outCount = 1,
                                    ),
                                ),
                        ),
                    onCheckReadiness = {},
                    onReviewAiConfirmation = { reviewed = it },
                )
            }
        }
        composeRule.onNodeWithText("Xem & xác nhận").performClick()
        assertEquals("SWEEP-2029", reviewed)
    }
}
