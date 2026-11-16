package com.luxmap.core.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

internal val LuxMapLightColorScheme =
    lightColorScheme(
        primary = Navy700,
        onPrimary = White,
        primaryContainer = Navy100,
        onPrimaryContainer = Navy800,
        secondary = Slate600,
        onSecondary = White,
        secondaryContainer = Gray200,
        onSecondaryContainer = Gray900,
        tertiary = Blue600,
        onTertiary = White,
        tertiaryContainer = Blue50,
        onTertiaryContainer = Blue900,
        background = Gray25,
        onBackground = Gray900,
        surface = White,
        onSurface = Gray900,
        surfaceVariant = Gray50,
        onSurfaceVariant = Slate600,
        surfaceTint = Color.Transparent,
        surfaceContainerLowest = White,
        surfaceContainerLow = Gray25,
        surfaceContainer = Gray50,
        surfaceContainerHigh = Gray150,
        surfaceContainerHighest = Gray200,
        surfaceDim = Gray200,
        surfaceBright = White,
        inverseSurface = Slate800,
        inverseOnSurface = Gray50,
        inversePrimary = Navy200,
        outline = Gray500,
        outlineVariant = Gray200,
        error = Red700,
        onError = White,
        errorContainer = Red50,
        onErrorContainer = Red900,
        scrim = Gray900,
    )

internal val LuxMapDarkColorScheme =
    darkColorScheme(
        primary = Navy300,
        onPrimary = Navy950,
        primaryContainer = Navy750,
        onPrimaryContainer = Navy100,
        secondary = DarkSecondary,
        onSecondary = Dark900,
        secondaryContainer = Dark700,
        onSecondaryContainer = Night100,
        tertiary = Blue300,
        onTertiary = Blue950,
        tertiaryContainer = Blue800,
        onTertiaryContainer = Blue100,
        background = Dark950,
        onBackground = Night100,
        surface = Dark900,
        onSurface = Night100,
        surfaceVariant = Night850,
        onSurfaceVariant = DarkSecondary,
        surfaceTint = Color.Transparent,
        surfaceContainerLowest = Dark950,
        surfaceContainerLow = Dark900,
        surfaceContainer = Night850,
        surfaceContainerHigh = Night800,
        surfaceContainerHighest = Night750,
        surfaceDim = Dark950,
        surfaceBright = Night750,
        inverseSurface = Night100,
        inverseOnSurface = Dark800,
        inversePrimary = Navy700,
        outline = Night500,
        outlineVariant = Dark700,
        error = ErrorDark,
        onError = NightOnError,
        errorContainer = NightError700,
        onErrorContainer = NightOnErrorContainer,
        scrim = Color.Black,
    )

// forceDark: dùng khi màn hình bắt buộc/mặc định dark theo ngữ cảnh (F03, F04, chế độ điều hướng
// tập trung của F09) — xem bảng "Dark Mode theo ngữ cảnh" trong CLAUDE.md. Màn đó tự bọc riêng
// LuxMapTheme(forceDark = true) { ... } quanh nội dung của mình, không đổi theme toàn app.
@Composable
fun LuxMapTheme(
    forceDark: Boolean = false,
    content: @Composable () -> Unit,
) {
    val useDarkTheme = forceDark || isSystemInDarkTheme()
    CompositionLocalProvider(
        LocalLuxExtendedColors provides if (useDarkTheme) NightFieldExtendedColors else LightExtendedColors,
        LocalLuxStatusColors provides if (useDarkTheme) NightFieldStatusColors else LightStatusColors,
    ) {
        MaterialTheme(
            colorScheme = if (useDarkTheme) LuxMapDarkColorScheme else LuxMapLightColorScheme,
            typography = Typography,
            shapes = LuxShapes,
            content = content,
        )
    }
}
