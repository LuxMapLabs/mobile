package com.luxmap.core.map

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.luxmap.core.theme.AssetCondition
import org.maplibre.android.MapLibre

val VECTOR_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

// Call once when the app starts (LuxMapApp.onCreate), before any MapView is created.
fun initMapLibre(context: Context) {
    MapLibre.getInstance(context)
}

fun AssetCondition.markerColor(): Color = markerFill()

fun AssetCondition.markerColorArgb(): Int = markerColor().toArgb()

fun routeColor(hasActiveSegmentFault: Boolean): Color =
    if (hasActiveSegmentFault) MapMarkerColors.RouteFault else MapMarkerColors.RouteNormal

fun routeColorArgb(hasActiveSegmentFault: Boolean): Int = routeColor(hasActiveSegmentFault).toArgb()
