package com.luxmap.feature.polelocation.data

import com.luxmap.core.network.AssetsApi
import com.luxmap.core.sync.SyncQueueManager
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class PoleMissingLocationItem(
    val poleId: String,
    val externalRef: String?,
    val note: String?,
    val communeName: String?,
    // One of PoleLocationUpdateStatus, or null when this pole has never been captured locally.
    val localStatus: String?,
)

@Singleton
class PoleLocationRepository
    @Inject
    constructor(
        private val api: AssetsApi,
        private val dao: PoleLocationUpdateDao,
        private val syncQueueManager: SyncQueueManager,
    ) {
        // A pole already PENDING/QUEUED is mid-flight to the server - showing it would invite a
        // duplicate capture. DONE never needs filtering here: the server's own list stops naming
        // that pole as missing a location on the very next fetch. CONFLICT/FAILED stay so the
        // engineer can retry.
        suspend fun polesMissingLocation(): Result<List<PoleMissingLocationItem>> =
            runCatching {
                api.listPoles(missingLocation = true).items
                    .map { item ->
                        val local = dao.byPoleId(item.poleId)
                        PoleMissingLocationItem(
                            poleId = item.poleId,
                            externalRef = item.externalRef,
                            note = item.note,
                            communeName = item.communeName,
                            localStatus = local?.status,
                        )
                    }
                    .filterNot {
                        it.localStatus == PoleLocationUpdateStatus.PENDING ||
                            it.localStatus == PoleLocationUpdateStatus.QUEUED
                    }
            }

        suspend fun submit(
            poleId: String,
            filePath: String,
            lat: Double,
            lng: Double,
            capturedAt: Instant,
        ) {
            val clientOpId = UUID.randomUUID().toString()
            dao.upsert(
                LocalPoleLocationUpdateEntity(
                    poleId = poleId,
                    photoFilePath = filePath,
                    lat = lat,
                    lng = lng,
                    capturedAt = capturedAt,
                    status = PoleLocationUpdateStatus.PENDING,
                    clientOpId = clientOpId,
                ),
            )
            syncQueueManager.enqueue(
                opType = "update_pole_location",
                payloadJson = Json.encodeToString(PoleLocationSyncPayload(poleId)),
                clientOpId = clientOpId,
            )
            dao.updateStatus(poleId, PoleLocationUpdateStatus.QUEUED)
        }
    }
