package com.luxmap.feature.survey.data.sync

import com.luxmap.core.network.WorkOrdersApi
import com.luxmap.core.network.errorCodeOrNull
import com.luxmap.core.sync.SyncOpHandler
import com.luxmap.core.sync.SyncOpResult
import com.luxmap.feature.survey.data.dao.PolePhotoDao
import com.luxmap.feature.survey.data.dto.PolePhotoReviewRequestDto
import com.luxmap.feature.survey.data.entity.LocalPolePhotoEntity
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException
import java.time.Instant
import javax.inject.Inject

class SubmitPolePhotoReviewSyncHandler
    @Inject
    constructor(
        private val api: WorkOrdersApi,
        private val dao: PolePhotoDao,
    ) : SyncOpHandler {
        override val opType = "submit_pole_photo_review"

        override suspend fun handle(
            payloadJson: String,
            onProgress: suspend (Long, Long) -> Unit,
        ): SyncOpResult {
            val payload = Json.decodeFromString<PolePhotoSyncPayload>(payloadJson)
            val photo = dao.byId(payload.photoId) ?: return SyncOpResult.Done
            val serverPhotoId =
                photo.serverPhotoId ?: return SyncOpResult.Failed("Local pole photo missing server photo id")
            val draftFinalCvState = photo.draftFinalCvState ?: return SyncOpResult.Done

            return try {
                val response =
                    api.reviewPolePhoto(
                        photo.workOrderId,
                        serverPhotoId,
                        PolePhotoReviewRequestDto(
                            finalCvState = draftFinalCvState,
                            reviewNote = photo.draftReviewNote ?: observationNote(photo, draftFinalCvState),
                        ),
                    )
                dao.updateAiResult(
                    photoId = photo.photoId,
                    aiStatus = response.aiStatus,
                    cvState = response.cvState,
                    confidence = response.confidence,
                    modelVersion = response.modelVersion,
                    analyzedAt = response.analyzedAt?.let(Instant::parse),
                    finalCvState = response.finalCvState,
                    reviewNote = response.reviewNote,
                    reviewedBy = response.reviewedBy,
                    reviewedAt = response.reviewedAt?.let(Instant::parse),
                    decision = response.decision,
                    decisionNote = response.decisionNote,
                    decidedBy = response.decidedBy,
                    decidedAt = response.decidedAt?.let(Instant::parse),
                    videoUrl = response.video?.videoUrl,
                )
                dao.updateDraftReview(photo.photoId, null, null)
                SyncOpResult.Done
            } catch (e: HttpException) {
                when (e.code()) {
                    409, 400 -> SyncOpResult.Failed(e.errorCodeOrNull() ?: "HTTP ${e.code()}")
                    else -> SyncOpResult.RetryLater
                }
            } catch (e: IOException) {
                SyncOpResult.RetryLater
            }
        }

        // The server requires a note whenever the label differs from the AI's cv_state. A label the
        // engineer picked at the pole before seeing any AI result explains itself, so say that
        // instead of making them type a reason.
        private fun observationNote(
            photo: LocalPolePhotoEntity,
            finalCvState: String,
        ): String? =
            OBSERVATION_NOTE.takeIf { photo.engineerObservation == finalCvState && photo.cvState != finalCvState }

        private companion object {
            const val OBSERVATION_NOTE = "Kỹ sư quan sát trực tiếp tại cột, chọn trước khi có kết quả AI."
        }
    }
