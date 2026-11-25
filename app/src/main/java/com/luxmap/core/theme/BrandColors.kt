package com.luxmap.core.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class LuxBrandColors(
    val hero: Color,
    val onHero: Color,
    val onHeroVariant: Color,
    val heroShapeA: Color,
    val heroShapeB: Color,
    val lumen: Color,
    val onLumen: Color,
)

private val Lumen = Color(0xFFFFC44D)

internal val LightBrandColors =
    LuxBrandColors(
        hero = Navy700,
        onHero = White,
        onHeroVariant = Navy100,
        heroShapeA = Color(0xFF2A4A7F),
        heroShapeB = Color(0xFF33558F),
        lumen = Lumen,
        onLumen = Navy950,
    )

internal val NightFieldBrandColors =
    LuxBrandColors(
        hero = Navy950,
        onHero = White,
        onHeroVariant = Navy200,
        heroShapeA = Navy750,
        heroShapeB = Color(0xFF2A4570),
        lumen = Lumen,
        onLumen = Navy950,
    )

val LocalLuxBrandColors = staticCompositionLocalOf { LightBrandColors }
