package com.luxmap.feature.survey.data

data class PoleListItem(
    val poleId: String,
    val position: Int,
    val fixtureStatus: String,
)

interface PoleListRepository {
    suspend fun poles(workOrderId: String): Result<List<PoleListItem>>
}
