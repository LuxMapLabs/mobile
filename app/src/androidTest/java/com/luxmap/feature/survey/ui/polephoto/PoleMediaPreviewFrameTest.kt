package com.luxmap.feature.survey.ui.polephoto

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso
import com.luxmap.core.theme.LuxMapTheme
import com.luxmap.core.ui.components.assertTouchTargetIsAtLeast
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PoleMediaPreviewFrameTest {
    @get:Rule val composeRule = createComposeRule()

    private fun show(
        isSaving: Boolean = false,
        confirmEnabled: Boolean = true,
        warning: String? = null,
        onClose: () -> Unit = {},
        onRetake: () -> Unit = {},
        onConfirm: () -> Unit = {},
    ) {
        composeRule.setContent {
            LuxMapTheme(forceDark = true) {
                PoleMediaPreviewFrame(
                    poleId = "POLE-0047",
                    isSaving = isSaving,
                    retakeLabel = "Chụp lại",
                    confirmLabel = "Dùng ảnh này",
                    onClose = onClose,
                    onRetake = onRetake,
                    onConfirm = onConfirm,
                    confirmEnabled = confirmEnabled,
                    warning = warning,
                ) {}
            }
        }
    }

    @Test
    fun saving_disables_every_action_and_close_keeps_a_48dp_target() {
        show(isSaving = true)
        composeRule.onNodeWithContentDescription("Đóng").assertIsNotEnabled().assertTouchTargetIsAtLeast(48.dp)
        composeRule.onNodeWithText("Chụp lại").assertIsNotEnabled()
        composeRule.onNode(hasText("Đang lưu…") and hasClickAction()).assertIsNotEnabled()
        composeRule.onNodeWithText("POLE-0047").assertExists()
    }

    @Test
    fun preview_actions_have_equal_size() {
        show()
        val retake = composeRule.onNode(hasText("Chụp lại") and hasClickAction()).fetchSemanticsNode()
        val confirm = composeRule.onNode(hasText("Dùng ảnh này") and hasClickAction()).fetchSemanticsNode()
        assertEquals(retake.size.height, confirm.size.height)
        assertEquals(retake.size.width.toDouble(), confirm.size.width.toDouble(), 1.0)
    }

    @Test
    fun choose_another_video_stays_on_one_line_on_a_narrow_screen_at_large_font() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                LuxMapTheme {
                    Box(Modifier.width(320.dp)) {
                        PoleMediaPreviewFrame(
                            poleId = "POLE-0047",
                            isSaving = false,
                            retakeLabel = "Chọn video khác",
                            confirmLabel = "Dùng video này",
                            onClose = {},
                            onRetake = {},
                            onConfirm = {},
                            keepRetakeLabelOnOneLine = true,
                        ) {}
                    }
                }
            }
        }
        val layouts = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithText("Chọn video khác", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(1, layouts.single().lineCount)
    }

    @Test
    fun idle_actions_call_their_callbacks() {
        var closed = 0
        var retaken = 0
        var confirmed = 0
        show(onClose = { closed++ }, onRetake = { retaken++ }, onConfirm = { confirmed++ })
        composeRule.onNodeWithContentDescription("Đóng").performClick()
        composeRule.onNodeWithText("Chụp lại").performClick()
        composeRule.onNodeWithText("Dùng ảnh này").performClick()
        assertEquals(listOf(1, 1, 1), listOf(closed, retaken, confirmed))
    }

    @Test
    fun an_invalid_clip_shows_the_warning_and_blocks_confirm() {
        show(confirmEnabled = false, warning = "Clip quá ngắn")
        composeRule.onNodeWithText("Clip quá ngắn").assertExists()
        composeRule.onNode(hasText("Dùng ảnh này") and hasClickAction()).assertIsNotEnabled()
    }

    private fun showWithOuterBack(
        isSaving: Boolean,
        onClose: () -> Unit,
        onOuterBack: () -> Unit,
    ) {
        composeRule.setContent {
            BackHandler { onOuterBack() }
            LuxMapTheme(forceDark = true) {
                PoleMediaPreviewFrame("POLE-0047", isSaving, "Chụp lại", "Dùng ảnh này", onClose, {}, {}) {}
            }
        }
    }

    @Test
    fun system_back_while_saving_does_nothing() {
        var closed = 0
        var outer = 0
        showWithOuterBack(isSaving = true, onClose = { closed++ }, onOuterBack = { outer++ })
        Espresso.pressBack()
        composeRule.waitForIdle()
        assertEquals(listOf(0, 0), listOf(closed, outer))
    }

    @Test
    fun system_back_when_idle_closes_the_preview() {
        var closed = 0
        var outer = 0
        showWithOuterBack(isSaving = false, onClose = { closed++ }, onOuterBack = { outer++ })
        Espresso.pressBack()
        composeRule.waitForIdle()
        assertEquals(listOf(1, 0), listOf(closed, outer))
    }
}
