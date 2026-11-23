package com.luxmap.feature.survey.data.sync

import com.luxmap.core.network.LuxReadingsApi
import com.luxmap.core.network.errorCodeOrNull
import com.luxmap.core.sync.SyncOpHandler
import com.luxmap.core.sync.SyncOpResult
import com.luxmap.feature.survey.data.dao.LuxReadingDao
import com.luxmap.feature.survey.data.dto.CreateLuxReadingRequestDto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

@Serializable
data class LuxReadingSyncPayload(
    @SerialName("client_op_id") val clientOpId: String,
)

class UploadLuxReadingSyncHandler
    @Inject
    constructor(
        private val api: LuxReadingsApi,
        private val dao: LuxReadingDao,
    ) : SyncOpHandler {
        override val opType = OP_TYPE

        override suspend fun handle(
            payloadJson: String,
            onProgress: suspend (Long, Long) -> Unit,
        ): SyncOpResult {
            val payload = Json.decodeFromString<LuxReadingSyncPayload>(payloadJson)
            val reading = dao.byId(payload.clientOpId) ?: return SyncOpResult.Done
            if (reading.serverLuxId != null) return SyncOpResult.Done
            return try {
                val response =
                    api.create(
                        CreateLuxReadingRequestDto(
                            clientOpId = reading.clientOpId,
                            poleId = reading.poleId,
                            measuredAt = reading.measuredAt.toString(),
                            luxValue = reading.luxValue,
                            dataSource = reading.dataSource,
                            meterModel = reading.meterModel,
                        ),
                    )
                dao.updateServerLuxId(reading.clientOpId, response.luxId)
                SyncOpResult.Done
            } catch (e: HttpException) {
                when (e.code()) {
                    400, 403, 404 -> SyncOpResult.Failed(e.errorCodeOrNull() ?: "HTTP ${e.code()}")
                    else -> SyncOpResult.RetryLater
                }
            } catch (e: IOException) {
                SyncOpResult.RetryLater
            }
        }

        companion object {
            const val OP_TYPE = "upload_lux_reading"
        }
    }
