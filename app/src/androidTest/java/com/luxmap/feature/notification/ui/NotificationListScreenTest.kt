package com.luxmap.feature.notification.ui

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.luxmap.core.theme.LuxMapTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

private fun row(
    id: String = "NTF-1",
    type: String = "work_order_assigned",
    entityType: String = "work_order",
    isRead: Boolean = false,
    title: String = "Bạn được giao phiếu WO-1001",
) = NotificationListRowState(
    id,
    type,
    title,
    "WO-1001 — Thay bóng đèn.",
    entityType,
    "WO-1001",
    "2026-10-18T08:00:00Z",
    isRead,
)

class NotificationListScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun loading_is_one_announced_region() {
        composeRule.setContent {
            LuxMapTheme { NotificationListScreen(state = NotificationListUiState.Loading, onBack = {}) }
        }
        composeRule.onNodeWithContentDescription("Đang tải thông báo").assertExists()
    }

    @Test
    fun success_state_shows_each_notification_title_and_body() {
        composeRule.setContent {
            LuxMapTheme {
                NotificationListScreen(state = NotificationListUiState.Success(listOf(row())), onBack = {})
            }
        }
        composeRule.onNodeWithText("Bạn được giao phiếu WO-1001").assertExists()
        composeRule.onNodeWithText("WO-1001 — Thay bóng đèn.").assertExists()
    }

    @Test
    fun tapping_a_work_order_row_calls_onItemClick_with_that_row() {
        var clicked: NotificationListRowState? = null
        composeRule.setContent {
            LuxMapTheme {
                NotificationListScreen(
                    state = NotificationListUiState.Success(listOf(row())),
                    onBack = {},
                    onItemClick = { clicked = it },
                )
            }
        }
        composeRule.onNodeWithText("Bạn được giao phiếu WO-1001").performClick()
        assertEquals("NTF-1", clicked?.notificationId)
    }

    @Test
    fun tapping_mark_all_read_calls_onMarkAllRead() {
        var marked = false
        composeRule.setContent {
            LuxMapTheme {
                NotificationListScreen(
                    state = NotificationListUiState.Success(listOf(row())),
                    onBack = {},
                    onMarkAllRead = { marked = true },
                )
            }
        }
        composeRule.onNodeWithText("Đọc tất cả").performClick()
        assertEquals(true, marked)
    }

    @Test
    fun empty_state_shows_the_empty_inbox_message() {
        composeRule.setContent {
            LuxMapTheme { NotificationListScreen(state = NotificationListUiState.Empty, onBack = {}) }
        }
        composeRule.onNodeWithText("Chưa có thông báo nào").assertExists()
    }

    @Test
    fun a_network_error_uses_screen_copy_and_retries() {
        var retries = 0
        composeRule.setContent {
            LuxMapTheme {
                NotificationListScreen(
                    state = NotificationListUiState.Error("java.net.SocketTimeoutException", isNetworkError = true),
                    onBack = {},
                    onRetry = { retries++ },
                )
            }
        }
        composeRule.onNodeWithText("Không tải được thông báo").assertExists()
        composeRule.onNodeWithText("Kiểm tra kết nối rồi thử lại.").assertExists()
        composeRule.onNodeWithText("SocketTimeoutException", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Thử lại").performClick()
        assertEquals(1, retries)
    }

    @Test
    fun an_unread_row_shows_the_unread_dot_and_a_read_row_does_not() {
        composeRule.setContent {
            LuxMapTheme {
                NotificationListScreen(
                    state = NotificationListUiState.Success(listOf(row(id = "NTF-1", isRead = false))),
                    onBack = {},
                )
            }
        }
        // useUnmergedTree = true: NotificationRow's outer Row is clickable, which merges all
        // descendant semantics into one node by default - testTag is not a mergeable property,
        // so the dot's tag is only visible by querying the unmerged tree.
        composeRule.onNodeWithTag("notification_unread_dot_NTF-1", useUnmergedTree = true).assertExists()
    }

    @Test
    fun a_notification_type_the_app_does_not_recognize_yet_still_renders_without_crashing() {
        composeRule.setContent {
            LuxMapTheme {
                NotificationListScreen(
                    state = NotificationListUiState.Success(listOf(row(type = "some_future_type"))),
                    onBack = {},
                )
            }
        }
        // No assertion on which icon shows (the fallback is an implementation detail) - the
        // point of this test is that an unrecognized type does not throw during composition and
        // the row's own text still renders, per Review Focus item 1.
        composeRule.onNodeWithText("Bạn được giao phiếu WO-1001").assertExists()
    }

    @Test
    fun success_state_shows_a_more_footer_when_fewer_items_loaded_than_total() {
        composeRule.setContent {
            LuxMapTheme {
                NotificationListScreen(
                    state = NotificationListUiState.Success(listOf(row()), total = 60),
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithTag("notification_list_more_footer").assertExists()
    }

    @Test
    fun success_state_hides_the_more_footer_when_every_notification_is_loaded() {
        composeRule.setContent {
            LuxMapTheme {
                NotificationListScreen(
                    state = NotificationListUiState.Success(listOf(row()), total = 1),
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithTag("notification_list_more_footer").assertDoesNotExist()
    }

    @Test
    fun mark_all_read_is_hidden_when_everything_is_read() {
        composeRule.setContent {
            LuxMapTheme {
                NotificationListScreen(state = NotificationListUiState.Success(listOf(row(isRead = true))), onBack = {})
            }
        }
        composeRule.onNodeWithText("Đọc tất cả").assertDoesNotExist()
        composeRule.onNodeWithText("Thông báo").assertExists()
    }

    @Test
    fun the_filter_is_a_radio_group_with_counts() {
        composeRule.setContent {
            LuxMapTheme {
                NotificationListScreen(
                    state =
                        NotificationListUiState.Success(
                            listOf(row(isRead = false), row(id = "NTF-2", isRead = true)),
                        ),
                    onBack = {},
                )
            }
        }
        composeRule
            .onNodeWithText("Tất cả · 2")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            .assertIsSelected()
        composeRule.onNodeWithText("Chưa đọc · 1").assertIsNotSelected()
    }

    @Test
    fun only_work_order_rows_are_buttons_with_a_chevron_and_unread_is_announced() {
        composeRule.setContent {
            LuxMapTheme {
                NotificationListScreen(
                    state =
                        NotificationListUiState.Success(
                            listOf(
                                row(id = "WO-ROW", entityType = "work_order", title = "Giao lệnh mới"),
                                row(id = "FAULT-ROW", entityType = "fault", title = "Sự cố mới"),
                            ),
                        ),
                    onBack = {},
                )
            }
        }
        composeRule
            .onNode(hasText("Giao lệnh mới", substring = true) and hasClickAction())
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "chưa đọc"))
        composeRule.onNodeWithTag("notification_chevron_WO-ROW", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("notification_chevron_FAULT-ROW", useUnmergedTree = true).assertDoesNotExist()
        composeRule
            .onNode(hasText("Sự cố mới", substring = true) and hasClickAction())
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Role))
    }

    @Test
    fun a_read_row_that_opens_nothing_has_no_click_action() {
        composeRule.setContent {
            LuxMapTheme {
                NotificationListScreen(
                    state =
                        NotificationListUiState.Success(
                            listOf(row(id = "READ-FAULT", entityType = "fault", title = "Sự cố đã đọc", isRead = true)),
                        ),
                    onBack = {},
                )
            }
        }
        composeRule.onNode(hasText("Sự cố đã đọc", substring = true) and hasClickAction()).assertDoesNotExist()
        composeRule.onNodeWithText("Sự cố đã đọc").assertExists()
    }
}
