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
)
