package com.luxmap.feature.assetimport.data

import kotlinx.coroutines.flow.Flow
import java.time.Instant

data class AssignedImportArea(
    val areaCode: String,
    val title: String,
    val communeLabel: String,
    val dueDateLabel: String,
)

enum class ImportTripStatus { IN_PROGRESS, SUBMITTED }

data class ImportTrip(
    val tripId: String,
    val areaCode: String,
    val areaTitle: String,
    val startedAt: Instant,
    val status: ImportTripStatus,
    val submittedAt: Instant?,
)

data class ImportPole(
    val poleEntryId: String,
    val tripId: String,
    val seq: Int,
    val photoFilePath: String,
    val lat: Double?,
    val lng: Double?,
    val manuallyAdjusted: Boolean,
    val note: String,
    val capturedAt: Instant,
)

interface AssetImportRepository {
    fun observeAssignedAreas(): Flow<List<AssignedImportArea>>

    suspend fun startOrResumeTrip(areaCode: String): ImportTrip

    fun observeTrip(tripId: String): Flow<ImportTrip?>

    fun observePoles(tripId: String): Flow<List<ImportPole>>

    suspend fun addPole(
        tripId: String,
        photoFilePath: String,
        lat: Double?,
        lng: Double?,
        manuallyAdjusted: Boolean,
        note: String,
    ): ImportPole

    suspend fun deletePole(poleEntryId: String)

    suspend fun submitTrip(tripId: String)
}
