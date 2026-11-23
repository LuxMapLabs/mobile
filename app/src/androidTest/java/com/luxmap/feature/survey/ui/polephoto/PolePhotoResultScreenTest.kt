package com.luxmap.feature.survey.ui.polephoto

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import com.luxmap.core.theme.LuxMapTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

private fun successState(
    cvState: String? = "on",
    confidence: Double? = 0.92,
    decision: String? = null,
    decisionNote: String? = null,
    isStale: Boolean = false,
    mediaType: String = "photo",
    luxValue: Double? = null,
) = PolePhotoResultUiState.Success(
    filePath = "/data/pole-photo.jpg",
    cvState = cvState,
    confidence = confidence,
    modelVersion = "cv-v1",
    analyzedAt = Instant.parse("2026-10-16T10:00:10Z"),
    decision = decision,
    decisionNote = decisionNote,
    decidedBy = "U-9",
    decidedAt = Instant.parse("2026-10-16T11:00:00Z"),
    isStale = isStale,
    mediaType = mediaType,
    luxValue = luxValue,
)

class PolePhotoResultScreenTest {
    @get:Rule val composeRule = createComposeRule()

    private fun choice(label: String) =
        composeRule.onNode(hasText(label) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))

    @Test
    fun loading_is_one_announced_region() {
        composeRule.setContent {
            LuxMapTheme {
                PolePhotoResultScreen(
                    poleId = "P-0103",
                    position = 3,
                    state = PolePhotoResultUiState.Loading,
                    onBack = {},
                    onRetry = {},
                )
            }
        }
        composeRule.onNodeWithContentDescription("Đang tải kết quả AI").assertExists()
    }

    @Test
    fun top_bar_shows_title_and_pole_in_every_state() {
        composeRule.setContent {
            LuxMapTheme {
                PolePhotoResultScreen(
                    poleId = "P-0103",
                    position = 3,
                    state = PolePhotoResultUiState.Loading,
                    onBack = {},
                    onRetry = {},
                )
            }
        }
        composeRule.onNodeWithText("Kết quả AI").assertExists()
        composeRule.onNodeWithText("Cột 3 (P-0103)").assertExists()
    }

    @Test
    fun the_manager_decision_is_a_banner_above_the_photo() {
        composeRule.setContent {
            LuxMapTheme {
                PolePhotoResultScreen(
                    poleId = "P-0103",
                    position = 3,
                    state = successState(decision = "confirm", decisionNote = "Đúng hiện trạng"),
                    onBack = {},
                    onRetry = {},
                )
            }
        }
        val banner = composeRule.onNodeWithText("Quản lý đã xác nhận", substring = true).fetchSemanticsNode()
        val photo = composeRule.onNodeWithTag("photo_preview").fetchSemanticsNode()
        assertTrue(banner.boundsInRoot.top < photo.boundsInRoot.top)
    }

    @Test
    fun pressing_the_back_arrow_invokes_onBack() {
        var wentBack = false
        composeRule.setContent {
            LuxMapTheme {
                PolePhotoResultScreen(
                    poleId = "P-0103",
                    position = 3,
                    state = PolePhotoResultUiState.Empty,
                    onBack = { wentBack = true },
                    onRetry = {},
                )
            }
        }
        composeRule.onNodeWithContentDescription("Quay lại").performClick()
        assertEquals(true, wentBack)
    }

    @Test
    fun cancelling_the_result_requires_confirmation_and_then_returns_to_the_list() {
        var withdrawn = false
        var wentBack = false
        composeRule.setContent {
            LuxMapTheme {
                PolePhotoResultScreen(
                    poleId = "P-0103",
                    position = 3,
                    state = successState(),
                    onBack = { wentBack = true },
                    onRetry = {},
                    onWithdrawResult = { withdrawn = true },
                )
            }
        }
        composeRule.onNodeWithText("Hủy").performClick()
        composeRule.onNodeWithText("Giữ lại").performClick()
        assertEquals(false, withdrawn)
        composeRule.onNodeWithText("Hủy").performClick()
        composeRule.onNodeWithText("Hủy kết quả").performClick()
        composeRule.runOnIdle {
            assertEquals(true, withdrawn)
            assertEquals(true, wentBack)
        }
    }

    @Test
    fun success_state_tapping_chup_lai_invokes_onRetake() {
        var retaken = false
        composeRule.setContent {
            LuxMapTheme {
                PolePhotoResultScreen(
                    poleId = "P-0103",
                    position = 3,
                    state = successState(),
                    onBack = {},
                    onRetry = {},
                    onRetake = { retaken = true },
                )
            }
        }
        composeRule.onNodeWithTag("details_toggle").performScrollTo().performClick()
        composeRule.onNodeWithText("Chụp lại").performScrollTo().performClick()
        assertEquals(false, retaken)
        composeRule.onNodeWithText("Tiếp tục").performClick()
        assertEquals(true, retaken)
    }

    @Test
    fun success_state_cancelling_the_retake_dialog_does_not_invoke_onRetake() {
        var retaken = false
        composeRule.setContent {
            LuxMapTheme {
                PolePhotoResultScreen(
                    poleId = "P-0103",
                    position = 3,
                    state = successState().copy(filePath = "/data/gallery-sample.mp4", mediaType = "video"),
                    onBack = {},
                    onRetry = {},
                    onRetake = { retaken = true },
                )
            }
        }
        composeRule.onNodeWithTag("details_toggle").performScrollTo().performClick()
        composeRule.onNodeWithText("Chọn video khác").performScrollTo().performClick()
        composeRule.onNodeWithText("Hủy").performClick()
        assertEquals(false, retaken)
    }

    @Test
    fun retake_button_is_hidden_once_a_manager_decision_already_exists() {
        composeRule.setContent {
            LuxMapTheme {
                PolePhotoResultScreen(
                    poleId = "P-0103",
                    position = 3,
                    state = successState(decision = "confirm"),
                    onBack = {},
                    onRetry = {},
                )
            }
        }
        composeRule.onNodeWithTag("details_toggle").performScrollTo().performClick()
        composeRule.onNodeWithText("Chụp lại").assertDoesNotExist()
    }

    @Test
    fun success_state_tapping_the_photo_opens_the_zoom_viewer() {
        composeRule.setContent {
            LuxMapTheme {
                PolePhotoResultScreen(
                    poleId = "P-0103",
                    position = 3,
                    state = successState(),
                    onBack = {},
                    onRetry = {},
                )
            }
        }
        composeRule.onNodeWithTag("photo_preview").performClick()
        composeRule.onNodeWithTag("photo_viewer").assertExists()
    }

    @Test
    fun success_state_with_video_media_type_shows_a_video_placeholder_not_a_broken_image() {
        composeRule.setContent {
            LuxMapTheme {
                PolePhotoResultScreen(
                    poleId = "P-0103",
                    position = 3,
                    state = successState(mediaType = "video"),
                    onBack = {},
                    onRetry = {},
                )
            }
        }
        composeRule.onNodeWithText("Video clip").assertExists()
    }

    @Test
    fun success_state_shows_the_cv_state_label_and_confidence() {
        composeRule.setContent {
            LuxMapTheme {
                PolePhotoResultScreen(
                    poleId = "P-0103",
                    position = 3,
                    state = successState(cvState = "on", confidence = 0.92),
                    onBack = {},
                    onRetry = {},
                )
            }
        }
        composeRule.onNodeWithTag("ai_verdict_label").assertTextEquals("Đèn sáng")
        composeRule.onNodeWithText("AI nhận định").assertExists()
        composeRule.onNodeWithText("Tin cậy cao (92%)").assertExists()
    }

    @Test
    fun success_state_hides_the_decision_card_when_decision_is_null() {
        composeRule.setContent {
            LuxMapTheme {
                PolePhotoResultScreen(
                    poleId = "P-0103",
                    position = 3,
                    state = successState(decision = null),
                    onBack = {},
                    onRetry = {},
                )
            }
        }
        composeRule.onNodeWithText("Chưa có quyết định từ Quản lý").assertDoesNotExist()
    }

    @Test
    fun success_state_shows_the_manager_decision_and_note_when_present() {
        composeRule.setContent {
            LuxMapTheme {
                PolePhotoResultScreen(
                    poleId = "P-0103",
                    position = 3,
                    state = successState(decision = "confirm", decisionNote = "Đã kiểm tra thực tế"),
                    onBack = {},
                    onRetry = {},
                )
            }
        }
        composeRule.onNodeWithText("Quản lý đã xác nhận").assertExists()
        composeRule.onNodeWithText("Đã kiểm tra thực tế", substring = true).assertExists()
        composeRule.onNodeWithText("Lúc ", substring = true).assertExists()
    }

    @Test
    fun success_state_shows_the_stale_banner_only_when_isStale_is_true() {
        composeRule.setContent {
            LuxMapTheme {
                PolePhotoResultScreen(
                    poleId = "P-0103",
                    position = 3,
                    state = successState(isStale = true),
                    onBack = {},
                    onRetry = {},
                )
            }
        }
        composeRule.onNodeWithText("Đang xem kết quả đã lưu trên máy, có thể chưa phải bản mới nhất").assertExists()
    }

    @Test
    fun empty_state_shows_the_ai_unavailable_message() {
        composeRule.setContent {
            LuxMapTheme {
                PolePhotoResultScreen(
                    poleId = "P-0103",
                    position = 3,
                    state = PolePhotoResultUiState.Empty,
                    onBack = {},
                    onRetry = {},
                )
            }
        }
        composeRule.onNodeWithText("AI chưa phân loại được ảnh này").assertExists()
    }

    @Test
    fun a_network_error_uses_screen_copy_and_retries() {
        var retries = 0
        composeRule.setContent {
            LuxMapTheme {
                PolePhotoResultScreen(
                    poleId = "P-0103",
                    position = 3,
                    state = PolePhotoResultUiState.Error("java.net.SocketTimeoutException", isNetworkError = true),
                    onBack = {},
                    onRetry = { retries++ },
                )
            }
        }
        composeRule.onNodeWithText("Không tải được kết quả AI").assertExists()
        composeRule.onNodeWithText("Kiểm tra kết nối rồi thử lại.").assertExists()
        composeRule.onNodeWithText("SocketTimeoutException", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Thử lại").performClick()
        assertEquals(1, retries)
    }

    @Test
    fun review_card_is_hidden_once_a_manager_decision_already_exists() {
        composeRule.setContent {
            LuxMapTheme {
                PolePhotoResultScreen(
                    poleId = "P-0103",
                    position = 3,
                    state = successState(decision = "confirm"),
                    onBack = {},
                    onRetry = {},
                )
            }
        }
        composeRule.onNodeWithText("Kỹ sư xác nhận nhãn").assertDoesNotExist()
    }

    @Test
    fun review_card_shows_even_when_the_ai_model_failed_outright() {
        composeRule.setContent {
            LuxMapTheme {
                PolePhotoResultScreen(
                    poleId = "P-0103",
                    position = 3,
                    state = successState(cvState = null, confidence = null),
                    onBack = {},
                    onRetry = {},
                )
            }
        }
        composeRule.onNodeWithText("AI không xử lý được. Hãy chọn trạng thái đèn.").assertExists()
        composeRule.onNodeWithTag("ai_verdict_label").assertTextEquals("Chưa có kết luận")
        composeRule.onNodeWithText("Kỹ sư xác nhận nhãn").performScrollTo().assertExists()
    }

    @Test
    fun review_card_does_not_require_a_note_when_agreeing_with_the_ai_label() {
        var submitted: Pair<String, String?>? = null
        composeRule.setContent {
            LuxMapTheme {
                PolePhotoResultScreen(
                    poleId = "P-0103",
                    position = 3,
                    state = successState(cvState = "on"),
                    onBack = {},
                    onRetry = {},
                    onSubmitReview = { finalCvState, reviewNote -> submitted = finalCvState to reviewNote },
                )
            }
        }
        choice("Đèn sáng").performScrollTo().performClick()
        composeRule.onNodeWithText("Xác nhận").performClick()
        assertEquals("on" to null, submitted)
    }

    // Found on a real device 2026-10-29: the button stayed enabled forever after a successful
    // submit, since selected/note are local drafts that never reset on their own - resubmitting
    // the exact same values was both pointless (server treats it as a no-op) and looked like the
    // first tap never registered.
    @Test
    fun review_card_disables_submit_right_after_saving_a_draft_with_no_further_edit() {
        composeRule.setContent {
            LuxMapTheme {
                PolePhotoResultScreen(
                    poleId = "P-0103",
                    position = 3,
                    state = successState(cvState = "on").copy(draftFinalCvState = "on", draftReviewNote = null),
                    onBack = {},
                    onRetry = {},
                    onSubmitReview = { _, _ -> },
                )
            }
        }
        composeRule.onNodeWithText("Xác nhận").assertIsNotEnabled()
        composeRule.onNodeWithText("Đã lưu, sẽ gửi khi Hoàn tất").assertExists()
    }

    @Test
    fun review_card_shows_no_saved_hint_when_there_is_no_draft_yet() {
        composeRule.setContent {
            LuxMapTheme {
                PolePhotoResultScreen(
                    poleId = "P-0103",
                    position = 3,
                    state = successState(cvState = "on"),
                    onBack = {},
                    onRetry = {},
                )
            }
        }
        composeRule.onNodeWithText("Đã lưu, sẽ gửi khi Hoàn tất").assertDoesNotExist()
    }

    @Test
    fun review_card_blocks_submit_until_a_note_is_given_for_a_label_different_from_ai() {
        var submitted = false
        composeRule.setContent {
            LuxMapTheme {
                PolePhotoResultScreen(
                    poleId = "P-0103",
                    position = 3,
                    state = successState(cvState = "on"),
                    onBack = {},
                    onRetry = {},
                    onSubmitReview = { _, _ -> submitted = true },
                )
            }
        }
        choice("Đèn tắt").performScrollTo().performClick()
        composeRule.onNodeWithText("Cần ghi chú vì khác với nhận định của AI").assertExists()
        composeRule.onNodeWithText("Xác nhận").assertIsNotEnabled()

        composeRule.onNodeWithText("Ghi chú", substring = true).performTextInput("Thấy đèn tắt khi kiểm tra")
        composeRule.onNodeWithText("Xác nhận").assertIsEnabled().performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(true, submitted)
    }

    @Test
    fun the_review_choice_is_a_radio_group() {
        composeRule.setContent {
            LuxMapTheme {
                PolePhotoResultScreen(
                    poleId = "P-0103",
                    position = 3,
                    state = successState(cvState = "on"),
                    onBack = {},
                    onRetry = {},
                )
            }
        }
        choice("Đèn sáng").performScrollTo().assertIsSelected()
        choice("Đèn tắt").performScrollTo().performClick().assertIsSelected()
        choice("Đèn sáng").assertIsNotSelected()
    }

    @Test
    fun only_the_submit_button_is_pinned() {
        composeRule.setContent {
            LuxMapTheme {
                PolePhotoResultScreen(
                    poleId = "P-0103",
                    position = 3,
                    state = successState(cvState = "on"),
                    onBack = {},
                    onRetry = {},
                )
            }
        }
        composeRule.onNodeWithText("Xác nhận").assertIsDisplayed()
        composeRule.onNodeWithText("Kỹ sư xác nhận nhãn").assertIsNotDisplayed()
    }

    @Test
    fun a_completed_survey_shows_the_result_read_only() {
        composeRule.setContent {
            LuxMapTheme {
                PolePhotoResultScreen(
                    poleId = "P-0103",
                    position = 3,
                    state = successState(cvState = "on"),
                    onBack = {},
                    onRetry = {},
                    isCompleted = true,
                )
            }
        }
        composeRule.onNodeWithTag("ai_verdict_label").assertExists()
        composeRule.onNodeWithText("Kỹ sư xác nhận nhãn").assertDoesNotExist()
        composeRule.onNodeWithText("Xác nhận").assertDoesNotExist()
        composeRule.onNodeWithTag("details_toggle").performScrollTo().performClick()
        composeRule.onNodeWithText("Chụp lại").assertDoesNotExist()
    }

    @Test
    fun tapping_the_photo_is_labelled_for_talkback() {
        composeRule.setContent {
            LuxMapTheme {
                PolePhotoResultScreen(
                    poleId = "P-0103",
                    position = 3,
                    state = successState(),
                    onBack = {},
                    onRetry = {},
                )
            }
        }
        composeRule
            .onNodeWithTag("photo_preview")
            .assert(
                SemanticsMatcher(
                    "zoom label",
                ) { it.config.getOrNull(SemanticsActions.OnClick)?.label == "Phóng to ảnh" },
            )
    }

    @Test
    fun a_fresh_server_label_replaces_an_untouched_draft_seed() {
        var state by mutableStateOf<PolePhotoResultUiState>(
            successState(cvState = "on").copy(finalCvState = "on", isStale = true),
        )
        composeRule.setContent {
            LuxMapTheme {
                PolePhotoResultScreen(poleId = "P-0103", position = 3, state = state, onBack = {}, onRetry = {})
            }
        }
        choice("Đèn sáng").performScrollTo().assertIsSelected()
        state = successState(cvState = "on").copy(finalCvState = "off", reviewNote = "Đèn tắt thật")
        composeRule.waitForIdle()
        choice("Đèn tắt").performScrollTo().assertIsSelected()
        composeRule.onNode(hasText("Xác nhận") and hasClickAction()).assertIsNotEnabled()
    }

    private fun showResult(luxValue: Double?) {
        composeRule.setContent {
            LuxMapTheme {
                PolePhotoResultScreen(
                    poleId = "P-0103",
                    position = 3,
                    state = successState(luxValue = luxValue),
                    onBack = {},
                    onRetry = {},
                )
            }
        }
    }

    @Test
    fun the_field_lux_is_shown_under_the_conclusion() {
        showResult(0.83)

        composeRule.onNodeWithTag("ai_result_lux", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("0,83 (tương đối)", useUnmergedTree = true).assertExists()
    }

    @Test
    fun no_field_lux_row_without_a_reading() {
        showResult(null)

        composeRule.onNodeWithTag("ai_result_lux", useUnmergedTree = true).assertDoesNotExist()
    }
}
