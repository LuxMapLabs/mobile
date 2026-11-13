package com.luxmap.core.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.pow

private fun channelLuminance(channel: Float): Double {
    val c = channel.toDouble()
    return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
}

fun relativeLuminance(color: Color): Double =
    0.2126 * channelLuminance(color.red) +
        0.7152 * channelLuminance(color.green) +
        0.0722 * channelLuminance(color.blue)

fun contrastRatio(
    foreground: Color,
    background: Color,
): Double {
    val a = relativeLuminance(foreground)
    val b = relativeLuminance(background)
    val lighter = maxOf(a, b)
    val darker = minOf(a, b)
    return (lighter + 0.05) / (darker + 0.05)
}
