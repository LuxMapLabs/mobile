package com.luxmap.feature.survey.data.sync

import com.luxmap.core.network.LuxReadingsApi
import com.luxmap.core.network.errorCodeOrNull
import com.luxmap.core.sync.SyncOpHandler
import com.luxmap.core.sync.SyncOpResult
import com.luxmap.core.sync.SyncQueueDao
import com.luxmap.feature.survey.data.dao.LuxReadingDao
import com.luxmap.feature.survey.data.dao.PolePhotoDao
import com.luxmap.feature.survey.data.dto.CreateLuxReadingRequestDto
import com.luxmap.feature.survey.data.dto.LuxProtocolDto
import com.luxmap.feature.survey.data.entity.LocalLuxReadingEntity
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
        private val photoDao: PolePhotoDao,
        private val syncQueueDao: SyncQueueDao,
    ) : SyncOpHandler {
        override val opType = OP_TYPE

        override suspend fun handle(
            payloadJson: String,
            onProgress: suspend (Long, Long) -> Unit,
        ): SyncOpResult {
            val payload = Json.decodeFromString<LuxReadingSyncPayload>(payloadJson)
            val reading = dao.byId(payload.clientOpId) ?: return SyncOpResult.Done
            if (reading.serverLuxId != null) return SyncOpResult.Done
            val photoLink = photoLinkFor(reading)
            if (photoLink is PhotoLink.Pending) return SyncOpResult.WaitingForDependency
            val serverPhotoId = (photoLink as? PhotoLink.Linked)?.serverPhotoId
            return try {
                send(reading, serverPhotoId)
            } catch (e: HttpException) {
                val errorCode = e.errorCodeOrNull()
                if (serverPhotoId != null && errorCode in PHOTO_LINK_ERRORS) {
                    try {
                        send(reading, serverPhotoId = null)
                    } catch (retry: HttpException) {
                        failureFor(retry)
                    } catch (retry: IOException) {
                        SyncOpResult.RetryLater
                    }
                } else {
                    failureFor(e, errorCode)
                }
            } catch (e: IOException) {
                SyncOpResult.RetryLater
            }
        }

        private suspend fun photoLinkFor(reading: LocalLuxReadingEntity): PhotoLink {
            val photo = photoDao.byId(reading.photoId) ?: return PhotoLink.None
            photo.serverPhotoId?.let { return PhotoLink.Linked(it) }
            return when (syncQueueDao.statusOf(reading.photoId)) {
                "queued", "syncing" -> PhotoLink.Pending
                else -> PhotoLink.None
            }
        }

        private suspend fun send(
            reading: LocalLuxReadingEntity,
            serverPhotoId: String?,
        ): SyncOpResult {
            val response =
                api.create(
                    CreateLuxReadingRequestDto(
                        clientOpId = reading.clientOpId,
                        poleId = reading.poleId,
                        measuredAt = reading.measuredAt.toString(),
                        luxValue = reading.luxValue,
                        dataSource = reading.dataSource,
                        meterModel = reading.meterModel,
                        photoId = serverPhotoId,
                        protocol = reading.protocol(),
                    ),
                )
            dao.updateServerLuxId(reading.clientOpId, response.luxId)
            return SyncOpResult.Done
        }

        private fun failureFor(
            e: HttpException,
            errorCode: String? = e.errorCodeOrNull(),
        ): SyncOpResult =
            when (e.code()) {
                400, 403, 404, 409 -> SyncOpResult.Failed(errorCode ?: "HTTP ${e.code()}")
                else -> SyncOpResult.RetryLater
            }

        private fun LocalLuxReadingEntity.protocol(): LuxProtocolDto? {
            val samples = samplesJson?.let { Json.decodeFromString<List<Double>>(it) } ?: return null
            return LuxProtocolDto(samples, sensorHeightM ?: return null, moduleId ?: return null)
        }

        private sealed interface PhotoLink {
            data object None : PhotoLink

            data object Pending : PhotoLink

            data class Linked(
                val serverPhotoId: String,
            ) : PhotoLink
        }

        companion object {
            const val OP_TYPE = "upload_lux_reading"
            private val PHOTO_LINK_ERRORS =
                setOf(
                    "POLE_PHOTO_NOT_FOUND",
                    "PHOTO_POLE_MISMATCH",
                    "PHOTO_LUX_ALREADY_LINKED",
                    "POLE_PHOTO_ALREADY_DECIDED",
                )
        }
    }
