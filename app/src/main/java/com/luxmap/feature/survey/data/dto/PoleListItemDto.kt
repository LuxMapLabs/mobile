package com.luxmap.feature.survey.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PoleListItemDto(
    @SerialName("pole_id") val poleId: String,
    val position: Int,
    @SerialName("fixture_status") val fixtureStatus: String,
)
