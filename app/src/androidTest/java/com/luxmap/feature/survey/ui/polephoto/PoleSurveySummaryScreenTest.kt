package com.luxmap.feature.survey.ui.polephoto

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.luxmap.core.theme.LuxMapTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

private fun labelled(
    poleId: String,
    observation: Observation,
    cvState: String?,
    aiStatus: String? = "available",
) = PoleListRowState(
    poleId = poleId,
    position = 1,
    fixtureStatus = "normal",
    serverPhotoId = null,
    syncStatus = null,
    observation = observation,
    filePath = "/data/$poleId.jpg",
    aiStatus = aiStatus,
    cvState = cvState,
    confidence = 0.87,
)

class PoleSurveySummaryScreenTest {
    @get:Rule val composeRule = createComposeRule()

    private fun show(
        poles: List<PoleListRowState>,
        onRetake: (String) -> Unit = {},
        onSubmit: () -> Unit = {},
        showSubmitted: Boolean = false,
        onSubmittedAcknowledged: () -> Unit = {},
    ) {
        composeRule.setContent {
            LuxMapTheme {
                PoleSurveySummaryScreen(
                    state = PoleListUiState.Success(poles),
                    onBack = {},
                    onRetake = onRetake,
                    onSubmit = onSubmit,
                    showSubmitted = showSubmitted,
                    onSubmittedAcknowledged = onSubmittedAcknowledged,
                )
            }
        }
    }

    @Test
    fun submitted_dialog_leaves_the_summary_only_after_acknowledged() {
        var acknowledged = 0
        show(
            listOf(labelled("POLE-1", Observation.ON, cvState = "on")),
            showSubmitted = true,
            onSubmittedAcknowledged = { acknowledged++ },
        )
        composeRule.onNodeWithText("Đã nộp báo cáo").assertExists()
        assertEquals(0, acknowledged)
        composeRule.onNodeWithTag("submitted_dialog_confirm").performClick()
        assertEquals(1, acknowledged)
    }

    @Test
    fun no_submitted_dialog_before_sending() {
        show(listOf(labelled("POLE-1", Observation.ON, cvState = "on")))
        composeRule.onNodeWithTag("submitted_dialog").assertDoesNotExist()
    }

    @Test
    fun only_disagreements_are_listed_and_keeping_the_label_never_adopts_the_ai() {
        var retaken: String? = null
        show(
            listOf(
                labelled("POLE-1", Observation.OFF, cvState = "on"),
                labelled("POLE-2", Observation.ON, cvState = "on"),
            ),
            onRetake = { retaken = it },
        )
        composeRule.onNodeWithText("AI thấy khác bạn ở 1 cột").assertExists()
        composeRule.onNodeWithText("AI: Đèn sáng (87%)").assertExists()
        composeRule.onNodeWithTag("mismatch_POLE-2").assertDoesNotExist()
        composeRule.onNodeWithText("Chụp lại").performScrollTo().performClick()
        assertEquals("POLE-1", retaken)
        composeRule.onNodeWithText("Giữ nhãn của tôi").performScrollTo().performClick()
        composeRule.onNodeWithText("Đã giữ nhãn của bạn").assertExists()
        composeRule.onNodeWithText("Bạn: Tắt").assertExists()
    }

    @Test
    fun photos_the_ai_has_not_read_yet_do_not_block_sending() {
        var submitted = false
        show(
            listOf(labelled("POLE-1", Observation.ON, cvState = null, aiStatus = null)),
            onSubmit = { submitted = true },
        )
        // Nothing was read yet, so there is no agreement to claim.
        composeRule.onNodeWithText("AI đồng ý với bạn ở tất cả ảnh đã đọc.").assertDoesNotExist()
        composeRule.onNodeWithText("Gửi khảo sát").performClick()
        composeRule.onNodeWithTag("confirm_survey_complete").performClick()
        assertEquals(true, submitted)
    }

    @Test
    fun a_route_with_every_pole_skipped_shows_no_ai_comparison() {
        show(
            listOf(
                PoleListRowState(
                    poleId = "POLE-1",
                    position = 1,
                    fixtureStatus = "normal",
                    serverPhotoId = null,
                    syncStatus = null,
                    skipReason = "Không tiếp cận được",
                ),
            ),
        )
        composeRule.onNodeWithText("AI thấy khác bạn", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("AI đồng ý với bạn", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Gửi khảo sát").assertExists()
    }
}
