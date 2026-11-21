package com.luxmap.feature.survey.data.dto

import com.luxmap.feature.workorder.data.dto.PoleCoordinateDto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PoleListItemDto(
    @SerialName("pole_id") val poleId: String,
    val position: Int? = null,
    val location: PoleCoordinateDto? = null,
    @SerialName("fixture_status") val fixtureStatus: String,
    @SerialName("segment_id") val segmentId: String? = null,
    val note: String? = null,
    @SerialName("fixture_type") val fixtureType: String? = null,
    @SerialName("lamp_watt") val lampWatt: Int? = null,
    @SerialName("lamp_count") val lampCount: Int? = null,
    @SerialName("fixture_count") val fixtureCount: Int? = null,
    @SerialName("near_sensitive_poi") val nearSensitivePoi: Boolean? = null,
)
