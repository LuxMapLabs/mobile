package com.luxmap.core.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LuxMapLightColorScheme =
    lightColorScheme(
        primary = LightSemanticColors.navigationActive,
        onPrimary = LightSemanticColors.surface,
        secondary = LightSemanticColors.focus,
        onSecondary = LightSemanticColors.surface,
        tertiary = Green400,
        onTertiary = Dark950,
        background = LightSemanticColors.background,
        onBackground = LightSemanticColors.textPrimary,
        surface = LightSemanticColors.surface,
        onSurface = LightSemanticColors.textPrimary,
        surfaceVariant = LightSemanticColors.surfaceSubtle,
        onSurfaceVariant = LightSemanticColors.textSecondary,
        outline = LightSemanticColors.border,
        error = Danger600,
        onError = Color(0xFFFFFFFF),
    )

private val LuxMapDarkColorScheme =
    darkColorScheme(
        primary = DarkSemanticColors.navigationActive,
        onPrimary = Dark950,
        secondary = DarkSemanticColors.focus,
        onSecondary = Dark950,
        tertiary = Green400,
        onTertiary = Dark950,
        background = DarkSemanticColors.background,
        onBackground = DarkSemanticColors.textPrimary,
        surface = DarkSemanticColors.surface,
        onSurface = DarkSemanticColors.textPrimary,
        surfaceVariant = DarkSemanticColors.surfaceSubtle,
        onSurfaceVariant = DarkSemanticColors.textSecondary,
        outline = DarkSemanticColors.border,
        error = Danger600,
        onError = Color(0xFFFFFFFF),
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
    MaterialTheme(
        colorScheme = if (useDarkTheme) LuxMapDarkColorScheme else LuxMapLightColorScheme,
        typography = Typography,
        content = content,
    )
}
