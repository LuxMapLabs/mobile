package com.luxmap.core.map

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.luxmap.core.theme.Amber500
import com.luxmap.core.theme.AssetCondition
import com.luxmap.core.theme.Blue500
import com.luxmap.core.theme.Gray500
import com.luxmap.core.theme.Green400
import com.luxmap.core.theme.Rose600
import org.maplibre.android.MapLibre

// OpenFreeMap "Liberty" style — vector style built from OSM, free, no API key needed, detailed
// enough for roads/neighborhoods/place names in Vietnam for the FM-06 prototype. Switch to the
// official tile source once FM-15 is finalized with Web GIS (WP5) — see the "Tech stack"
// section in CLAUDE.md.
const val MAP_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

// Call once when the app starts (LuxMapApp.onCreate), before any MapView is created.
fun initMapLibre(context: Context) {
    MapLibre.getInstance(context)
}

// Marker fill color on the map — use the 3 existing primitive colors from core/theme/Color.kt
// (Green400/Amber500/Rose600) + Gray500 for unknown, NOT the badge text bg/text pair (Design
// System section 2.3) because those colors are darker/lighter and hard to see at dot size. One
// color source — MapScreen uses the ARGB version for MapLibre Expression, MapLegend uses Color
// directly for Compose.
fun AssetCondition.markerColor(): Color =
    when (this) {
        AssetCondition.NORMAL -> Green400
        AssetCondition.DIM -> Amber500
        AssetCondition.OUT -> Rose600
        AssetCondition.UNKNOWN -> Gray500
    }

fun AssetCondition.markerColorArgb(): Int = markerColor().toArgb()

// Route color for "surveyed route" (F12) — colored by has_active_segment_fault to match how
// Web GIS shows grid faults, using the 2 existing tokens (Rose600/Blue500), no new color picked.
fun routeColor(hasActiveSegmentFault: Boolean): Color = if (hasActiveSegmentFault) Rose600 else Blue500

fun routeColorArgb(hasActiveSegmentFault: Boolean): Int = routeColor(hasActiveSegmentFault).toArgb()
