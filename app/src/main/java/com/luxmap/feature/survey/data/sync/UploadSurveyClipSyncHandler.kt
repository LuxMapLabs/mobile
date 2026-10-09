package com.luxmap.feature.survey.data.sync

import com.luxmap.core.network.SweepsApi
import com.luxmap.core.network.errorCodeOrNull
import com.luxmap.core.sync.SyncOpHandler
import com.luxmap.core.sync.SyncOpResult
import com.luxmap.feature.survey.data.dao.SurveySessionDao
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.HttpException
import java.io.File
import java.io.IOException
import javax.inject.Inject

class UploadSurveyClipSyncHandler
    @Inject
    constructor(
        private val api: SweepsApi,
        private val dao: SurveySessionDao,
    ) : SyncOpHandler {
        override val opType = "upload_survey_clip"

        override suspend fun handle(
            payloadJson: String,
            onProgress: suspend (Long, Long) -> Unit,
        ): SyncOpResult {
            val payload = Json.decodeFromString<UploadSurveyClipPayload>(payloadJson)
            val session = dao.sessionById(payload.sessionId) ?: return SyncOpResult.Failed("Local session row missing")
            val sweepId = session.serverSweepId ?: return SyncOpResult.RetryLater // create_survey_sweep not done yet
            val segment =
                dao.segmentsFor(payload.sessionId).firstOrNull { it.segmentIndex == payload.clipNo }
                    ?: return SyncOpResult.Failed("Local video segment ${payload.clipNo} missing")
            val checksum =
                segment.checksumSha256 ?: return SyncOpResult.Failed("Segment ${payload.clipNo} has no checksum")

            return try {
                val file = File(segment.filePath)
                onProgress(0L, file.length())
                api.uploadClip(
                    sweepId = sweepId,
                    clipNo = payload.clipNo,
                    sha256 = checksum,
                    body = file.asRequestBody("video/mp4".toMediaType()),
                )
                onProgress(file.length(), file.length())
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
