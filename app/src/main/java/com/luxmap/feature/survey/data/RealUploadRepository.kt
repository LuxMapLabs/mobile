package com.luxmap.feature.survey.data

import androidx.room.withTransaction
import com.luxmap.core.database.AppDatabase
import com.luxmap.core.network.ConnectivityObserver
import com.luxmap.core.sync.SyncQueueDao
import com.luxmap.core.sync.SyncQueueManager
import com.luxmap.core.sync.SyncQueueProcessor
import com.luxmap.core.sync.UploadPreferences
import com.luxmap.feature.survey.data.dao.SurveySessionDao
import com.luxmap.feature.survey.data.entity.LocalSurveySessionEntity
import com.luxmap.feature.survey.data.sync.CreateSurveySweepPayload
import com.luxmap.feature.survey.data.sync.SubmitSurveySweepPayload
import com.luxmap.feature.survey.data.sync.UploadSurveyClipPayload
import com.luxmap.feature.survey.data.sync.UploadSurveyRawPayload
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant
import javax.inject.Inject

class RealUploadRepository
    @Inject
    constructor(
        private val database: AppDatabase,
        private val sessionDao: SurveySessionDao,
        private val syncQueueDao: SyncQueueDao,
        private val syncQueueManager: SyncQueueManager,
        private val syncQueueProcessor: SyncQueueProcessor,
        private val uploadPreferences: UploadPreferences,
        private val connectivityObserver: ConnectivityObserver,
    ) : UploadRepository {
        override fun uploadSession(sessionId: String): Flow<UploadProgress> =
            flow {
                val session = sessionDao.sessionById(sessionId)
                if (session == null) {
                    emit(UploadProgress.Failed("Session $sessionId not found"))
                    return@flow
                }
                val segments = sessionDao.segmentsFor(sessionId).sortedBy { it.segmentIndex }

                val opIds = opIdsFor(session, segments.size)
                // claimForUpload and enqueueChain must commit or roll back together - without
                // this, a cancellation between the two (user backs out, process dies) leaves the
                // session claimed with a partial or empty chain, and nothing ever re-claims a
                // session whose syncState is already non-null.
                val justClaimed =
                    database.withTransaction {
                        val claimed = sessionDao.claimForUpload(sessionId, Instant.now()) == 1
                        if (claimed) {
                            enqueueChain(session, segments.size, opIds)
                        }
                        claimed
                    }
                if (!justClaimed) {
                    // Either a genuine retry (syncState was already non-null), or this call lost
                    // a race against a concurrent uploadSession call that claimed the session
                    // first - either way the chain is already enqueued, only a failed op (if any)
                    // needs resetting so queuedRows() picks it up again.
                    syncQueueDao.resetFailedOps(opIds, Instant.now())
                }

                // The chain above is enqueued either way - only this foreground drain attempt is
                // skipped. SyncQueueManager.enqueue() already triggered the background SyncWorker
                // (wifi-only-aware itself), which picks this up once a wifi network is available.
                if (uploadPreferences.observeWifiOnlyUpload().first() && !connectivityObserver.isOnWifi.first()) {
                    emit(UploadProgress.WaitingForWifi)
                    return@flow
                }

                val totalBytes =
                    segments.sumOf { it.sizeBytes ?: 0L } +
                        listOfNotNull(session.gpsTrackFilePath, session.luxLogFilePath, session.captureConfigFilePath)
                            .sumOf { File(it).length() }
                // Emitted before processQueuedOps so the screen never looks frozen even if this
                // call loses the SyncQueueProcessor Mutex to a background SyncWorker that drains
                // the whole chain before onRowProgress ever fires for this caller.
                emit(UploadProgress.InProgress(0L, totalBytes))
                val sentByOp = mutableMapOf<String, Long>()
                syncQueueProcessor.processQueuedOps(
                    onRowProgress = { clientOpId, sent, _ ->
                        if (clientOpId in opIds) {
                            sentByOp[clientOpId] = sent
                            emit(UploadProgress.InProgress(sentByOp.values.sum(), totalBytes))
                        }
                    },
                )

                val statuses = opIds.map { syncQueueDao.statusOf(it) }
                when {
                    statuses.any { it == "conflict" } ->
                        emit(UploadProgress.Conflict("Dữ liệu đã thay đổi trên server"))
                    statuses.any { it == "failed" } -> emit(UploadProgress.Failed("Nộp thất bại, thử lại sau"))
                    statuses.all { it == "done" } -> emit(UploadProgress.Done)
                    else -> emit(UploadProgress.InProgress(sentByOp.values.sum(), totalBytes))
                }
            }

        override fun observeFileStatuses(sessionId: String): Flow<List<SessionFileStatus>> =
            syncQueueDao.observeOpsForSession("$sessionId:%").map { rows ->
                rows.map { row ->
                    SessionFileStatus(label = labelForOp(row.clientOpId, sessionId), status = row.status)
                }
            }

        override suspend fun sessionSummary(sessionId: String): SessionUploadSummary? {
            val session = sessionDao.sessionById(sessionId) ?: return null
            val segments = sessionDao.segmentsFor(sessionId)
            val totalBytes =
                segments.sumOf { it.sizeBytes ?: 0L } +
                    listOfNotNull(session.gpsTrackFilePath, session.luxLogFilePath, session.captureConfigFilePath)
                        .sumOf { File(it).length() }
            return SessionUploadSummary(
                durationSeconds = session.durationSeconds,
                distanceMeters = session.distanceMeters,
                totalBytes = totalBytes,
                segmentCount = segments.size,
            )
        }

        private fun labelForOp(
            clientOpId: String,
            sessionId: String,
        ): String =
            when (val suffix = clientOpId.removePrefix("$sessionId:")) {
                "create_sweep" -> "Tạo phiên khảo sát"
                "submit" -> "Nộp phiên khảo sát"
                "raw:gps_track" -> "Log GPS"
                "raw:lux_log" -> "Log lux"
                "raw:capture_config" -> "Cấu hình quay"
                else ->
                    suffix.removePrefix("clip:").toIntOrNull()?.let { "Video đoạn ${it + 1}" }
                        ?: suffix
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
