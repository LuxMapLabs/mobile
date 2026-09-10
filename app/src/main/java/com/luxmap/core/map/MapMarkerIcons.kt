package com.luxmap.core.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.content.ContextCompat
import com.luxmap.R
import org.maplibre.android.maps.Style

const val POI_BADGE_ICON_ID = "poi_badge_icon"
const val IOT_BADGE_ICON_ID = "iot_badge_icon"

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
