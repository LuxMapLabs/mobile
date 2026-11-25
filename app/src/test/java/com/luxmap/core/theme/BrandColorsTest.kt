package com.luxmap.core.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrandColorsTest {
    private val themes =
        listOf(
            "light" to LightBrandColors,
            "night" to NightFieldBrandColors,
        )

    private fun assertContrast(
        label: String,
        foreground: Color,
        background: Color,
        minimum: Double,
    ) {
        val ratio = contrastRatio(foreground, background)
        assertTrue("$label was $ratio, needs $minimum", ratio >= minimum)
    }

    @Test
    fun `hero text reaches AAA on the hero in both themes`() {
        themes.forEach { (name, brand) ->
            assertContrast("$name onHero", brand.onHero, brand.hero, 7.0)
            assertContrast("$name onHeroVariant", brand.onHeroVariant, brand.hero, 4.5)
        }
    }

    @Test
    fun `lumen is visible on the hero and carries AAA text`() {
        themes.forEach { (name, brand) ->
            assertContrast("$name lumen on hero", brand.lumen, brand.hero, 4.5)
            assertContrast("$name onLumen on lumen", brand.onLumen, brand.lumen, 7.0)
        }
    }

    @Test
    fun `lumen keeps the same value in both themes`() {
        assertEquals(LightBrandColors.lumen, NightFieldBrandColors.lumen)
        assertEquals(Color(0xFFFFC44D), LightBrandColors.lumen)
    }

    @Test
    fun `hero shapes are distinct from the hero so the art stays visible`() {
        themes.forEach { (name, brand) ->
            assertNotEquals("$name shapeA", brand.hero, brand.heroShapeA)
            assertNotEquals("$name shapeB", brand.hero, brand.heroShapeB)
            assertNotEquals("$name shapes", brand.heroShapeA, brand.heroShapeB)
        }
    }

    @Test
    fun `light hero keeps the approved Navy700 brand colour`() {
        assertEquals(Navy700, LightBrandColors.hero)
    }
}
