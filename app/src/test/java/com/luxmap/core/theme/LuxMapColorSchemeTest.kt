package com.luxmap.core.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LuxMapColorSchemeTest {
    private val materialDefaultPurpleContainer = Color(0xFFEADDFF)

    @Test
    fun `light primaryContainer is the navy token, not the Material default`() {
        assertEquals(Navy50, LuxMapLightColorScheme.primaryContainer)
    }

    @Test
    fun `light onPrimaryContainer is the navy token`() {
        assertEquals(Navy700, LuxMapLightColorScheme.onPrimaryContainer)
    }

    @Test
    fun `light primaryContainer is never the Material baseline purple`() {
        assertTrue(LuxMapLightColorScheme.primaryContainer != materialDefaultPurpleContainer)
    }

    @Test
    fun `dark primaryContainer is the navy token`() {
        assertEquals(Navy800, LuxMapDarkColorScheme.primaryContainer)
    }

    @Test
    fun `dark onPrimaryContainer is the navy token`() {
        assertEquals(Navy100, LuxMapDarkColorScheme.onPrimaryContainer)
    }

    @Test
    fun `selected tab label meets AA against its own container in light`() {
        val ratio =
            contrastRatio(
                LuxMapLightColorScheme.onPrimaryContainer,
                LuxMapLightColorScheme.primaryContainer,
            )
        assertTrue("contrast was $ratio", ratio >= 4.5)
    }

    @Test
    fun `selected tab label meets AA against its own container in dark`() {
        val ratio =
            contrastRatio(
                LuxMapDarkColorScheme.onPrimaryContainer,
                LuxMapDarkColorScheme.primaryContainer,
            )
        assertTrue("contrast was $ratio", ratio >= 4.5)
    }

    @Test
    fun `no role the app references is a Material baseline purple`() {
        val baselinePurples =
            setOf(
                Color(0xFF6750A4), Color(0xFFEADDFF), Color(0xFF21005D), Color(0xFF4F378B),
                Color(0xFF625B71), Color(0xFFE8DEF8), Color(0xFF1D192B), Color(0xFF7D5260),
                Color(0xFFFFD8E4), Color(0xFF31111D),
            )
        listOf(
            "light" to LuxMapLightColorScheme,
            "dark" to LuxMapDarkColorScheme,
        ).forEach { (name, scheme) ->
            val referenced =
                mapOf(
                    "primary" to scheme.primary,
                    "onPrimary" to scheme.onPrimary,
                    "primaryContainer" to scheme.primaryContainer,
                    "onPrimaryContainer" to scheme.onPrimaryContainer,
                    "secondary" to scheme.secondary,
                    "background" to scheme.background,
                    "surface" to scheme.surface,
                    "onSurface" to scheme.onSurface,
                    "surfaceVariant" to scheme.surfaceVariant,
                    "onSurfaceVariant" to scheme.onSurfaceVariant,
                    "outline" to scheme.outline,
                    "error" to scheme.error,
                )
            referenced.forEach { (role, value) ->
                assertTrue("$name.$role leaked a Material baseline colour", value !in baselinePurples)
            }
        }
    }
}
