package com.luxmap.core.common

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.util.Locale

fun geoUri(
    lat: Double,
    lng: Double,
): String {
    val coords = String.format(Locale.US, "%.6f,%.6f", lat, lng)
    return "geo:$coords?q=$coords"
}

fun openInMaps(
    context: Context,
    lat: Double,
    lng: Double,
) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(geoUri(lat, lng)))
    context.startActivity(Intent.createChooser(intent, null))
}
