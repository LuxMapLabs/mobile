package com.luxmap.core.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorSchemeContrastTest {
    private val themes = listOf("light" to LuxMapLightColorScheme, "night" to LuxMapDarkColorScheme)

    private fun assertContrast(
        theme: String,
        pair: String,
        foreground: Color,
        background: Color,
        minimum: Double,
    ) {
        val ratio = contrastRatio(foreground, background)
        assertTrue("$theme $pair was $ratio, needs $minimum", ratio >= minimum)
    }

    private fun ColorScheme.surfaces(): Map<String, Color> =
        mapOf(
            "background" to background,
            "surface" to surface,
            "surfaceContainerLowest" to surfaceContainerLowest,
            "surfaceContainerLow" to surfaceContainerLow,
            "surfaceContainer" to surfaceContainer,
            "surfaceContainerHigh" to surfaceContainerHigh,
            "surfaceContainerHighest" to surfaceContainerHighest,
        )

    @Test
    fun `primary content and CTA text reach AAA`() {
        themes.forEach { (name, scheme) ->
            assertContrast(name, "onPrimary/primary", scheme.onPrimary, scheme.primary, 7.0)
            assertContrast(name, "onSurface/surface", scheme.onSurface, scheme.surface, 7.0)
            assertContrast(name, "onBackground/background", scheme.onBackground, scheme.background, 7.0)
            assertContrast(name, "onPrimaryContainer", scheme.onPrimaryContainer, scheme.primaryContainer, 7.0)
            assertContrast(name, "onSecondaryContainer", scheme.onSecondaryContainer, scheme.secondaryContainer, 7.0)
            assertContrast(name, "onTertiaryContainer", scheme.onTertiaryContainer, scheme.tertiaryContainer, 7.0)
            assertContrast(name, "onErrorContainer", scheme.onErrorContainer, scheme.errorContainer, 7.0)
            assertContrast(name, "inverseOnSurface", scheme.inverseOnSurface, scheme.inverseSurface, 7.0)
        }
    }

    @Test
    fun `text roles reach AA on every surface level`() {
        themes.forEach { (name, scheme) ->
            scheme.surfaces().forEach { (surfaceName, surface) ->
                assertContrast(name, "onSurface/$surfaceName", scheme.onSurface, surface, 7.0)
                assertContrast(name, "onSurfaceVariant/$surfaceName", scheme.onSurfaceVariant, surface, 4.5)
            }
            assertContrast(name, "primary/surface", scheme.primary, scheme.surface, 4.5)
            assertContrast(name, "secondary/surface", scheme.secondary, scheme.surface, 4.5)
            assertContrast(name, "tertiary/surface", scheme.tertiary, scheme.surface, 4.5)
            assertContrast(name, "error/surface", scheme.error, scheme.surface, 4.5)
            assertContrast(name, "error/errorContainer", scheme.error, scheme.errorContainer, 4.5)
            assertContrast(name, "onSecondary/secondary", scheme.onSecondary, scheme.secondary, 4.5)
            assertContrast(name, "onTertiary/tertiary", scheme.onTertiary, scheme.tertiary, 4.5)
            assertContrast(name, "onError/error", scheme.onError, scheme.error, 4.5)
            assertContrast(name, "inversePrimary/inverseSurface", scheme.inversePrimary, scheme.inverseSurface, 4.5)
        }
    }

    @Test
    fun `control outline reaches 3 to 1 on every surface level`() {
        themes.forEach { (name, scheme) ->
            scheme.surfaces().forEach { (surfaceName, surface) ->
                assertContrast(name, "outline/$surfaceName", scheme.outline, surface, 3.0)
            }
        }
    }

    @Test
    fun `night field error is readable on dark surfaces`() {
        assertContrast("night", "error/surface", LuxMapDarkColorScheme.error, LuxMapDarkColorScheme.surface, 7.0)
    }
}
