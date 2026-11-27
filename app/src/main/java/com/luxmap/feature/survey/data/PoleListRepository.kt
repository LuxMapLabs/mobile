package com.luxmap.feature.survey.data

data class PoleListItem(
    val poleId: String,
    val position: Int?,
    val lat: Double?,
    val lng: Double?,
    val fixtureStatus: String,
    val segmentId: String? = null,
    val note: String? = null,
    val fixtureType: String? = null,
    val lampWatt: Int? = null,
    val lampCount: Int? = null,
    val fixtureCount: Int? = null,
    val nearSensitivePoi: Boolean? = null,
)

data class PoleListResult(
    val poles: List<PoleListItem>,
    // True when this came from the local cache after a failed fetch, not the live backend.
    val isStale: Boolean,
)

interface PoleListRepository {
    suspend fun poles(workOrderId: String): Result<PoleListResult>

    // The list last saved on this device, no network: empty when it was never fetched.
    suspend fun cachedPoles(workOrderId: String): List<PoleListItem>
}
