package com.luxmap.feature.map.ui

import com.luxmap.R
import com.luxmap.core.theme.AssetCondition

internal val MAP_STATUS_ORDER =
    listOf(AssetCondition.NORMAL, AssetCondition.DIM, AssetCondition.OUT, AssetCondition.UNKNOWN)

internal fun AssetCondition.mapLabel(): String =
    when (this) {
        AssetCondition.NORMAL -> "Đạt chuẩn"
        AssetCondition.DIM -> "Đèn mờ"
        AssetCondition.OUT -> "Hỏng/Tắt"
        AssetCondition.UNKNOWN -> "Chưa quét"
    }

internal fun AssetCondition.markerIconResId(): Int =
    when (this) {
        AssetCondition.NORMAL -> R.drawable.ic_marker_normal
        AssetCondition.DIM -> R.drawable.ic_marker_dim
        AssetCondition.OUT -> R.drawable.ic_marker_out
        AssetCondition.UNKNOWN -> R.drawable.ic_marker_unknown
    }
