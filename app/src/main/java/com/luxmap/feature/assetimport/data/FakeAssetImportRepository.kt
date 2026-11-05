package com.luxmap.feature.assetimport.data

import com.luxmap.feature.assetimport.data.dao.AssetImportDao
import com.luxmap.feature.assetimport.data.entity.LocalAssetImportPoleEntity
import com.luxmap.feature.assetimport.data.entity.LocalAssetImportTripEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private const val STATUS_IN_PROGRESS = "in_progress"
private const val STATUS_SUBMITTED = "submitted"

private val FAKE_ASSIGNED_AREAS =
    listOf(
        AssignedImportArea(
            areaCode = "KV-07",
            title = "Khu dân cư Ấp 3 – Phước Lợi",
            communeLabel = "KV-07 · Phước Lợi",
            dueDateLabel = "15/11",
        ),
    )

@Singleton
class FakeAssetImportRepository
    @Inject
    constructor(
        private val dao: AssetImportDao,
    ) : AssetImportRepository {
        private val assignedAreas = MutableStateFlow(FAKE_ASSIGNED_AREAS)

        override fun observeAssignedAreas(): Flow<List<AssignedImportArea>> = assignedAreas

        override suspend fun startOrResumeTrip(areaCode: String): ImportTrip {
            val area = FAKE_ASSIGNED_AREAS.first { it.areaCode == areaCode }
            val existing = dao.inProgressTripForArea(areaCode)
            if (existing != null) return existing.toDomain()
            val entity =
                LocalAssetImportTripEntity(
                    tripId = UUID.randomUUID().toString(),
                    areaCode = area.areaCode,
                    areaTitle = area.title,
                    startedAt = Instant.now(),
                    status = STATUS_IN_PROGRESS,
                )
            dao.insertTrip(entity)
            return entity.toDomain()
        }

        override fun observeTrip(tripId: String): Flow<ImportTrip?> = dao.observeTrip(tripId).map { it?.toDomain() }

        override fun observePoles(tripId: String): Flow<List<ImportPole>> =
            dao.observePoles(tripId).map { poles -> poles.map { it.toDomain() } }

        override suspend fun addPole(
            tripId: String,
            photoFilePath: String,
            lat: Double?,
            lng: Double?,
            manuallyAdjusted: Boolean,
            note: String,
        ): ImportPole {
            val seq = dao.poleCount(tripId) + 1
            val entity =
                LocalAssetImportPoleEntity(
                    poleEntryId = UUID.randomUUID().toString(),
                    tripId = tripId,
                    seq = seq,
                    photoFilePath = photoFilePath,
                    lat = lat,
                    lng = lng,
                    manuallyAdjusted = manuallyAdjusted,
                    note = note,
                    capturedAt = Instant.now(),
                )
            dao.insertPole(entity)
            return entity.toDomain()
        }

        override suspend fun deletePole(poleEntryId: String) {
            dao.deletePole(poleEntryId)
        }

        override suspend fun submitTrip(tripId: String) {
            dao.updateTripStatus(tripId, STATUS_SUBMITTED)
        }
    }

private fun LocalAssetImportTripEntity.toDomain() =
    ImportTrip(
        tripId = tripId,
        areaCode = areaCode,
        areaTitle = areaTitle,
        startedAt = startedAt,
        status = if (status == STATUS_SUBMITTED) ImportTripStatus.SUBMITTED else ImportTripStatus.IN_PROGRESS,
    )

private fun LocalAssetImportPoleEntity.toDomain() =
    ImportPole(
        poleEntryId = poleEntryId,
        tripId = tripId,
        seq = seq,
        photoFilePath = photoFilePath,
        lat = lat,
        lng = lng,
        manuallyAdjusted = manuallyAdjusted,
        note = note,
        capturedAt = capturedAt,
    )
