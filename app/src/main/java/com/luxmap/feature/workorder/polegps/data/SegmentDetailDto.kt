package com.luxmap.feature.workorder.polegps.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SegmentDetailDto(
    @SerialName("geom_wkt") val geomWkt: String? = null,
)
