package com.luxmap.core.theme

import org.junit.Assert.assertTrue
import org.junit.Test

class SemanticTextContrastTest {
    @Test
    fun `light secondary text clears AAA on the page background`() {
        val ratio =
            contrastRatio(
                LuxMapLightColorScheme.onSurfaceVariant,
                LuxMapLightColorScheme.background,
            )
        assertTrue("contrast was $ratio", ratio >= 7.0)
    }

    @Test
    fun `light secondary text clears AAA on a card surface`() {
        val ratio =
            contrastRatio(
                LuxMapLightColorScheme.onSurfaceVariant,
                LuxMapLightColorScheme.surface,
            )
        assertTrue("contrast was $ratio", ratio >= 7.0)
    }

    @Test
    fun `dark secondary text clears AAA on the page background`() {
        val ratio =
            contrastRatio(
                LuxMapDarkColorScheme.onSurfaceVariant,
                LuxMapDarkColorScheme.background,
            )
        assertTrue("contrast was $ratio", ratio >= 7.0)
    }

    @Test
    fun `dark secondary text clears AAA on a card surface`() {
        val ratio =
            contrastRatio(
                LuxMapDarkColorScheme.onSurfaceVariant,
                LuxMapDarkColorScheme.surface,
            )
        assertTrue("contrast was $ratio", ratio >= 7.0)
    }

    @Test
    fun `primary text clears AAA in both themes`() {
        val light = contrastRatio(LuxMapLightColorScheme.onSurface, LuxMapLightColorScheme.surface)
        val dark = contrastRatio(LuxMapDarkColorScheme.onSurface, LuxMapDarkColorScheme.surface)
        assertTrue("light was $light", light >= 7.0)
        assertTrue("dark was $dark", dark >= 7.0)
    }
}
