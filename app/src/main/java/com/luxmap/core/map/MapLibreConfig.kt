package com.luxmap.core.map

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.luxmap.BuildConfig
import com.luxmap.core.theme.Amber500
import com.luxmap.core.theme.AssetCondition
import com.luxmap.core.theme.Blue500
import com.luxmap.core.theme.Gray500
import com.luxmap.core.theme.Green400
import com.luxmap.core.theme.Rose600
import org.maplibre.android.MapLibre

// OpenFreeMap "Liberty" style — vector style built from OSM, free, no API key needed. Drawn
// with lines (not a photo) so it stays sharp at every zoom level — unlike the satellite basemap
// below, which can look blurry where the source imagery has low resolution (see MAP_STYLE_URL).
// Used as the fallback when MAPTILER_API_KEY is not configured, and as the "vector" choice when
// the user taps the basemap toggle on MapScreen for a sharper view.
val VECTOR_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

// MapTiler's official satellite + labels style (not Google tiles through a hidden URL like Web
// GIS does — that breaks Google's terms of service and gets blocked easily). Key read from
// local.properties through BuildConfig (see app/build.gradle.kts), never hardcoded in source.
// NOTE: the free satellite imagery is only sharp in urban areas — rural areas (LuxMap's actual
// scope) have lower-resolution source imagery so it looks softer/blurrier than VECTOR_STYLE_URL.
// This is a real limit of the imagery source, not fixable in code, so MapScreen has a button to
// switch to VECTOR_STYLE_URL when a sharper view is needed.
val MAP_STYLE_URL: String =
    if (BuildConfig.MAPTILER_API_KEY.isNotBlank()) {
        "https://api.maptiler.com/maps/hybrid/style.json?key=${BuildConfig.MAPTILER_API_KEY}"
    } else {
        VECTOR_STYLE_URL
    }

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
