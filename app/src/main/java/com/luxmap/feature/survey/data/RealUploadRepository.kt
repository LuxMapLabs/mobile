package com.luxmap.feature.survey.data

import com.luxmap.core.sync.SyncQueueDao
import com.luxmap.core.sync.SyncQueueManager
import com.luxmap.core.sync.SyncQueueProcessor
import com.luxmap.feature.survey.data.dao.SurveySessionDao
import com.luxmap.feature.survey.data.entity.LocalSurveySessionEntity
import com.luxmap.feature.survey.data.sync.CreateSurveySweepPayload
import com.luxmap.feature.survey.data.sync.SubmitSurveySweepPayload
import com.luxmap.feature.survey.data.sync.UploadSurveyClipPayload
import com.luxmap.feature.survey.data.sync.UploadSurveyRawPayload
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant
import javax.inject.Inject

class RealUploadRepository
    @Inject
    constructor(
        private val sessionDao: SurveySessionDao,
        private val syncQueueDao: SyncQueueDao,
        private val syncQueueManager: SyncQueueManager,
        private val syncQueueProcessor: SyncQueueProcessor,
    ) : UploadRepository {
        override fun uploadSession(sessionId: String): Flow<UploadProgress> =
            callbackFlow {
                val session = sessionDao.sessionById(sessionId)
                if (session == null) {
                    trySend(UploadProgress.Failed("Session $sessionId not found"))
                    close()
                    return@callbackFlow
                }
                val segments = sessionDao.segmentsFor(sessionId).sortedBy { it.segmentIndex }

                val opIds = opIdsFor(session, segments.size)
                if (session.syncState == null) {
                    enqueueChain(session, segments.size, opIds)
                    sessionDao.updateSession(session.copy(syncState = "queued", updatedAt = Instant.now()))
                }

                val totalBytes =
                    segments.sumOf { it.sizeBytes ?: 0L } +
                        listOfNotNull(session.gpsTrackFilePath, session.luxLogFilePath, session.captureConfigFilePath)
                            .sumOf { File(it).length() }
                val sentByOp = mutableMapOf<String, Long>()
                syncQueueProcessor.processQueuedOps(
                    onRowProgress = { clientOpId, sent, _ ->
                        if (clientOpId in opIds) {
                            sentByOp[clientOpId] = sent
                            trySend(UploadProgress.InProgress(sentByOp.values.sum(), totalBytes))
                        }
                    },
                )

                val statuses = opIds.map { syncQueueDao.statusOf(it) }
                when {
                    statuses.any { it == "conflict" } ->
                        trySend(UploadProgress.Conflict("Dữ liệu đã thay đổi trên server"))
                    statuses.any { it == "failed" } -> trySend(UploadProgress.Failed("Nộp thất bại, thử lại sau"))
                    statuses.all { it == "done" } -> trySend(UploadProgress.Done)
                    else -> trySend(UploadProgress.InProgress(sentByOp.values.sum(), totalBytes))
                }
                close()
                awaitClose { }
            }

        private fun opIdsFor(
            session: LocalSurveySessionEntity,
            clipCount: Int,
        ): List<String> {
            val clipIds = (0 until clipCount).map { "${session.sessionId}:clip:$it" }
            val rawIds = listOf("gps_track", "lux_log", "capture_config").map { "${session.sessionId}:raw:$it" }
            val createId = listOf("${session.sessionId}:create_sweep")
            val submitId = listOf("${session.sessionId}:submit")
            return createId + clipIds + rawIds + submitId
        }

        private suspend fun enqueueChain(
            session: LocalSurveySessionEntity,
            clipCount: Int,
            opIds: List<String>,
        ) {
            val createId = opIds.first()
            syncQueueManager.enqueue(
                opType = "create_survey_sweep",
                payloadJson = Json.encodeToString(CreateSurveySweepPayload(session.sessionId)),
                clientOpId = createId,
            )

            var previousId = createId
            (0 until clipCount).forEach { clipNo ->
                val clipOpId = "${session.sessionId}:clip:$clipNo"
                syncQueueManager.enqueue(
                    opType = "upload_survey_clip",
                    payloadJson = Json.encodeToString(UploadSurveyClipPayload(session.sessionId, clipNo)),
                    clientOpId = clipOpId,
                    dependsOnClientOpId = previousId,
                )
                previousId = clipOpId
            }

            listOf("gps_track", "lux_log", "capture_config").forEach { kind ->
                val rawOpId = "${session.sessionId}:raw:$kind"
                syncQueueManager.enqueue(
                    opType = "upload_survey_raw",
                    payloadJson = Json.encodeToString(UploadSurveyRawPayload(session.sessionId, kind)),
                    clientOpId = rawOpId,
                    dependsOnClientOpId = previousId,
                )
                previousId = rawOpId
            }

            syncQueueManager.enqueue(
                opType = "submit_survey_sweep",
                payloadJson = Json.encodeToString(SubmitSurveySweepPayload(session.sessionId)),
                clientOpId = opIds.last(),
                dependsOnClientOpId = previousId,
            )
        }
    }
