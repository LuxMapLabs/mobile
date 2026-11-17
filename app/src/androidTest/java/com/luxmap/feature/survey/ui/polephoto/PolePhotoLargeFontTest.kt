package com.luxmap.feature.survey.ui.polephoto

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.luxmap.core.theme.LuxMapTheme
import com.luxmap.core.theme.SyncStatus
import com.luxmap.core.ui.components.assertTouchTargetIsAtLeast
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class PolePhotoLargeFontTest {
    @get:Rule val composeRule = createComposeRule()

    private fun large(content: @Composable () -> Unit) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                LuxMapTheme {
                    Box(modifier = Modifier.width(360.dp)) { content() }
                }
            }
        }
    }

    @Test
    fun pole_rows_and_the_complete_button_keep_48dp_targets() {
        large {
            PoleListScreen(
                workOrderId = "WO-2026-000123",
                state =
                    PoleListUiState.Success(
                        poles =
                            listOf(
                                PoleListRowState("POLE-XA-PHUOC-HIEP-0047", 1, "normal", "S-1", SyncStatus.DONE)
                                    .copy(aiVerdict = PoleAiVerdict.INCONCLUSIVE),
                                PoleListRowState("POLE-XA-PHUOC-HIEP-0048", 2, "normal", null, SyncStatus.FAILED),
                            ),
                    ),
                onCapturePole = { _, _ -> },
                onBack = {},
            )
        }
        composeRule
            .onNode(hasClickAction() and hasText("POLE-XA-PHUOC-HIEP-0047", substring = true))
            .assertTouchTargetIsAtLeast(48.dp)
        composeRule.onNodeWithText("Đồng bộ lỗi").assertIsDisplayed()
        composeRule.onNode(hasText("Hoàn tất") and hasClickAction()).assertTouchTargetIsAtLeast(48.dp)
    }

    @Test
    fun the_review_choice_and_pinned_submit_keep_48dp_targets() {
        large {
            PolePhotoResultScreen(
                poleId = "POLE-XA-PHUOC-HIEP-0047",
                position = 12,
                state =
                    PolePhotoResultUiState.Success(
                        filePath = "/data/pole.jpg",
                        cvState = "off",
                        confidence = 0.62,
                        modelVersion = "cv-v1",
                        analyzedAt = Instant.parse("2026-11-17T03:00:00Z"),
                        decision = null,
                        decisionNote = null,
                        decidedBy = null,
                        decidedAt = null,
                    ),
                onBack = {},
                onRetry = {},
            )
        }
        composeRule
            .onNode(hasText("Đèn sáng") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            .performScrollTo()
            .assertTouchTargetIsAtLeast(48.dp)
        composeRule.onNode(
            hasText("Đồng ý với AI") and hasClickAction(),
        ).assertIsDisplayed().assertTouchTargetIsAtLeast(48.dp)
    }
}
