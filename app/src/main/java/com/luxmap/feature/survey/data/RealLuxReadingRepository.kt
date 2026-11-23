package com.luxmap.feature.survey.data

import com.luxmap.core.sync.SyncQueueDao
import com.luxmap.core.sync.SyncQueueManager
import com.luxmap.feature.survey.data.dao.LuxReadingDao
import com.luxmap.feature.survey.data.entity.LocalLuxReadingEntity
import com.luxmap.feature.survey.data.sync.LuxReadingSyncPayload
import com.luxmap.feature.survey.data.sync.UploadLuxReadingSyncHandler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

class RealLuxReadingRepository
    @Inject
    constructor(
        private val dao: LuxReadingDao,
        private val syncQueueManager: SyncQueueManager,
        private val syncQueueDao: SyncQueueDao,
    ) : LuxReadingRepository {
        override suspend fun record(
            workOrderId: String,
            poleId: String,
            photoId: String,
            luxValue: Double,
            measuredAt: Instant,
        ): LuxReading {
            val entity =
                LocalLuxReadingEntity(
                    clientOpId = UUID.randomUUID().toString(),
                    workOrderId = workOrderId,
                    poleId = poleId,
                    photoId = photoId,
                    measuredAt = measuredAt,
                    luxValue = luxValue,
                    meterModel = METER_MODEL,
                    dataSource = DATA_SOURCE,
                    createdAt = Instant.now(),
                )
            dao.insert(entity)
            syncQueueManager.enqueue(
                opType = UploadLuxReadingSyncHandler.OP_TYPE,
                payloadJson = Json.encodeToString(LuxReadingSyncPayload(entity.clientOpId)),
                clientOpId = entity.clientOpId,
            )
            return entity.toLuxReading(syncStatus = "queued")
        }

        @OptIn(ExperimentalCoroutinesApi::class)
        override fun observeReadings(workOrderId: String): Flow<List<LuxReading>> =
            dao.observeByWorkOrder(workOrderId).flatMapLatest { entities ->
                if (entities.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    syncQueueDao.observeStatusesFor(entities.map { it.clientOpId }).map { rows ->
                        val statuses = rows.associate { it.clientOpId to it.status }
                        entities.map { it.toLuxReading(statuses[it.clientOpId]) }
                    }
                }
            }

        private fun LocalLuxReadingEntity.toLuxReading(syncStatus: String?) =
            LuxReading(
                clientOpId = clientOpId,
                poleId = poleId,
                photoId = photoId,
                luxValue = luxValue,
                measuredAt = measuredAt,
                syncStatus = syncStatus,
            )

        private companion object {
            const val METER_MODEL = "LuxMap_ESP32"
            const val DATA_SOURCE = "field"
        }
    }
