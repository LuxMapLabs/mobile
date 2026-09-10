package com.luxmap.core.map

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.luxmap.BuildConfig
import com.luxmap.core.theme.Amber500
import com.luxmap.core.theme.AssetCondition
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

// Gọi 1 lần khi app khởi động (LuxMapApp.onCreate), trước khi bất kỳ MapView nào được tạo.
fun initMapLibre(context: Context) {
    MapLibre.getInstance(context)
}

// Màu fill marker trên bản đồ — lấy đúng 3 màu primitive có sẵn từ core/theme/Color.kt
// (Green400/Amber500/Rose600) + Gray500 cho unknown, KHÔNG dùng cặp bg/text của badge chữ
// (mục 2.3 Design System) vì những màu đó tối/nhạt hơn, khó thấy ở kích thước chấm nhỏ.
// Một nguồn màu duy nhất — MapScreen dùng bản ARGB cho MapLibre Expression, MapLegend dùng
// thẳng Color cho Compose.
fun AssetCondition.markerColor(): Color =
    when (this) {
        AssetCondition.NORMAL -> Green400
        AssetCondition.DIM -> Amber500
        AssetCondition.OUT -> Rose600
        AssetCondition.UNKNOWN -> Gray500
    }

fun AssetCondition.markerColorArgb(): Int = markerColor().toArgb()
