package com.luxmap.feature.survey.ui.polephoto

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.luxmap.core.theme.LuxMapTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.Instant

private fun successState(
    cvState: String? = "on",
    confidence: Double? = 0.92,
    decision: String? = null,
    decisionNote: String? = null,
    isStale: Boolean = false,
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
)

class PolePhotoResultBottomSheetTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun loading_state_shows_the_skeleton() {
        composeRule.setContent {
            LuxMapTheme {
                PolePhotoResultBottomSheet(
                    poleId = "P-0103",
                    position = 3,
                    state = PolePhotoResultUiState.Loading,
                    onDismiss = {},
                    onRetry = {},
                )
            }
        }
        composeRule.onNodeWithTag("pole_photo_result_loading").assertExists()
    }

    @Test
    fun header_shows_the_pole_id_and_the_position_badge_in_every_state() {
        composeRule.setContent {
            LuxMapTheme {
                PolePhotoResultBottomSheet(
                    poleId = "P-0103",
                    position = 3,
                    state = PolePhotoResultUiState.Loading,
                    onDismiss = {},
                    onRetry = {},
                )
            }
        }
        composeRule.onNodeWithText("P-0103").assertExists()
        composeRule.onNodeWithText("#3").assertExists()
    }

    @Test
    fun closing_the_header_button_invokes_onDismiss() {
        var dismissed = false
        composeRule.setContent {
            LuxMapTheme {
                PolePhotoResultBottomSheet(
                    poleId = "P-0103",
                    position = 3,
                    state = PolePhotoResultUiState.Empty,
                    onDismiss = { dismissed = true },
                    onRetry = {},
                )
            }
        }
        composeRule.onNodeWithContentDescription("Đóng").performClick()
        assertEquals(true, dismissed)
    }

    @Test
    fun success_state_tapping_chup_lai_invokes_onRetake() {
        var retaken = false
        composeRule.setContent {
            LuxMapTheme {
                PolePhotoResultBottomSheet(
                    poleId = "P-0103",
                    position = 3,
                    state = successState(),
                    onDismiss = {},
                    onRetry = {},
                    onRetake = { retaken = true },
                )
            }
        }
        composeRule.onNodeWithText("Chụp lại cột này").performScrollTo().performClick()
        assertEquals(true, retaken)
    }

    @Test
    fun success_state_shows_the_cv_state_label_and_confidence() {
        composeRule.setContent {
            LuxMapTheme {
                PolePhotoResultBottomSheet(
                    poleId = "P-0103",
                    position = 3,
                    state = successState(cvState = "on", confidence = 0.92),
                    onDismiss = {},
                    onRetry = {},
                )
            }
        }
        // Tagged, not plain text - ReviewCard's own "Đèn sáng" chip renders at the same time.
        composeRule.onNodeWithTag("ai_verdict_label").assertTextEquals("Đèn sáng")
        composeRule.onNodeWithText("Tin cậy 92%").assertExists()
    }

    @Test
    fun success_state_shows_no_decision_yet_when_decision_is_null() {
        composeRule.setContent {
            LuxMapTheme {
                PolePhotoResultBottomSheet(
                    poleId = "P-0103",
                    position = 3,
                    state = successState(decision = null),
                    onDismiss = {},
                    onRetry = {},
                )
            }
        }
        composeRule.onNodeWithText("Chưa có quyết định từ Kỹ sư bảo trì").assertExists()
    }

    @Test
    fun success_state_shows_the_manager_decision_and_note_when_present() {
        composeRule.setContent {
            LuxMapTheme {
                PolePhotoResultBottomSheet(
                    poleId = "P-0103",
                    position = 3,
                    state = successState(decision = "confirm", decisionNote = "Đã kiểm tra thực tế"),
                    onDismiss = {},
                    onRetry = {},
                )
            }
        }
        composeRule.onNodeWithText("Kỹ sư bảo trì đã xác nhận").assertExists()
        composeRule.onNodeWithText("Đã kiểm tra thực tế").assertExists()
    }

    @Test
    fun success_state_shows_the_stale_banner_only_when_isStale_is_true() {
        composeRule.setContent {
            LuxMapTheme {
                PolePhotoResultBottomSheet(
                    poleId = "P-0103",
                    position = 3,
                    state = successState(isStale = true),
                    onDismiss = {},
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
                PolePhotoResultBottomSheet(
                    poleId = "P-0103",
                    position = 3,
                    state = PolePhotoResultUiState.Empty,
                    onDismiss = {},
                    onRetry = {},
                )
            }
        }
        composeRule.onNodeWithText("AI chưa phân loại được ảnh này").assertExists()
    }

    @Test
    fun error_state_shows_the_message_and_retry_invokes_onRetry() {
        var retried = false
        composeRule.setContent {
            LuxMapTheme {
                PolePhotoResultBottomSheet(
                    poleId = "P-0103",
                    position = 3,
                    state = PolePhotoResultUiState.Error("network down"),
                    onDismiss = {},
                    onRetry = { retried = true },
                )
            }
        }
        composeRule.onNodeWithText("network down").assertExists()
        composeRule.onNodeWithText("Thử lại").performClick()
        assertEquals(true, retried)
    }

    @Test
    fun review_card_is_hidden_once_a_manager_decision_already_exists() {
        composeRule.setContent {
            LuxMapTheme {
                PolePhotoResultBottomSheet(
                    poleId = "P-0103",
                    position = 3,
                    state = successState(decision = "confirm"),
                    onDismiss = {},
                    onRetry = {},
                )
            }
        }
        composeRule.onNodeWithText("KỸ SƯ XÁC NHẬN NHÃN").assertDoesNotExist()
    }

    @Test
    fun review_card_shows_even_when_the_ai_model_failed_outright() {
        composeRule.setContent {
            LuxMapTheme {
                PolePhotoResultBottomSheet(
                    poleId = "P-0103",
                    position = 3,
                    state = successState(cvState = null, confidence = null),
                    onDismiss = {},
                    onRetry = {},
                )
            }
        }
        composeRule.onNodeWithText("AI không xử lý được").assertExists()
        composeRule.onNodeWithText("KỸ SƯ XÁC NHẬN NHÃN").assertExists()
    }

    @Test
    fun review_card_does_not_require_a_note_when_agreeing_with_the_ai_label() {
        var submitted: Pair<String, String?>? = null
        composeRule.setContent {
            LuxMapTheme {
                PolePhotoResultBottomSheet(
                    poleId = "P-0103",
                    position = 3,
                    state = successState(cvState = "on"),
                    onDismiss = {},
                    onRetry = {},
                    onSubmitReview = { finalCvState, reviewNote -> submitted = finalCvState to reviewNote },
                )
            }
        }
        composeRule.onNodeWithTag("review_choice_on").performScrollTo().performClick()
        composeRule.onNodeWithText("Gửi xác nhận").performScrollTo().performClick()
        assertEquals("on" to null, submitted)
    }

    // Found on a real device 2026-10-29: the button stayed enabled forever after a successful
    // submit, since selected/note are local drafts that never reset on their own - resubmitting
    // the exact same values was both pointless (server treats it as a no-op) and looked like the
    // first tap never registered.
    @Test
    fun review_card_disables_submit_right_after_a_successful_submit_with_no_further_edit() {
        composeRule.setContent {
            LuxMapTheme {
                PolePhotoResultBottomSheet(
                    poleId = "P-0103",
                    position = 3,
                    state = successState(cvState = "on").copy(finalCvState = "on", reviewNote = null),
                    onDismiss = {},
                    onRetry = {},
                    onSubmitReview = { _, _ -> },
                )
            }
        }
        composeRule.onNodeWithText("Gửi xác nhận").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun review_card_blocks_submit_until_a_note_is_given_for_a_label_different_from_ai() {
        var submitted = false
        composeRule.setContent {
            LuxMapTheme {
                PolePhotoResultBottomSheet(
                    poleId = "P-0103",
                    position = 3,
                    state = successState(cvState = "on"),
                    onDismiss = {},
                    onRetry = {},
                    onSubmitReview = { _, _ -> submitted = true },
                )
            }
        }
        composeRule.onNodeWithTag("review_choice_off").performScrollTo().performClick()
        composeRule.onNodeWithText("Cần ghi chú vì khác với nhận định của AI").assertExists()
        composeRule.onNodeWithText("Gửi xác nhận").assertIsNotEnabled()

        composeRule.onNodeWithText("Ghi chú").performTextInput("Thấy đèn tắt khi kiểm tra")
        composeRule.onNodeWithText("Gửi xác nhận").performScrollTo().performClick()
        assertEquals(true, submitted)
    }

    @Test
    fun review_card_shows_a_message_when_the_submission_fails() {
        composeRule.setContent {
            LuxMapTheme {
                PolePhotoResultBottomSheet(
                    poleId = "P-0103",
                    position = 3,
                    state =
                        successState(cvState = "on").copy(
                            reviewError = "Ảnh này đã được Kỹ sư bảo trì công bố, không thể sửa nhãn nữa",
                        ),
                    onDismiss = {},
                    onRetry = {},
                )
            }
        }
        composeRule.onNodeWithText("Ảnh này đã được Kỹ sư bảo trì công bố, không thể sửa nhãn nữa").assertExists()
    }
}
