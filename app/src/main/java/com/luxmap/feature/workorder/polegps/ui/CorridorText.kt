package com.luxmap.feature.workorder.polegps.ui

import com.luxmap.feature.workorder.polegps.data.CorridorCheck
import com.luxmap.feature.workorder.polegps.data.ROUTE_CORRIDOR_METRES
import java.util.Locale

fun corridorListLabel(check: CorridorCheck): String? =
    when (check) {
        is CorridorCheck.Inside -> "Cách tuyến ${metres(check.distanceM)} / ${metres(ROUTE_CORRIDOR_METRES)}"
        is CorridorCheck.Outside ->
            "Ngoài hành lang tuyến, cách tuyến ${metres(check.distanceM)} / ${metres(ROUTE_CORRIDOR_METRES)}"
        CorridorCheck.Unknown -> null
    }

fun corridorPreviewLabel(check: CorridorCheck): String =
    corridorListLabel(check) ?: "Chưa kiểm tra được hành lang tuyến"

fun previousDistanceLabel(distanceM: Double?): String? = distanceM?.let { "Lệch vị trí cũ ${metres(it)}" }

private fun metres(value: Double): String = "%.1f m".format(Locale.ROOT, value)
