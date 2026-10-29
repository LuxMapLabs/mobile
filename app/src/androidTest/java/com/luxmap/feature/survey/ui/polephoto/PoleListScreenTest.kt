package com.luxmap.feature.survey.ui.polephoto

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
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
                PoleListScreen(workOrderId = "WO-1", state = PoleListUiState.Loading, onCapturePole = {}, onBack = {})
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
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("POLE-1").assertExists()
        composeRule.onNodeWithText("Đèn mờ").assertExists()
    }

    @Test
    fun success_state_tapping_a_pole_row_opens_the_report_method_sheet() {
        composeRule.setContent {
            LuxMapTheme {
                PoleListScreen(
                    workOrderId = "WO-1",
                    state = PoleListUiState.Success(poles = listOf(pole(poleId = "POLE-1"))),
                    onCapturePole = {},
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("POLE-1").performClick()
        composeRule.onNodeWithText("Chụp ảnh").assertExists()
        composeRule.onNodeWithText("Quay video").assertExists()
    }

    @Test
    fun report_method_sheet_choosing_chup_anh_calls_onCapturePole_with_its_id() {
        var capturedPoleId: String? = null
        composeRule.setContent {
            LuxMapTheme {
                PoleListScreen(
                    workOrderId = "WO-1",
                    state = PoleListUiState.Success(poles = listOf(pole(poleId = "POLE-1"))),
                    onCapturePole = { capturedPoleId = it },
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("POLE-1").performClick()
        composeRule.onNodeWithText("Chụp ảnh").performClick()
        assertEquals("POLE-1", capturedPoleId)
    }

    @Test
    fun report_method_sheet_quay_video_is_disabled() {
        var capturedPoleId: String? = null
        composeRule.setContent {
            LuxMapTheme {
                PoleListScreen(
                    workOrderId = "WO-1",
                    state = PoleListUiState.Success(poles = listOf(pole(poleId = "POLE-1"))),
                    onCapturePole = { capturedPoleId = it },
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("POLE-1").performClick()
        composeRule.onNodeWithText("Quay video").performClick()
        composeRule.onNodeWithText("Chưa hỗ trợ gửi video, dùng ảnh thay thế").assertExists()
        assertEquals(null, capturedPoleId)
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
                    onBack = {},
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
                    onBack = {},
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
                    onBack = {},
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
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("Đang xem danh sách cột đã lưu trên máy, có thể chưa phải bản mới nhất")
            .assertExists()
    }

    // Found on a real device 2026-10-29: tapping "Hoan tat" used to call onComplete and navigate
    // away in the same tap, with no confirmation and no way to tell afterward it worked. Now it
    // only opens a confirm sheet - onComplete fires after the user confirms there.
    @Test
    fun success_state_tapping_hoan_tat_opens_a_confirm_sheet_without_completing_yet() {
        var completed = false
        composeRule.setContent {
            LuxMapTheme {
                PoleListScreen(
                    workOrderId = "WO-1",
                    state =
                        PoleListUiState.Success(
                            poles =
                                listOf(
                                    pole(poleId = "POLE-1", serverPhotoId = "PH-1", syncStatus = SyncStatus.DONE),
                                ),
                        ),
                    onCapturePole = {},
                    onBack = {},
                    onComplete = { completed = true },
                )
            }
        }
        composeRule.onNodeWithText("Hoàn tất").performClick()
        composeRule.onNodeWithText("Hoàn tất khảo sát tuyến?").assertExists()
        composeRule.onNodeWithText("1 cột báo lỗi, 0 cột còn lại được tính là bình thường.").assertExists()
        assertEquals(false, completed)
    }

    @Test
    fun confirm_sheet_tapping_kiem_tra_lai_dismisses_without_completing() {
        var completed = false
        composeRule.setContent {
            LuxMapTheme {
                PoleListScreen(
                    workOrderId = "WO-1",
                    state = PoleListUiState.Success(poles = listOf(pole())),
                    onCapturePole = {},
                    onBack = {},
                    onComplete = { completed = true },
                )
            }
        }
        composeRule.onNodeWithText("Hoàn tất").performClick()
        composeRule.onNodeWithText("Kiểm tra lại").performClick()
        composeRule.onNodeWithText("Hoàn tất khảo sát tuyến?").assertDoesNotExist()
        assertEquals(false, completed)
    }

    @Test
    fun confirm_sheet_confirming_calls_onComplete() {
        var completed = false
        composeRule.setContent {
            LuxMapTheme {
                PoleListScreen(
                    workOrderId = "WO-1",
                    state = PoleListUiState.Success(poles = listOf(pole())),
                    onCapturePole = {},
                    onBack = {},
                    onComplete = { completed = true },
                )
            }
        }
        composeRule.onNodeWithText("Hoàn tất").performClick()
        // The sheet's confirm button also reads "Hoan tat", same as the bottom bar button behind
        // it - testTag disambiguates instead of relying on text/position.
        composeRule.onNodeWithTag("confirm_survey_complete").performClick()
        assertEquals(true, completed)
    }

    // Found on a real device 2026-10-29: the back arrow used to also silently call onComplete()
    // (it reused the same callback as the bottom bar button), so leaving the screen after
    // capturing just 1 photo marked the whole survey complete even without tapping "Hoan tat".
    @Test
    fun tapping_the_back_arrow_only_calls_onBack_not_onComplete() {
        var backCalled = false
        var completed = false
        composeRule.setContent {
            LuxMapTheme {
                PoleListScreen(
                    workOrderId = "WO-1",
                    state =
                        PoleListUiState.Success(
                            poles = listOf(pole(serverPhotoId = "PH-1", syncStatus = SyncStatus.DONE)),
                        ),
                    onCapturePole = {},
                    onBack = { backCalled = true },
                    onComplete = { completed = true },
                )
            }
        }
        composeRule.onNodeWithContentDescription("Quay lại").performClick()
        assertEquals(true, backCalled)
        assertEquals(false, completed)
    }

    @Test
    fun a_completed_survey_shows_da_hoan_tat_disabled_and_does_not_open_the_report_sheet() {
        composeRule.setContent {
            LuxMapTheme {
                PoleListScreen(
                    workOrderId = "WO-1",
                    state = PoleListUiState.Success(poles = listOf(pole(poleId = "POLE-1")), isCompleted = true),
                    onCapturePole = {},
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("Đã hoàn tất").assertIsNotEnabled()
        composeRule.onNodeWithText("POLE-1").performClick()
        composeRule.onNodeWithText("Chụp ảnh").assertDoesNotExist()
    }

    @Test
    fun empty_state_shows_the_no_poles_message() {
        composeRule.setContent {
            LuxMapTheme {
                PoleListScreen(workOrderId = "WO-1", state = PoleListUiState.Empty, onCapturePole = {}, onBack = {})
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
                    onBack = {},
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
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("AI chưa phân loại được ảnh này").assertExists()
    }
}
