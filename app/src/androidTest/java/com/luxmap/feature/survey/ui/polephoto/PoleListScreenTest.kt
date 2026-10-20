package com.luxmap.feature.survey.ui.polephoto

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.luxmap.core.theme.LuxMapTheme
import com.luxmap.core.theme.SyncStatus
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

private fun pole(
    poleId: String = "POLE-1",
    position: Int = 1,
    fixtureStatus: String = "normal",
    serverPhotoId: String? = null,
    syncStatus: SyncStatus? = null,
) = PoleListRowState(poleId, position, fixtureStatus, serverPhotoId, syncStatus)

class PoleListScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun loading_state_shows_the_hero_header() {
        composeRule.setContent {
            LuxMapTheme {
                PoleListScreen(workOrderId = "WO-1", state = PoleListUiState.Loading, onCapturePole = {}, onDone = {})
            }
        }
        composeRule.onNodeWithText("Chụp ảnh cột đèn").assertExists()
    }

    @Test
    fun success_state_shows_each_pole_with_its_condition_badge() {
        composeRule.setContent {
            LuxMapTheme {
                PoleListScreen(
                    workOrderId = "WO-1",
                    state = PoleListUiState.Success(poles = listOf(pole(poleId = "POLE-1", fixtureStatus = "dim"))),
                    onCapturePole = {},
                    onDone = {},
                )
            }
        }
        composeRule.onNodeWithText("POLE-1").assertExists()
        composeRule.onNodeWithText("Đèn mờ").assertExists()
    }

    @Test
    fun success_state_tapping_a_pole_row_calls_onCapturePole_with_its_id() {
        var capturedPoleId: String? = null
        composeRule.setContent {
            LuxMapTheme {
                PoleListScreen(
                    workOrderId = "WO-1",
                    state = PoleListUiState.Success(poles = listOf(pole(poleId = "POLE-1"))),
                    onCapturePole = { capturedPoleId = it },
                    onDone = {},
                )
            }
        }
        composeRule.onNodeWithText("POLE-1").performClick()
        assertEquals("POLE-1", capturedPoleId)
    }

    @Test
    fun success_state_shows_the_sync_status_badge_when_a_photo_was_captured() {
        composeRule.setContent {
            LuxMapTheme {
                PoleListScreen(
                    workOrderId = "WO-1",
                    state =
                        PoleListUiState.Success(
                            poles = listOf(pole(serverPhotoId = "SERVER-PH-1", syncStatus = SyncStatus.DONE)),
                        ),
                    onCapturePole = {},
                    onDone = {},
                )
            }
        }
        composeRule.onNodeWithText("Đã tải lên").assertExists()
    }

    @Test
    fun success_state_tapping_the_ai_result_icon_calls_onViewAiResult_with_the_server_photo_id() {
        var viewedServerPhotoId: String? = null
        composeRule.setContent {
            LuxMapTheme {
                PoleListScreen(
                    workOrderId = "WO-1",
                    state =
                        PoleListUiState.Success(
                            poles = listOf(pole(serverPhotoId = "SERVER-PH-1", syncStatus = SyncStatus.DONE)),
                        ),
                    onCapturePole = {},
                    onDone = {},
                    onViewAiResult = { viewedServerPhotoId = it },
                )
            }
        }
        composeRule.onNodeWithContentDescription("Xem kết quả AI").performClick()
        assertEquals("SERVER-PH-1", viewedServerPhotoId)
    }

    @Test
    fun success_state_hides_the_ai_result_icon_when_the_photo_has_not_finished_syncing() {
        composeRule.setContent {
            LuxMapTheme {
                PoleListScreen(
                    workOrderId = "WO-1",
                    state =
                        PoleListUiState.Success(
                            poles = listOf(pole(serverPhotoId = "SERVER-PH-1", syncStatus = SyncStatus.SYNCING)),
                        ),
                    onCapturePole = {},
                    onDone = {},
                )
            }
        }
        composeRule.onNodeWithContentDescription("Xem kết quả AI").assertDoesNotExist()
    }

    @Test
    fun success_state_shows_the_stale_banner_only_when_isStale_is_true() {
        composeRule.setContent {
            LuxMapTheme {
                PoleListScreen(
                    workOrderId = "WO-1",
                    state = PoleListUiState.Success(poles = listOf(pole()), isStale = true),
                    onCapturePole = {},
                    onDone = {},
                )
            }
        }
        composeRule.onNodeWithText("Đang xem danh sách cột đã lưu trên máy, có thể chưa phải bản mới nhất")
            .assertExists()
    }

    @Test
    fun success_state_tapping_hoan_tat_calls_onDone() {
        var done = false
        composeRule.setContent {
            LuxMapTheme {
                PoleListScreen(
                    workOrderId = "WO-1",
                    state = PoleListUiState.Success(poles = listOf(pole())),
                    onCapturePole = {},
                    onDone = { done = true },
                )
            }
        }
        composeRule.onNodeWithText("Hoàn tất").performClick()
        assertEquals(true, done)
    }

    @Test
    fun empty_state_shows_the_no_poles_message() {
        composeRule.setContent {
            LuxMapTheme {
                PoleListScreen(workOrderId = "WO-1", state = PoleListUiState.Empty, onCapturePole = {}, onDone = {})
            }
        }
        composeRule.onNodeWithText("Tuyến này chưa có cột đèn nào").assertExists()
    }

    @Test
    fun error_state_shows_the_message_and_retry_calls_onRetry() {
        var retried = false
        composeRule.setContent {
            LuxMapTheme {
                PoleListScreen(
                    workOrderId = "WO-1",
                    state = PoleListUiState.Error("network down"),
                    onCapturePole = {},
                    onDone = {},
                    onRetry = { retried = true },
                )
            }
        }
        composeRule.onNodeWithText("network down").assertExists()
        composeRule.onNodeWithText("Thử lại").performClick()
        assertEquals(true, retried)
    }

    @Test
    fun ai_result_sheet_shows_on_top_when_aiResultState_is_not_null() {
        composeRule.setContent {
            LuxMapTheme {
                PoleListScreen(
                    workOrderId = "WO-1",
                    state = PoleListUiState.Success(poles = listOf(pole())),
                    aiResultState = PolePhotoResultUiState.Empty,
                    onCapturePole = {},
                    onDone = {},
                )
            }
        }
        composeRule.onNodeWithText("AI chưa phân loại được ảnh này").assertExists()
    }
}
