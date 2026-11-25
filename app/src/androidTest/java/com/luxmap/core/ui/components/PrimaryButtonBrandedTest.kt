package com.luxmap.core.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.luxmap.core.theme.LuxMapTheme
import com.luxmap.core.theme.LuxTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test

class PrimaryButtonBrandedTest {
    @get:Rule val composeRule = createComposeRule()

    private fun ImageBitmap.edgePixel(): Color {
        val map = toPixelMap()
        return map[map.width / 10, map.height / 2]
    }

    @Test
    fun an_enabled_branded_button_is_filled_with_lumen() {
        var lumen = Color.Unspecified
        composeRule.setContent {
            LuxMapTheme {
                lumen = LuxTheme.brand.lumen
                PrimaryButton(text = "Lưu báo cáo", onClick = {}, branded = true)
            }
        }
        val pixel = composeRule.onNodeWithText("Lưu báo cáo").captureToImage().edgePixel()
        assertEquals(lumen.toArgb(), pixel.toArgb())
    }

    @Test
    fun a_locked_branded_button_stays_grey_not_lumen() {
        var lumen = Color.Unspecified
        var disabled = Color.Unspecified
        composeRule.setContent {
            LuxMapTheme {
                lumen = LuxTheme.brand.lumen
                disabled = LuxTheme.colors.disabledContainer
                PrimaryButton(text = "Lưu báo cáo", onClick = {}, enabled = false, branded = true)
            }
        }
        val pixel = composeRule.onNodeWithText("Lưu báo cáo").captureToImage().edgePixel()
        assertNotEquals(lumen.toArgb(), pixel.toArgb())
        assertEquals(disabled.toArgb(), pixel.toArgb())
    }

    @Test
    fun a_plain_button_keeps_the_primary_colour() {
        var primary = Color.Unspecified
        composeRule.setContent {
            LuxMapTheme {
                primary = androidx.compose.material3.MaterialTheme.colorScheme.primary
                PrimaryButton(text = "Đăng nhập", onClick = {})
            }
        }
        val pixel = composeRule.onNodeWithText("Đăng nhập").captureToImage().edgePixel()
        assertEquals(primary.toArgb(), pixel.toArgb())
    }
}
