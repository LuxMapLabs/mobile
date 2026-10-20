package com.luxmap.feature.survey.ui.polephoto

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.luxmap.core.theme.LuxMapTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.Instant

private fun successState(
    cvState: String = "on",
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
        composeRule.onNodeWithText("Chụp lại cột này").performClick()
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
        composeRule.onNodeWithText("Đèn sáng").assertExists()
        composeRule.onNodeWithText("Độ tin cậy 92%").assertExists()
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
}
