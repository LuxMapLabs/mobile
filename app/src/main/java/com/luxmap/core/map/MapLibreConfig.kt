package com.luxmap.core.map

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.luxmap.core.theme.Amber500
import com.luxmap.core.theme.AssetCondition
import com.luxmap.core.theme.Gray500
import com.luxmap.core.theme.Green400
import com.luxmap.core.theme.Rose600
import org.maplibre.android.MapLibre

// Style OpenFreeMap "Liberty" — vector style dựng từ OSM, miễn phí, không cần API key,
// đủ chi tiết đường/khu dân cư/địa danh Việt Nam cho bước prototype FM-06. Đổi sang nguồn
// tile chính thức khi FM-15 chốt cùng Web GIS (WP5) — xem CLAUDE.md mục "Ngăn xếp công nghệ".
const val MAP_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

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
