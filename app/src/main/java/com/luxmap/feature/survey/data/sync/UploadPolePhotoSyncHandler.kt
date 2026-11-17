package com.luxmap.feature.survey.data.sync

import com.luxmap.core.network.WorkOrdersApi
import com.luxmap.core.network.errorCodeOrNull
import com.luxmap.core.sync.SyncOpHandler
import com.luxmap.core.sync.SyncOpResult
import com.luxmap.feature.survey.data.PolePhotoWithdrawal
import com.luxmap.feature.survey.data.dao.PolePhotoDao
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import java.io.File
import java.io.IOException
import java.time.Instant
import javax.inject.Inject

class UploadPolePhotoSyncHandler
    @Inject
    constructor(
        private val api: WorkOrdersApi,
        private val dao: PolePhotoDao,
        private val withdrawal: PolePhotoWithdrawal = PolePhotoWithdrawal(api, dao),
    ) : SyncOpHandler {
        override val opType = "upload_pole_photo"

        override suspend fun handle(
            payloadJson: String,
            onProgress: suspend (Long, Long) -> Unit,
        ): SyncOpResult =
            withdrawal.mediaMutex.withLock {
                val payload = Json.decodeFromString<PolePhotoSyncPayload>(payloadJson)
                val photo = dao.byId(payload.photoId) ?: return@withLock SyncOpResult.Done

                try {
                    if (photo.serverPhotoId == null) {
                        val file = File(photo.filePath)
                        val photoPart =
                            MultipartBody.Part.createFormData(
                                "photo",
                                file.name,
                                file.asRequestBody("image/jpeg".toMediaType()),
                            )
                        val response =
                            api.uploadPolePhoto(
                                id = photo.workOrderId,
                                poleId = photo.poleId.toRequestBody(),
                                photo = photoPart,
                                capturedAt = photo.capturedAt.toString().toRequestBody(),
                                lat = photo.lat.toString().toRequestBody(),
                                lng = photo.lng.toString().toRequestBody(),
                                clientOpId = photo.photoId.toRequestBody(),
                            )
                        // Server assigns its own photo_id (WorkOrderPolePhotoService.cs), different from
                        // client_op_id above - must be saved to look up the AI result later.
                        dao.updateServerPhotoId(photo.photoId, response.photoId)
                        response.aiStatus?.let { aiStatus ->
                            dao.updateUploadAiResult(
                                photoId = photo.photoId,
                                aiStatus = aiStatus,
                                cvState = response.cvState,
                                confidence = response.confidence,
                                modelVersion = response.modelVersion,
                                analyzedAt = response.analyzedAt?.let { runCatching { Instant.parse(it) }.getOrNull() },
                            )
                        }
                    }
                    withdrawal.withdrawReplacedMedia(photo.workOrderId, photo.poleId)
                    SyncOpResult.Done
                } catch (e: HttpException) {
                    when (e.code()) {
                        409 -> {
                            val code = e.errorCodeOrNull()
                            if (code == "POLE_PHOTO_WITHDRAWN") {
                                dao.deleteByPolePhotoId(photo.photoId)
                                SyncOpResult.Done
                            } else {
                                SyncOpResult.Conflict(code ?: "conflict")
                            }
                        }
                        400, 403, 404, 415 -> SyncOpResult.Failed(e.errorCodeOrNull() ?: "HTTP ${e.code()}")
                        else -> SyncOpResult.RetryLater
                    }
                } catch (e: IOException) {
                    SyncOpResult.RetryLater
                }
            }
    }
