package com.luxmap.core.map

import androidx.compose.ui.graphics.Color
import com.luxmap.core.theme.AssetCondition

object MapMarkerColors {
    val Normal = Color(0xFF10B981)
    val Dim = Color(0xFFF59E0B)
    val Out = Color(0xFFF43F5E)
    val Unknown = Color(0xFF64748B)
    val PoiBadge = Color(0xFFF59E0B)
    val Edge = Color(0xFF0F172A)
    val SelectionVector = Color(0xFF1F3864)
    val RouteNormal = Color(0xFF3E86C9)
    val RouteFault = Color(0xFFD64545)
}

fun AssetCondition.markerFill(): Color =
    when (this) {
        AssetCondition.NORMAL -> MapMarkerColors.Normal
        AssetCondition.DIM -> MapMarkerColors.Dim
        AssetCondition.OUT -> MapMarkerColors.Out
        AssetCondition.UNKNOWN -> MapMarkerColors.Unknown
    }
