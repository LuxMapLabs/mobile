package com.civicflow.core.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val CivicFlowColorScheme =
    lightColorScheme(
        primary = Navy,
        onPrimary = Bg,
        secondary = Blue,
        onSecondary = Bg,
        tertiary = Teal,
        onTertiary = Bg,
        background = Bg,
        onBackground = TextPrimary,
        surface = Bg,
        onSurface = TextPrimary,
        surfaceVariant = LightBlue,
        error = Danger,
        onError = Bg,
    )

@Composable
fun CivicFlowTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CivicFlowColorScheme,
        typography = Typography,
        content = content,
    )
}
