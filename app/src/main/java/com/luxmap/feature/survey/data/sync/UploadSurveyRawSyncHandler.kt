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

class UploadSurveyRawSyncHandler
    @Inject
    constructor(
        private val api: SweepsApi,
        private val dao: SurveySessionDao,
    ) : SyncOpHandler {
        override val opType = "upload_survey_raw"

        override suspend fun handle(
            payloadJson: String,
            onProgress: suspend (Long, Long) -> Unit,
        ): SyncOpResult {
            val payload = Json.decodeFromString<UploadSurveyRawPayload>(payloadJson)
            val session = dao.sessionById(payload.sessionId) ?: return SyncOpResult.Failed("Local session row missing")
            val sweepId = session.serverSweepId ?: return SyncOpResult.RetryLater

            val (path, mediaType) =
                when (payload.kind) {
                    "gps_track" -> session.gpsTrackFilePath to "application/x-ndjson"
                    "lux_log" -> session.luxLogFilePath to "application/x-ndjson"
                    "capture_config" -> session.captureConfigFilePath to "application/json"
                    else -> return SyncOpResult.Failed("Unknown raw kind ${payload.kind}")
                }
            if (path == null) return SyncOpResult.Failed("Session has no ${payload.kind} file path")

            return try {
                val file = File(path)
                onProgress(0L, file.length())
                api.uploadRaw(
                    sweepId = sweepId,
                    kind = payload.kind,
                    body = file.asRequestBody(mediaType.toMediaType()),
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
