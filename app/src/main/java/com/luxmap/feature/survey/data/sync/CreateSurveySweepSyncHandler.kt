package com.luxmap.feature.survey.data.sync

import com.luxmap.core.network.SweepsApi
import com.luxmap.core.network.dto.CreateSweepRequestDto
import com.luxmap.core.network.errorCodeOrNull
import com.luxmap.core.sync.SyncOpHandler
import com.luxmap.core.sync.SyncOpResult
import com.luxmap.feature.survey.data.dao.SurveySessionDao
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException
import java.time.Instant
import javax.inject.Inject

class CreateSurveySweepSyncHandler
    @Inject
    constructor(
        private val api: SweepsApi,
        private val dao: SurveySessionDao,
    ) : SyncOpHandler {
        override val opType = "create_survey_sweep"

        override suspend fun handle(
            payloadJson: String,
            onProgress: suspend (Long, Long) -> Unit,
        ): SyncOpResult {
            val payload = Json.decodeFromString<CreateSurveySweepPayload>(payloadJson)
            val session =
                dao.sessionById(payload.sessionId) ?: return SyncOpResult.Failed("Local session row missing")

            // Retried after a partial chain failure downstream (e.g. clip 2 of 3 failed) - do not
            // create a second sweep, reuse the one already assigned (Review Focus item).
            if (session.serverSweepId != null) return SyncOpResult.Done

            return try {
                val response =
                    api.create(
                        CreateSweepRequestDto(
                            workOrderId = session.workOrderId,
                            clientOpId = session.sessionId,
                            // placeholder, see contract-drift.md entry below
                            bootSessionId = session.sessionId,
                            elapsedAnchorNs = session.startedAtElapsedNs.toString(),
                            utcAnchor = session.startedAtUtc.toString(),
                            utcUncertaintyMs = 50.0,
                            dataSource = "field",
                            startedElapsedNs = session.startedAtElapsedNs.toString(),
                        ),
                    )
                dao.updateSession(session.copy(serverSweepId = response.sweepId, updatedAt = Instant.now()))
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
