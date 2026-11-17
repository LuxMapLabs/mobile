package com.luxmap.feature.survey.ui.polephoto

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
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
    fun the_top_bar_shows_the_title_and_the_work_order() {
        composeRule.setContent {
            LuxMapTheme {
                PoleListScreen(
                    workOrderId = "WO-1",
                    state = PoleListUiState.Success(poles = listOf(pole())),
                    onCapturePole = { _, _ -> },
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("Chụp ảnh cột đèn").assertExists()
        composeRule.onNodeWithText("WO-1").assertExists()
    }

    @Test
    fun a_pole_shows_the_verdict_of_its_photo_not_the_system_status() {
        composeRule.setContent {
            LuxMapTheme {
                PoleListScreen(
                    workOrderId = "WO-1",
                    state =
                        PoleListUiState.Success(
                            poles =
                                listOf(
                                    pole(
                                        poleId = "POLE-1",
                                        fixtureStatus = "dim",
                                        syncStatus = SyncStatus.DONE,
                                        serverPhotoId = "S-1",
                                    )
                                        .copy(aiVerdict = PoleAiVerdict.OFF),
                                    pole(poleId = "POLE-2", position = 2, fixtureStatus = "dim"),
                                ),
                        ),
                    onCapturePole = { _, _ -> },
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("POLE-1").assertExists()
        composeRule.onNodeWithText("Đèn tắt").assertExists()
        composeRule.onNodeWithText("Đèn mờ").assertDoesNotExist()
    }

    @Test
    fun success_state_tapping_a_pole_row_opens_the_report_method_sheet() {
        composeRule.setContent {
            LuxMapTheme {
                PoleListScreen(
                    workOrderId = "WO-1",
                    state = PoleListUiState.Success(poles = listOf(pole(poleId = "POLE-1"))),
                    onCapturePole = { _, _ -> },
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("POLE-1").performClick()
        composeRule.onNodeWithText("Chụp ảnh").assertExists()
        composeRule.onNodeWithText("Chọn từ thư viện").assertExists()
        composeRule.onNodeWithText("Quay video").assertExists()
        composeRule.onNodeWithText("Video từ thư viện").assertExists()
    }

    @Test
    fun report_method_sheet_choosing_chup_anh_calls_onCapturePole_with_its_id() {
        var capturedPoleId: String? = null
        composeRule.setContent {
            LuxMapTheme {
                PoleListScreen(
                    workOrderId = "WO-1",
                    state = PoleListUiState.Success(poles = listOf(pole(poleId = "POLE-1"))),
                    onCapturePole = { poleId, _ -> capturedPoleId = poleId },
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("POLE-1").performClick()
        composeRule.onNodeWithText("Chụp ảnh").performClick()
        assertEquals("POLE-1", capturedPoleId)
    }

    @Test
    fun report_method_sheet_choosing_video_calls_onCaptureVideo_with_CAMERA() {
        var capturedPoleId: String? = null
        var capturedSource: PoleVideoCaptureSource? = null
        composeRule.setContent {
            LuxMapTheme {
                PoleListScreen(
                    workOrderId = "WO-1",
                    state = PoleListUiState.Success(poles = listOf(pole(poleId = "POLE-1"))),
                    onCapturePole = { _, _ -> },
                    onCaptureVideo = { poleId, source ->
                        capturedPoleId = poleId
                        capturedSource = source
                    },
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("POLE-1").performClick()
        composeRule.onNodeWithText("Quay video").performClick()
        assertEquals("POLE-1", capturedPoleId)
        assertEquals(PoleVideoCaptureSource.CAMERA, capturedSource)
    }

    @Test
    fun report_method_sheet_choosing_video_from_the_gallery_calls_onCaptureVideo_with_GALLERY() {
        var capturedSource: PoleVideoCaptureSource? = null
        composeRule.setContent {
            LuxMapTheme {
                PoleListScreen(
                    workOrderId = "WO-1",
                    state = PoleListUiState.Success(poles = listOf(pole(poleId = "POLE-1"))),
                    onCapturePole = { _, _ -> },
                    onCaptureVideo = { _, source -> capturedSource = source },
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("POLE-1").performClick()
        composeRule.onNodeWithText("Video từ thư viện").performClick()
        assertEquals(PoleVideoCaptureSource.GALLERY, capturedSource)
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
                    onCapturePole = { _, _ -> },
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("Đã tải lên").assertExists()
    }

    @Test
    fun tapping_a_pole_with_an_ai_result_opens_it() {
        var viewed: String? = null
        composeRule.setContent {
            LuxMapTheme {
                PoleListScreen(
                    workOrderId = "WO-1",
                    state =
                        PoleListUiState.Success(
                            poles = listOf(pole(serverPhotoId = "PH-9", syncStatus = SyncStatus.DONE)),
                        ),
                    onCapturePole = { _, _ -> },
                    onBack = {},
                    onViewAiResult = { viewed = it },
                )
            }
        }
        composeRule
            .onNode(hasText("POLE-1", substring = true) and hasClickAction())
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performClick()
        assertEquals("PH-9", viewed)
        composeRule.onNodeWithTag("pole_chevron_POLE-1", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Báo lỗi cột").assertDoesNotExist()
    }

    @Test
    fun a_sync_error_hides_the_review_badge() {
        composeRule.setContent {
            LuxMapTheme {
                PoleListScreen(
                    workOrderId = "WO-1",
                    state =
                        PoleListUiState.Success(
                            poles = listOf(pole(syncStatus = SyncStatus.FAILED).copy(hasPendingReview = true)),
                        ),
                    onCapturePole = { _, _ -> },
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("Đồng bộ lỗi").assertExists()
        composeRule.onNodeWithText("Đã xác nhận").assertDoesNotExist()
    }

    @Test
    fun a_completed_survey_keeps_ai_rows_clickable_but_not_the_others() {
        composeRule.setContent {
            LuxMapTheme {
                PoleListScreen(
                    workOrderId = "WO-1",
                    state =
                        PoleListUiState.Success(
                            poles =
                                listOf(
                                    pole(serverPhotoId = "PH-9", syncStatus = SyncStatus.DONE),
                                    pole(poleId = "POLE-2", position = 2),
                                ),
                            isCompleted = true,
                        ),
                    onCapturePole = { _, _ -> },
                    onBack = {},
                )
            }
        }
        composeRule.onNode(hasText("POLE-1", substring = true) and hasClickAction()).assertExists()
        composeRule.onNode(hasText("POLE-2", substring = true) and hasClickAction()).assertDoesNotExist()
    }

    @Test
    fun success_state_shows_the_stale_banner_only_when_isStale_is_true() {
        composeRule.setContent {
            LuxMapTheme {
                PoleListScreen(
                    workOrderId = "WO-1",
                    state = PoleListUiState.Success(poles = listOf(pole()), isStale = true),
                    onCapturePole = { _, _ -> },
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
                    onCapturePole = { _, _ -> },
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
                    onCapturePole = { _, _ -> },
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
                    onCapturePole = { _, _ -> },
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
                    onCapturePole = { _, _ -> },
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
                    onCapturePole = { _, _ -> },
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
                PoleListScreen(
                    workOrderId = "WO-1",
                    state = PoleListUiState.Empty,
                    onCapturePole = { _, _ -> },
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("Tuyến này chưa có cột đèn nào").assertExists()
    }

    @Test
    fun a_network_error_uses_screen_copy_and_retries() {
        var retries = 0
        composeRule.setContent {
            LuxMapTheme {
                PoleListScreen(
                    workOrderId = "WO-1",
                    state = PoleListUiState.Error("java.net.UnknownHostException", isNetworkError = true),
                    onCapturePole = { _, _ -> },
                    onBack = {},
                    onRetry = { retries++ },
                )
            }
        }
        composeRule.onNodeWithText("Không tải được danh sách cột").assertExists()
        composeRule.onNodeWithText("Kiểm tra kết nối rồi thử lại.").assertExists()
        composeRule.onNodeWithText("UnknownHostException", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Thử lại").performClick()
        assertEquals(1, retries)
    }

    @Test
    fun loading_is_one_announced_region() {
        composeRule.setContent {
            LuxMapTheme {
                PoleListScreen(
                    workOrderId = "WO-1",
                    state = PoleListUiState.Loading,
                    onCapturePole = { _, _ -> },
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithContentDescription("Đang tải danh sách cột").assertExists()
    }

    @Test
    fun success_state_shows_the_pending_review_tag_when_hasPendingReview_is_true() {
        composeRule.setContent {
            LuxMapTheme {
                PoleListScreen(
                    workOrderId = "WO-1",
                    state =
                        PoleListUiState.Success(
                            poles =
                                listOf(
                                    pole(serverPhotoId = "SERVER-PH-1", syncStatus = SyncStatus.DONE).copy(
                                        hasPendingReview = true,
                                    ),
                                ),
                        ),
                    onCapturePole = { _, _ -> },
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("Đã xác nhận").assertExists()
    }

    @Test
    fun success_state_shows_the_pending_decision_tag_when_hasPendingDecision_is_true() {
        composeRule.setContent {
            LuxMapTheme {
                PoleListScreen(
                    workOrderId = "WO-1",
                    state =
                        PoleListUiState.Success(
                            poles =
                                listOf(
                                    pole(serverPhotoId = "SERVER-PH-1", syncStatus = SyncStatus.DONE).copy(
                                        hasPendingDecision = true,
                                    ),
                                ),
                        ),
                    onCapturePole = { _, _ -> },
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("Chờ duyệt").assertExists()
    }

    @Test
    fun report_method_sheet_choosing_the_gallery_option_calls_onCapturePole_with_GALLERY() {
        var capturedSource: PolePhotoCaptureSource? = null
        composeRule.setContent {
            LuxMapTheme {
                PoleListScreen(
                    workOrderId = "WO-1",
                    state = PoleListUiState.Success(poles = listOf(pole(poleId = "POLE-1"))),
                    onCapturePole = { _, source -> capturedSource = source },
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("POLE-1").performClick()
        composeRule.onNodeWithText("Chọn từ thư viện").performClick()
        assertEquals(PolePhotoCaptureSource.GALLERY, capturedSource)
    }

    @Test
    fun report_rows_are_buttons_and_a_captured_row_says_it_will_retake() {
        composeRule.setContent {
            LuxMapTheme {
                PoleListScreen(
                    workOrderId = "WO-1",
                    state =
                        PoleListUiState.Success(
                            poles =
                                listOf(
                                    pole(),
                                    pole(poleId = "POLE-2", position = 2, syncStatus = SyncStatus.QUEUED_ONLINE),
                                ),
                        ),
                    onCapturePole = { _, _ -> },
                    onBack = {},
                )
            }
        }

        fun clickLabel(label: String) =
            SemanticsMatcher("click label $label") { it.config.getOrNull(SemanticsActions.OnClick)?.label == label }
        composeRule
            .onNode(hasText("POLE-1", substring = true) and hasClickAction())
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(clickLabel("Chụp ảnh cột"))
        composeRule
            .onNode(hasText("POLE-2", substring = true) and hasClickAction())
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(clickLabel("Chụp lại cột"))
        composeRule.onNodeWithTag("pole_retake_POLE-2", useUnmergedTree = true).assertExists()
    }
}
