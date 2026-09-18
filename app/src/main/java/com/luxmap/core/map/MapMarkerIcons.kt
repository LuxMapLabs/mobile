package com.luxmap.core.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.content.ContextCompat
import com.luxmap.R
import com.luxmap.core.theme.AssetCondition
import org.maplibre.android.maps.Style

const val POI_BADGE_ICON_ID = "poi_badge_icon"
const val IOT_BADGE_ICON_ID = "iot_badge_icon"

// Marker shape per fixture_status (F12/FM-37, Design System 3.4) — each icon has its own baked-in
// color (see the 4 ic_marker_*.xml drawables), picked via Expression.match on iconImage, same
// idea as circleColor's match in MapScreen.kt's old CircleLayer version. Not SDF/tinted, same
// fixed-color pattern as the POI/IoT badges above.
const val MARKER_NORMAL_ICON_ID = "marker_icon_normal"
const val MARKER_DIM_ICON_ID = "marker_icon_dim"
const val MARKER_OUT_ICON_ID = "marker_icon_out"
const val MARKER_UNKNOWN_ICON_ID = "marker_icon_unknown"

fun AssetCondition.markerIconId(): String =
    when (this) {
        AssetCondition.NORMAL -> MARKER_NORMAL_ICON_ID
        AssetCondition.DIM -> MARKER_DIM_ICON_ID
        AssetCondition.OUT -> MARKER_OUT_ICON_ID
        AssetCondition.UNKNOWN -> MARKER_UNKNOWN_ICON_ID
    }

// SymbolLayer icon-image needs a Bitmap already registered on the Style (style.addImage) — it
// can't reference a resource id directly. Call this once in MapScreen's style-loaded callback,
// the same way Web GIS registers "bridge_icon" after the map loads.
fun registerMarkerBadgeIcons(
    context: Context,
    style: Style,
) {
    if (style.getImage(POI_BADGE_ICON_ID) == null) {
        style.addImage(POI_BADGE_ICON_ID, context.drawableToBitmap(R.drawable.ic_poi_badge))
    }
    if (style.getImage(IOT_BADGE_ICON_ID) == null) {
        style.addImage(IOT_BADGE_ICON_ID, context.drawableToBitmap(R.drawable.ic_iot_badge))
    }
    if (style.getImage(MARKER_NORMAL_ICON_ID) == null) {
        style.addImage(MARKER_NORMAL_ICON_ID, context.drawableToBitmap(R.drawable.ic_marker_normal))
    }
    if (style.getImage(MARKER_DIM_ICON_ID) == null) {
        style.addImage(MARKER_DIM_ICON_ID, context.drawableToBitmap(R.drawable.ic_marker_dim))
    }
    if (style.getImage(MARKER_OUT_ICON_ID) == null) {
        style.addImage(MARKER_OUT_ICON_ID, context.drawableToBitmap(R.drawable.ic_marker_out))
    }
    if (style.getImage(MARKER_UNKNOWN_ICON_ID) == null) {
        style.addImage(MARKER_UNKNOWN_ICON_ID, context.drawableToBitmap(R.drawable.ic_marker_unknown))
    }
}

private fun Context.drawableToBitmap(drawableResId: Int): Bitmap {
    val drawable = requireNotNull(ContextCompat.getDrawable(this, drawableResId))
    val bitmap =
        Bitmap.createBitmap(
            drawable.intrinsicWidth,
            drawable.intrinsicHeight,
            Bitmap.Config.ARGB_8888,
        )
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap
}
