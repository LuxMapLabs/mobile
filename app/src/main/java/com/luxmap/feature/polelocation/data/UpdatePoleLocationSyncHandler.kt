package com.luxmap.feature.polelocation.data

import com.luxmap.core.network.AssetsApi
import com.luxmap.core.network.errorCodeOrNull
import com.luxmap.core.sync.SyncOpHandler
import com.luxmap.core.sync.SyncOpResult
import com.luxmap.feature.polelocation.data.dto.SetPoleLocationRequestDto
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

@Serializable
data class PoleLocationSyncPayload(val poleId: String)

class UpdatePoleLocationSyncHandler
    @Inject
    constructor(
        private val api: AssetsApi,
        private val dao: PoleLocationUpdateDao,
    ) : SyncOpHandler {
        override val opType = "update_pole_location"

        override suspend fun handle(
            payloadJson: String,
            onProgress: suspend (Long, Long) -> Unit,
        ): SyncOpResult {
            val payload = Json.decodeFromString<PoleLocationSyncPayload>(payloadJson)
            val row = dao.byPoleId(payload.poleId) ?: return SyncOpResult.Failed("Local pole location row missing")

            return try {
                api.setPoleLocation(payload.poleId, SetPoleLocationRequestDto(row.lat, row.lng))
                dao.updateStatus(payload.poleId, PoleLocationUpdateStatus.DONE)
                SyncOpResult.Done
            } catch (e: HttpException) {
                when {
                    e.code() == 409 -> {
                        dao.updateStatus(payload.poleId, PoleLocationUpdateStatus.CONFLICT)
                        SyncOpResult.Conflict(e.errorCodeOrNull() ?: "conflict")
                    }
                    e.code() == 400 || e.code() == 404 -> {
                        dao.updateStatus(payload.poleId, PoleLocationUpdateStatus.FAILED)
                        SyncOpResult.Failed(e.errorCodeOrNull() ?: "HTTP ${e.code()}")
                    }
                    else -> SyncOpResult.RetryLater
                }
            } catch (e: IOException) {
                SyncOpResult.RetryLater
            }
        }
    }
