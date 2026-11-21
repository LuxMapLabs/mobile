package com.luxmap.feature.survey.ui.hub

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import com.luxmap.core.theme.LuxMapTheme
import com.luxmap.feature.survey.data.AssignedSurveyRoute
import com.luxmap.feature.workorder.data.CompletionSyncState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class SurveyHubScreenTest {
    @get:Rule val composeRule = createComposeRule()

    private fun route(woStatus: String = "assigned") =
        AssignedSurveyRoute(
            workOrderId = "WO-1",
            title = "Khảo sát đường A",
            communeId = "COM-001",
            woStatus = woStatus,
            dueDate = null,
            scheduledDate = LocalDate.now().toString(),
        )

    @Test
    fun the_top_bar_has_the_title_and_a_bell_that_opens_notifications() {
        var opened = 0
        composeRule.setContent {
            LuxMapTheme {
                SurveyHubScreen(
                    state = SurveyHubUiState.Loading,
                    unreadNotificationCount = 2,
                    onOpenNotifications = { opened++ },
                )
            }
        }
        composeRule.onNodeWithText("Khảo sát").assertExists()
        composeRule.onNodeWithContentDescription("Thông báo, 2 chưa đọc").performClick()
        assertEquals(1, opened)
    }

    @Test
    fun the_missing_gps_entry_is_gone() {
        composeRule.setContent {
            LuxMapTheme { SurveyHubScreen(state = SurveyHubUiState.Success(assignedRoutes = listOf(route()))) }
        }
        composeRule.onNodeWithText("Cột thiếu GPS").assertDoesNotExist()
    }

    @Test
    fun success_state_with_an_assigned_route_shows_the_route_name_and_the_todo_section_header() {
        composeRule.setContent {
            LuxMapTheme {
                SurveyHubScreen(state = SurveyHubUiState.Success(assignedRoutes = listOf(route())))
            }
        }
        composeRule.onNodeWithText("Khảo sát đường A").assertExists()
        composeRule.onNodeWithText("Tuyến khảo sát WO-1").assertExists()
        composeRule.onNodeWithText("Chưa bắt đầu (1)").assertExists()
    }

    // Merged CTA (Khao Sat v4 mock, CLAUDE.md 2026-10-29) - "Bat dau tuyen" and the separately
    // disabled "Chup anh cot den" no longer exist as 2 buttons, replaced by one "Bat dau" button
    // that leads into the pole list after starting. The separate "Quay video toan tuyen" entry
    // point was removed from the Hub entirely (user decision, this session) - the F03-F06 screens
    // it led to stay in the codebase, just unreachable from here.
    @Test
    fun an_assigned_route_shows_bat_dau_on_the_merged_cta() {
        composeRule.setContent {
            LuxMapTheme {
                SurveyHubScreen(state = SurveyHubUiState.Success(assignedRoutes = listOf(route())))
            }
        }
        composeRule.onNodeWithText("Bắt đầu").assertIsEnabled()
        composeRule.onNodeWithText("Quay video toàn tuyến").assertDoesNotExist()
        composeRule.onNodeWithText("Tiếp tục").assertDoesNotExist()
    }

    // A route whose survey was already completed at the pole list has nothing left for the
    // Field Engineer to do on it here - showing it (previously it fell into the "Chưa bắt đầu"
    // section, which is actively wrong for a finished route) was found unnecessary on a real
    // device, 2026-11-11. Hidden entirely instead, regardless of how far its wo_status has
    // caught up (in_progress/done/verified all hide the same way).
    @Test
    fun a_locally_completed_route_does_not_appear_in_either_section() {
        composeRule.setContent {
            LuxMapTheme {
                SurveyHubScreen(
                    state =
                        SurveyHubUiState.Success(
                            assignedRoutes = listOf(route(woStatus = "in_progress")),
                            locallyCompletedWorkOrderIds = setOf("WO-1"),
                        ),
                )
            }
        }
        composeRule.onNodeWithText("Khảo sát đường A").assertDoesNotExist()
        composeRule.onNodeWithText("Đang thực hiện").assertDoesNotExist()
        composeRule.onNodeWithText("Chưa bắt đầu").assertDoesNotExist()
    }

    @Test
    fun tapping_bat_dau_on_the_merged_cta_starts_for_pole_capture() {
        var started: String? = null
        composeRule.setContent {
            LuxMapTheme {
                SurveyHubScreen(
                    state = SurveyHubUiState.Success(assignedRoutes = listOf(route())),
                    onStartRoute = { id -> started = id },
                )
            }
        }
        composeRule.onNodeWithText("Bắt đầu").performClick()
        assertEquals("WO-1", started)
    }

    @Test
    fun an_in_progress_route_shows_the_pole_capture_label_and_it_calls_onCapturePolePhotos_directly() {
        var captured: String? = null
        composeRule.setContent {
            LuxMapTheme {
                SurveyHubScreen(
                    state = SurveyHubUiState.Success(assignedRoutes = listOf(route(woStatus = "in_progress"))),
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
    fun a_start_error_shows_an_inline_banner() {
        composeRule.setContent {
            LuxMapTheme {
                SurveyHubScreen(
                    state =
                        SurveyHubUiState.Success(
                            assignedRoutes = listOf(route()),
                            startError = "Không bắt đầu được tuyến này",
                        ),
                )
            }
        }
        composeRule.onNodeWithText("Không bắt đầu được tuyến này").assertExists()
    }

    @Test
    fun a_pole_gps_order_alone_is_listed_under_its_status_group() {
        composeRule.setContent {
            LuxMapTheme {
                SurveyHubScreen(
                    state =
                        SurveyHubUiState.Success(
                            assignedRoutes = emptyList(),
                            assignedPoleGpsOrders = listOf(route().copy(workOrderId = "WO-9")),
                        ),
                )
            }
        }
        composeRule.onNodeWithText("Chưa bắt đầu (1)").assertExists()
        composeRule.onNodeWithText("Đo GPS cột WO-9").assertExists()
    }

    @Test
    fun empty_offers_refresh() {
        var retries = 0
        composeRule.setContent {
            LuxMapTheme {
                SurveyHubScreen(
                    state = SurveyHubUiState.Empty,
                    onRetry = { retries++ },
                )
            }
        }
        composeRule.onNodeWithText("Chưa có việc khảo sát được giao").assertExists()
        composeRule.onNodeWithText("Làm mới").performClick()
        assertEquals(1, retries)
    }

    @Test
    fun a_network_error_uses_hub_copy_and_hides_the_raw_message() {
        var retries = 0
        composeRule.setContent {
            LuxMapTheme {
                SurveyHubScreen(
                    state = SurveyHubUiState.Error("java.net.UnknownHostException", isNetworkError = true),
                    onRetry = { retries++ },
                )
            }
        }
        composeRule.onNodeWithText("Không tải được việc khảo sát").assertExists()
        composeRule.onNodeWithText("Kiểm tra kết nối rồi thử lại.").assertExists()
        composeRule.onNodeWithText("UnknownHostException", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Thử lại").performClick()
        assertEquals(1, retries)
    }

    @Test
    fun loading_is_one_announced_region() {
        composeRule.setContent { LuxMapTheme { SurveyHubScreen(state = SurveyHubUiState.Loading) } }
        composeRule.onNodeWithContentDescription("Đang tải việc khảo sát").assertExists()
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
                )
            }
        }
        composeRule.onNodeWithText("Yêu cầu hủy").assertDoesNotExist()
    }

    @Test
    fun routes_and_pole_gps_orders_are_grouped_by_status() {
        composeRule.setContent {
            LuxMapTheme {
                SurveyHubScreen(
                    state =
                        SurveyHubUiState.Success(
                            assignedRoutes = listOf(route(woStatus = "in_progress")),
                            assignedPoleGpsOrders =
                                listOf(route(woStatus = "assigned").copy(workOrderId = "WO-G", title = "Đo GPS ấp 3")),
                        ),
                )
            }
        }
        composeRule.onNodeWithText("Đang làm (1)").assertExists()
        composeRule.onNodeWithText("Chưa bắt đầu (1)").assertExists()
        composeRule.onNodeWithText("Đo GPS ấp 3", useUnmergedTree = true).assertExists()
    }

    @Test
    fun a_route_scheduled_later_has_locked_buttons_and_an_opens_on_hint() {
        val later = LocalDate.now().plusDays(3)
        composeRule.setContent {
            LuxMapTheme {
                SurveyHubScreen(
                    state =
                        SurveyHubUiState.Success(
                            assignedRoutes = listOf(route().copy(scheduledDate = later.toString())),
                        ),
                )
            }
        }
        composeRule.onNodeWithText("Bắt đầu").assertIsNotEnabled()
        composeRule.onNodeWithText("Yêu cầu hủy").assertIsNotEnabled()
        composeRule.onNodeWithText("Mở vào", substring = true).assertExists()
    }

    @Test
    fun a_route_past_its_schedule_is_locked_and_flags_the_schedule_row() {
        val yesterday = LocalDate.now().minusDays(1)
        composeRule.setContent {
            LuxMapTheme {
                SurveyHubScreen(
                    state =
                        SurveyHubUiState.Success(
                            assignedRoutes = listOf(route().copy(scheduledDate = yesterday.toString())),
                        ),
                )
            }
        }
        composeRule.onNodeWithText("Bắt đầu").assertIsNotEnabled()
        composeRule.onNodeWithText("Đã khoá do quá hạn").assertExists()
        composeRule.onNodeWithText("Đã qua lịch").assertExists()
    }

    @Test
    fun a_pending_sync_route_has_a_sync_badge_and_no_buttons() {
        composeRule.setContent {
            LuxMapTheme {
                SurveyHubScreen(
                    state =
                        SurveyHubUiState.Success(
                            assignedRoutes = listOf(route(woStatus = "in_progress")),
                            locallyCompletedWorkOrderIds = setOf("WO-1"),
                            completionSyncStates = mapOf("WO-1" to CompletionSyncState.FAILED),
                        ),
                )
            }
        }
        composeRule.onNodeWithText("Chờ đồng bộ (1)").assertExists()
        composeRule.onNodeWithText("Đồng bộ lỗi").assertExists()
        composeRule.onNodeWithText("Chụp ảnh/video từng cột").assertDoesNotExist()
    }

    @Test
    fun tapping_a_pole_gps_card_opens_the_detail() {
        var opened: String? = null
        composeRule.setContent {
            LuxMapTheme {
                SurveyHubScreen(
                    state =
                        SurveyHubUiState.Success(
                            assignedRoutes = emptyList(),
                            assignedPoleGpsOrders = listOf(route().copy(workOrderId = "WO-G")),
                        ),
                    onOpenWorkOrderDetail = { opened = it },
                )
            }
        }
        composeRule.onNode(hasClickAction() and hasText("Đo GPS cột", substring = true)).performClick()
        assertEquals("WO-G", opened)
    }

    @Test
    fun an_unknown_highlight_id_does_not_crash() {
        composeRule.setContent {
            LuxMapTheme {
                SurveyHubScreen(
                    state = SurveyHubUiState.Success(assignedRoutes = listOf(route())),
                    highlightWorkOrderId = "NOPE",
                )
            }
        }
        composeRule.onNodeWithText("Bắt đầu").assertExists()
    }

    @Test
    fun a_done_route_is_not_listed() {
        composeRule.setContent {
            LuxMapTheme {
                SurveyHubScreen(
                    state =
                        SurveyHubUiState.Success(
                            assignedRoutes =
                                listOf(
                                    route(),
                                    route(woStatus = "done").copy(workOrderId = "WO-D", title = "Tuyến đã xong"),
                                ),
                        ),
                )
            }
        }
        composeRule.onNodeWithText("Tuyến đã xong").assertDoesNotExist()
        composeRule.onNodeWithText("Chưa bắt đầu (1)").assertExists()
    }

    @Test
    fun the_highlight_scrolls_once_and_does_not_yank_the_list_back_on_later_updates() {
        val routes = (1..8).map { route().copy(workOrderId = "WO-$it", title = "Tuyến số $it") }
        var state by mutableStateOf<SurveyHubUiState>(SurveyHubUiState.Success(assignedRoutes = routes))
        composeRule.setContent {
            LuxMapTheme { SurveyHubScreen(state = state, highlightWorkOrderId = "WO-8") }
        }
        composeRule.waitForIdle()
        composeRule.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        val extended = routes + route().copy(workOrderId = "WO-9", title = "Tuyến số 9")
        state = SurveyHubUiState.Success(assignedRoutes = extended)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Tuyến số 1").assertIsDisplayed()
    }
}
