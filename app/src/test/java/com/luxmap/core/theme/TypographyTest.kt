package com.luxmap.core.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TypographyTest {
    private val slots: Map<String, TextStyle> =
        with(Typography) {
            mapOf(
                "displayLarge" to displayLarge,
                "displayMedium" to displayMedium,
                "displaySmall" to displaySmall,
                "headlineLarge" to headlineLarge,
                "headlineMedium" to headlineMedium,
                "headlineSmall" to headlineSmall,
                "titleLarge" to titleLarge,
                "titleMedium" to titleMedium,
                "titleSmall" to titleSmall,
                "bodyLarge" to bodyLarge,
                "bodyMedium" to bodyMedium,
                "bodySmall" to bodySmall,
                "labelLarge" to labelLarge,
                "labelMedium" to labelMedium,
                "labelSmall" to labelSmall,
            )
        }

    @Test
    fun `all fifteen slots use Be Vietnam Pro`() {
        assertEquals(15, slots.size)
        slots.forEach { (name, style) -> assertEquals(name, BeVietnamPro, style.fontFamily) }
    }

    @Test
    fun `no slot is smaller than 14sp`() {
        slots.forEach { (name, style) -> assertTrue("$name was ${style.fontSize}", style.fontSize.value >= 14f) }
    }

    @Test
    fun `reading and action slots are at least 16sp`() {
        listOf("titleSmall", "bodyLarge", "bodyMedium", "labelLarge").forEach { name ->
            assertTrue("$name too small", slots.getValue(name).fontSize.value >= 16f)
        }
    }

    @Test
    fun `text slots leave room for stacked Vietnamese diacritics`() {
        slots.filterKeys { !it.startsWith("display") }.forEach { (name, style) ->
            val ratio = style.lineHeight.value / style.fontSize.value
            assertTrue("$name line height ratio was $ratio", ratio >= 1.35f)
        }
    }

    @Test
    fun `legacy badge and overline styles follow labelMedium instead of 13sp`() {
        assertEquals(Typography.labelMedium, BadgeTextStyle)
        assertEquals(Typography.labelMedium, OverlineTextStyle)
    }

    @Test
    fun `no slot trims the line box or tightens letter spacing`() {
        slots.forEach { (name, style) ->
            assertEquals(name, LineHeightStyle.Trim.None, style.lineHeightStyle?.trim)
            assertEquals(name, 0.sp, style.letterSpacing)
        }
    }
}
