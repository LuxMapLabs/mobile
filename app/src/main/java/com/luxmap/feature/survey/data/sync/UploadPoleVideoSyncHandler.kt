package com.luxmap.feature.survey.data.sync

import com.luxmap.core.network.WorkOrdersApi
import com.luxmap.core.network.errorCodeOrNull
import com.luxmap.core.sync.SyncOpHandler
import com.luxmap.core.sync.SyncOpResult
import com.luxmap.feature.survey.data.dao.PolePhotoDao
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.HttpException
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject

class UploadPoleVideoSyncHandler
    @Inject
    constructor(
        private val api: WorkOrdersApi,
        private val dao: PolePhotoDao,
    ) : SyncOpHandler {
        override val opType = "upload_pole_video"

        override suspend fun handle(
            payloadJson: String,
            onProgress: suspend (Long, Long) -> Unit,
        ): SyncOpResult {
            val payload = Json.decodeFromString<PolePhotoSyncPayload>(payloadJson)
            val photo = dao.byId(payload.photoId) ?: return SyncOpResult.Failed("Local pole photo row missing")

            return try {
                val file = File(photo.filePath)
                val response =
                    api.uploadPoleVideo(
                        id = photo.workOrderId,
                        clientOpId = photo.photoId,
                        poleId = photo.poleId,
                        capturedAt = photo.capturedAt.toString(),
                        lat = photo.lat.toString(),
                        lng = photo.lng.toString(),
                        sha256 = sha256Of(file),
                        body = file.asRequestBody("video/mp4".toMediaType()),
                    )
                dao.updateServerPhotoId(photo.photoId, response.photoId)
                SyncOpResult.Done
            } catch (e: HttpException) {
                when (e.code()) {
                    409 -> SyncOpResult.Conflict(e.errorCodeOrNull() ?: "conflict")
                    400, 403, 404, 413, 415 -> SyncOpResult.Failed(e.errorCodeOrNull() ?: "HTTP ${e.code()}")
                    else -> SyncOpResult.RetryLater
                }
            } catch (e: IOException) {
                SyncOpResult.RetryLater
            }
        }

        private fun sha256Of(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(8192)
                var bytesRead = input.read(buffer)
                while (bytesRead != -1) {
                    digest.update(buffer, 0, bytesRead)
                    bytesRead = input.read(buffer)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
