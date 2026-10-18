package com.luxmap.feature.survey.data.sync

import com.luxmap.core.network.WorkOrdersApi
import com.luxmap.core.network.errorCodeOrNull
import com.luxmap.core.sync.SyncOpHandler
import com.luxmap.core.sync.SyncOpResult
import com.luxmap.feature.survey.data.dao.PolePhotoDao
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import java.io.File
import java.io.IOException
import javax.inject.Inject

class UploadPolePhotoSyncHandler
    @Inject
    constructor(
        private val api: WorkOrdersApi,
        private val dao: PolePhotoDao,
    ) : SyncOpHandler {
        override val opType = "upload_pole_photo"

        override suspend fun handle(
            payloadJson: String,
            onProgress: suspend (Long, Long) -> Unit,
        ): SyncOpResult {
            val payload = Json.decodeFromString<PolePhotoSyncPayload>(payloadJson)
            val photo = dao.byId(payload.photoId) ?: return SyncOpResult.Failed("Local pole photo row missing")

            return try {
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
                SyncOpResult.Done
            } catch (e: HttpException) {
                when (e.code()) {
                    409 -> SyncOpResult.Conflict(e.errorCodeOrNull() ?: "conflict")
                    400, 403, 404, 415 -> SyncOpResult.Failed(e.errorCodeOrNull() ?: "HTTP ${e.code()}")
                    else -> SyncOpResult.RetryLater
                }
            } catch (e: IOException) {
                SyncOpResult.RetryLater
            }
        }
    }
