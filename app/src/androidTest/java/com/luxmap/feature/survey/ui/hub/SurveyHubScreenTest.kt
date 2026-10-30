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

    private fun route(woStatus: String = "assigned") =
        AssignedSurveyRoute(
            workOrderId = "WO-1",
            title = "Khảo sát đường A",
            communeId = "COM-001",
            woStatus = woStatus,
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
        composeRule.onNodeWithText("Khảo sát đường A").assertExists()
        composeRule.onNodeWithText("1 tuyến").assertExists()
    }

    // Merged CTA (Khao Sat v4 mock, CLAUDE.md 2026-10-29) - "Bat dau tuyen" and the separately
    // disabled "Chup anh cot den" no longer exist as 2 buttons, replaced by one "Bat dau" button
    // that leads into the pole list after starting.
    @Test
    fun an_assigned_route_shows_bat_dau_on_the_merged_cta_and_quay_video_toan_tuyen() {
        composeRule.setContent {
            LuxMapTheme {
                SurveyHubScreen(
                    state = SurveyHubUiState.Success(assignedRoutes = listOf(route())),
                    onCheckReadiness = {},
                )
            }
        }
        composeRule.onNodeWithText("Bắt đầu").assertIsEnabled()
        composeRule.onNodeWithText("Quay video toàn tuyến").assertIsEnabled()
        composeRule.onNodeWithText("Tiếp tục").assertDoesNotExist()
    }

    // Found on a real device 2026-10-29: a route's card looked completely unchanged after
    // completing its survey at the pole list, since nothing locally tracked a pending completion
    // for it.
    @Test
    fun a_locally_completed_route_shows_a_locked_row_and_hides_the_action_buttons() {
        composeRule.setContent {
            LuxMapTheme {
                SurveyHubScreen(
                    state =
                        SurveyHubUiState.Success(
                            assignedRoutes = listOf(route()),
                            locallyCompletedWorkOrderIds = setOf("WO-1"),
                        ),
                    onCheckReadiness = {},
                )
            }
        }
        composeRule.onNodeWithText("Đã nộp kết quả khảo sát · chờ đồng bộ").assertExists()
        composeRule.onNodeWithText("Bắt đầu").assertDoesNotExist()
        composeRule.onNodeWithText("Quay video toàn tuyến").assertDoesNotExist()
    }

    @Test
    fun tapping_bat_dau_on_the_merged_cta_starts_for_pole_capture() {
        var started: String? = null
        var destination: SurveyRouteStartDestination? = null
        composeRule.setContent {
            LuxMapTheme {
                SurveyHubScreen(
                    state = SurveyHubUiState.Success(assignedRoutes = listOf(route())),
                    onCheckReadiness = {},
                    onStartRoute = { id, dest ->
                        started = id
                        destination = dest
                    },
                )
            }
        }
        composeRule.onNodeWithText("Bắt đầu").performClick()
        assertEquals("WO-1", started)
        assertEquals(SurveyRouteStartDestination.POLE_CAPTURE, destination)
    }

    // Fixes a gap found 2026-10-29: this button used to skip straight to the readiness check
    // without starting the route, risking a lost recording at upload time (POST /sweeps
    // requires wo_status = in_progress server-side).
    @Test
    fun tapping_quay_video_toan_tuyen_starts_for_readiness_check() {
        var started: String? = null
        var destination: SurveyRouteStartDestination? = null
        composeRule.setContent {
            LuxMapTheme {
                SurveyHubScreen(
                    state = SurveyHubUiState.Success(assignedRoutes = listOf(route())),
                    onCheckReadiness = {},
                    onStartRoute = { id, dest ->
                        started = id
                        destination = dest
                    },
                )
            }
        }
        composeRule.onNodeWithText("Quay video toàn tuyến").performClick()
        assertEquals("WO-1", started)
        assertEquals(SurveyRouteStartDestination.READINESS_CHECK, destination)
    }

    @Test
    fun an_in_progress_route_shows_the_pole_capture_label_and_it_calls_onCapturePolePhotos_directly() {
        var captured: String? = null
        composeRule.setContent {
            LuxMapTheme {
                SurveyHubScreen(
                    state = SurveyHubUiState.Success(assignedRoutes = listOf(route(woStatus = "in_progress"))),
                    onCheckReadiness = {},
                    onCapturePolePhotos = { captured = it },
                )
            }
        }
        composeRule.onNodeWithText("Bắt đầu").assertDoesNotExist()
        composeRule.onNodeWithText("Tiếp tục").assertDoesNotExist()
        composeRule.onNodeWithText("Chụp ảnh/video từng cột").assertIsEnabled().performClick()
        assertEquals("WO-1", captured)
    }

    // Found 2026-10-29: a done/verified route used to keep showing "Chup anh cot den" on a
    // disabled button, which looked like a stuck action rather than a finished route.
    @Test
    fun a_done_route_shows_da_hoan_tat_disabled_on_the_merged_cta() {
        composeRule.setContent {
            LuxMapTheme {
                SurveyHubScreen(
                    state = SurveyHubUiState.Success(assignedRoutes = listOf(route(woStatus = "done"))),
                    onCheckReadiness = {},
                )
            }
        }
        composeRule.onNodeWithText("Đã hoàn tất").assertIsNotEnabled()
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
        composeRule.onNodeWithText("Quay video toàn tuyến").assertDoesNotExist()
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

    // WO-CANCEL-REQUEST on the Hub (CLAUDE.md 2026-10-29): button only shows while still
    // assigned, opens the shared CancelRequestSheet, and calls onRequestCancellation on send.
    @Test
    fun an_assigned_route_shows_yeu_cau_huy_and_sending_it_calls_onRequestCancellation() {
        var cancelledId: String? = null
        var cancelledReason: String? = null
        composeRule.setContent {
            LuxMapTheme {
                SurveyHubScreen(
                    state = SurveyHubUiState.Success(assignedRoutes = listOf(route())),
                    onCheckReadiness = {},
                    onRequestCancellation = { id, reason ->
                        cancelledId = id
                        cancelledReason = reason
                    },
                )
            }
        }
        composeRule.onNodeWithText("Yêu cầu hủy").performClick()
        composeRule.onNodeWithText("Lý do khác").performClick()
        composeRule.onNodeWithText("Gửi yêu cầu").performClick()
        assertEquals("WO-1", cancelledId)
        assertEquals("Lý do khác", cancelledReason)
    }

    @Test
    fun an_in_progress_route_hides_yeu_cau_huy() {
        composeRule.setContent {
            LuxMapTheme {
                SurveyHubScreen(
                    state = SurveyHubUiState.Success(assignedRoutes = listOf(route(woStatus = "in_progress"))),
                    onCheckReadiness = {},
                )
            }
        }
        composeRule.onNodeWithText("Yêu cầu hủy").assertDoesNotExist()
    }
}
