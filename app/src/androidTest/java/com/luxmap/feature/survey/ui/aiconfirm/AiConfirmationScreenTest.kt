package com.luxmap.feature.survey.ui.aiconfirm

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.luxmap.core.theme.AssetCondition
import com.luxmap.core.theme.LuxMapTheme
import com.luxmap.core.theme.label
import com.luxmap.feature.survey.data.LocalAiClassificationReview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private fun review(
    reviewId: String = "1",
    aiClass: AssetCondition = AssetCondition.NORMAL,
    finalClass: AssetCondition = aiClass,
    aiConfidencePercent: Int = 95,
    overrideReason: String? = null,
) = LocalAiClassificationReview(
    reviewId = reviewId,
    surveySweepId = "SWEEP-1",
    poleId = "POLE-010$reviewId",
    distanceKm = 1.2,
    frameRef = "Khung hình #0$reviewId",
    aiClass = aiClass,
    aiConfidencePercent = aiConfidencePercent,
    finalClass = finalClass,
    overrideReason = overrideReason,
)

class AiConfirmationScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun loading_state_shows_progress_indicator() {
        composeRule.setContent {
            LuxMapTheme {
                AiConfirmationScreen(
                    surveySweepId = "SWEEP-1",
                    uiState = AiConfirmationUiState.Loading,
                    sheet = null,
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithTag("ai_confirmation_loading").assertExists()
    }

    @Test
    fun success_state_shows_the_pole_id_and_condition_counts() {
        composeRule.setContent {
            LuxMapTheme {
                AiConfirmationScreen(
                    surveySweepId = "SWEEP-1",
                    uiState =
                        AiConfirmationUiState.Success(
                            reviews = listOf(review()),
                            filter = ReviewFilter.ALL,
                            normalCount = 1,
                            dimCount = 0,
                            outCount = 0,
                        ),
                    sheet = null,
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("POLE-0101").assertExists()
        composeRule.onNodeWithText("Bình thường 1").assertExists()
    }

    @Test
    fun a_review_below_85_percent_confidence_shows_the_low_confidence_label() {
        composeRule.setContent {
            LuxMapTheme {
                AiConfirmationScreen(
                    surveySweepId = "SWEEP-1",
                    uiState =
                        AiConfirmationUiState.Success(
                            reviews = listOf(review(aiConfidencePercent = 61)),
                            filter = ReviewFilter.ALL,
                            normalCount = 1,
                            dimCount = 0,
                            outCount = 0,
                        ),
                    sheet = null,
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("tin cậy thấp", substring = true).assertExists()
    }

    @Test
    fun a_review_at_85_percent_confidence_or_above_does_not_show_the_low_confidence_label() {
        composeRule.setContent {
            LuxMapTheme {
                AiConfirmationScreen(
                    surveySweepId = "SWEEP-1",
                    uiState =
                        AiConfirmationUiState.Success(
                            reviews = listOf(review(aiConfidencePercent = 85)),
                            filter = ReviewFilter.ALL,
                            normalCount = 1,
                            dimCount = 0,
                            outCount = 0,
                        ),
                    sheet = null,
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("tin cậy thấp", substring = true).assertDoesNotExist()
    }

    @Test
    fun an_overridden_review_shows_the_overridden_from_label() {
        composeRule.setContent {
            LuxMapTheme {
                AiConfirmationScreen(
                    surveySweepId = "SWEEP-1",
                    uiState =
                        AiConfirmationUiState.Success(
                            reviews =
                                listOf(
                                    review(
                                        aiClass = AssetCondition.NORMAL,
                                        finalClass = AssetCondition.DIM,
                                        overrideReason = "sửa",
                                    ),
                                ),
                            filter = ReviewFilter.ALL,
                            normalCount = 0,
                            dimCount = 1,
                            outCount = 0,
                        ),
                    sheet = null,
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("đã sửa từ Bình thường", substring = true).assertExists()
    }

    @Test
    fun tapping_a_review_row_invokes_onReviewSelected_with_that_review() {
        var selected: LocalAiClassificationReview? = null
        val target = review()
        composeRule.setContent {
            LuxMapTheme {
                AiConfirmationScreen(
                    surveySweepId = "SWEEP-1",
                    uiState =
                        AiConfirmationUiState.Success(
                            reviews = listOf(target),
                            filter = ReviewFilter.ALL,
                            normalCount = 1,
                            dimCount = 0,
                            outCount = 0,
                        ),
                    sheet = null,
                    onBack = {},
                    onReviewSelected = { selected = it },
                )
            }
        }
        composeRule.onNodeWithText("POLE-0101").performClick()
        assertEquals(target, selected)
    }

    @Test
    fun empty_state_shows_the_no_reviews_message() {
        composeRule.setContent {
            LuxMapTheme {
                AiConfirmationScreen(
                    surveySweepId = "SWEEP-1",
                    uiState = AiConfirmationUiState.Empty,
                    sheet = null,
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("Chưa có kết quả AI", substring = true).assertExists()
    }

    @Test
    fun error_state_shows_the_message() {
        composeRule.setContent {
            LuxMapTheme {
                AiConfirmationScreen(
                    surveySweepId = "SWEEP-1",
                    uiState = AiConfirmationUiState.Error("Không tải được kết quả AI"),
                    sheet = null,
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("Không tải được kết quả AI").assertExists()
    }

    @Test
    fun error_state_shows_a_retry_button_that_invokes_onRetry() {
        var retried = false
        composeRule.setContent {
            LuxMapTheme {
                AiConfirmationScreen(
                    surveySweepId = "SWEEP-1",
                    uiState = AiConfirmationUiState.Error("Không tải được kết quả AI"),
                    sheet = null,
                    onBack = {},
                    onRetry = { retried = true },
                )
            }
        }
        composeRule.onNodeWithText("Thử lại").performClick()
        assertTrue(retried)
    }

    @Test
    fun a_filter_with_no_matching_reviews_shows_an_inline_empty_message_instead_of_a_blank_list() {
        composeRule.setContent {
            LuxMapTheme {
                AiConfirmationScreen(
                    surveySweepId = "SWEEP-1",
                    uiState =
                        AiConfirmationUiState.Success(
                            reviews = emptyList(),
                            filter = ReviewFilter.OVERRIDDEN,
                            normalCount = 1,
                            dimCount = 0,
                            outCount = 0,
                        ),
                    sheet = null,
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("Không có kết quả khớp bộ lọc này").assertExists()
        composeRule.onNodeWithText("Đã sửa").assertExists()
    }

    @Test
    fun the_sheet_shows_Luu_thay_doi_disabled_when_a_different_class_is_chosen_without_a_reason() {
        val target = review(aiClass = AssetCondition.NORMAL)
        composeRule.setContent {
            LuxMapTheme {
                AiConfirmationScreen(
                    surveySweepId = "SWEEP-1",
                    uiState = AiConfirmationUiState.Empty,
                    sheet = AiReviewSheetState(review = target, chosenClass = AssetCondition.OUT, reason = ""),
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("Lưu thay đổi").assertIsNotEnabled()
    }

    @Test
    fun the_sheet_shows_Luu_thay_doi_enabled_once_a_reason_is_entered() {
        val target = review(aiClass = AssetCondition.NORMAL)
        composeRule.setContent {
            LuxMapTheme {
                AiConfirmationScreen(
                    surveySweepId = "SWEEP-1",
                    uiState = AiConfirmationUiState.Empty,
                    sheet = AiReviewSheetState(review = target, chosenClass = AssetCondition.OUT, reason = "tắt hẳn"),
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("Lưu thay đổi").assertIsEnabled()
    }

    @Test
    fun the_sheet_shows_Giu_ket_qua_AI_and_stays_enabled_when_the_ai_class_itself_is_chosen() {
        val target = review(aiClass = AssetCondition.NORMAL, finalClass = AssetCondition.OUT, overrideReason = "x")
        composeRule.setContent {
            LuxMapTheme {
                AiConfirmationScreen(
                    surveySweepId = "SWEEP-1",
                    uiState = AiConfirmationUiState.Empty,
                    sheet = AiReviewSheetState(review = target, chosenClass = AssetCondition.NORMAL, reason = "x"),
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("Giữ kết quả AI").assertIsEnabled()
    }

    @Test
    fun choosing_a_condition_in_the_grid_invokes_onConditionChosen() {
        var chosen: AssetCondition? = null
        val target = review(aiClass = AssetCondition.NORMAL)
        composeRule.setContent {
            LuxMapTheme {
                AiConfirmationScreen(
                    surveySweepId = "SWEEP-1",
                    uiState = AiConfirmationUiState.Empty,
                    sheet = AiReviewSheetState(review = target, chosenClass = AssetCondition.NORMAL, reason = ""),
                    onBack = {},
                    onConditionChosen = { chosen = it },
                )
            }
        }
        composeRule.onNodeWithText(AssetCondition.OUT.label()).performClick()
        assertEquals(AssetCondition.OUT, chosen)
    }

    @Test
    fun confirming_the_submit_dialog_invokes_onSubmit_and_shows_the_saved_toast() {
        var submitted = false
        composeRule.setContent {
            LuxMapTheme {
                AiConfirmationScreen(
                    surveySweepId = "SWEEP-1",
                    uiState =
                        AiConfirmationUiState.Success(
                            reviews = listOf(review()),
                            filter = ReviewFilter.ALL,
                            normalCount = 1,
                            dimCount = 0,
                            outCount = 0,
                        ),
                    sheet = null,
                    onBack = {},
                    onSubmit = { submitted = true },
                )
            }
        }
        composeRule.onNodeWithText("Gửi").performClick()
        composeRule.onNodeWithText("Gửi kết quả xác nhận?").assertExists()
        composeRule.onAllNodesWithText("Gửi")[1].performClick()
        assertTrue(submitted)
        composeRule.onNodeWithText("Đã lưu trên thiết bị · sẽ gửi khi có mạng").assertExists()
    }
}
