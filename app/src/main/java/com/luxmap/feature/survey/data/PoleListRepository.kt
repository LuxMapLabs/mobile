package com.luxmap.feature.survey.data

data class PoleListItem(
    val poleId: String,
    val position: Int,
    val fixtureStatus: String,
)

data class PoleListResult(
    val poles: List<PoleListItem>,
    // True when this came from the local cache after a failed fetch, not the live backend.
    val isStale: Boolean,
)

interface PoleListRepository {
    suspend fun poles(workOrderId: String): Result<PoleListResult>
}
