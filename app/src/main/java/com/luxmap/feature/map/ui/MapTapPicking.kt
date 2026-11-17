package com.luxmap.feature.map.ui

import android.graphics.RectF

data class TapCandidate(val id: String, val x: Float, val y: Float)

fun pickNearestFeature(
    tapX: Float,
    tapY: Float,
    candidates: List<TapCandidate>,
): String? =
    candidates
        .minWithOrNull(
            compareBy<TapCandidate>({ (it.x - tapX) * (it.x - tapX) + (it.y - tapY) * (it.y - tapY) }, { it.id }),
        )?.id

fun tapSquare(
    tapX: Float,
    tapY: Float,
    density: Float,
): RectF {
    val half = TAP_SQUARE_DP / 2 * density
    return RectF(tapX - half, tapY - half, tapX + half, tapY + half)
}

private const val TAP_SQUARE_DP = 48f
