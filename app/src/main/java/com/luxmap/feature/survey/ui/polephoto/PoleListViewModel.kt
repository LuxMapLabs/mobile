package com.luxmap.feature.survey.ui.polephoto

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luxmap.core.security.TokenStore
import com.luxmap.core.theme.SyncStatus
import com.luxmap.feature.survey.data.LuxReadingRepository
import com.luxmap.feature.survey.data.PoleListRepository
import com.luxmap.feature.survey.data.PolePhotoRepository
import com.luxmap.feature.survey.data.entity.LocalPolePhotoEntity
import com.luxmap.feature.survey.data.latestForPhoto
import com.luxmap.feature.workorder.data.WorkOrderCompletionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PoleListViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val poleListRepository: PoleListRepository,
        private val polePhotoRepository: PolePhotoRepository,
        private val completionRepository: WorkOrderCompletionRepository,
        private val tokenStore: TokenStore,
        private val luxReadingRepository: LuxReadingRepository,
    ) : ViewModel() {
        private val workOrderId: String = checkNotNull(savedStateHandle[WORK_ORDER_ID_ARG])
        private val _uiState = MutableStateFlow<PoleListUiState>(PoleListUiState.Loading)
        val uiState: StateFlow<PoleListUiState> = _uiState.asStateFlow()

        private val _aiResultState = MutableStateFlow<PolePhotoResultUiState?>(null)
        val aiResultState: StateFlow<PolePhotoResultUiState?> = _aiResultState.asStateFlow()

        // A successful PATCH .../review only changes a small "Da review luc ..." timestamp in
        // the sheet, easy to miss - checked on a real device 2026-10-29 (the request returns 200
        // but nothing visibly confirms it). This one-shot event lets the Route show a snackbar.

        // "Hoan tat" used to navigate back immediately with zero feedback, and the Hub card
        // looked completely unchanged on return - found on a real device, 2026-10-29. This
        // one-shot event lets the Route show a toast right on this screen instead.
        private val _surveyCompleted = MutableSharedFlow<Int>(extraBufferCapacity = 1)
        val surveyCompleted: SharedFlow<Int> = _surveyCompleted.asSharedFlow()

        init {
            load()
        }

        fun onRetry() {
            _uiState.value = PoleListUiState.Loading
            load()
        }

        fun onViewAiResult(serverPhotoId: String) {
            viewModelScope.launch {
                // Offline-first: show whatever is already cached in Room immediately (marked
                // stale), instead of a Loading spinner - only fall back to Loading when there is
                // nothing cached yet. A failed refresh then keeps the stale cache on screen
                // rather than replacing it with an error.
                val cachedPhoto = polePhotoRepository.cachedResult(serverPhotoId)
                val cached = cachedPhoto?.toResultUiState(isStale = true, luxValue = luxFor(cachedPhoto))
                _aiResultState.value = cached ?: PolePhotoResultUiState.Loading

                polePhotoRepository.fetchResult(workOrderId, serverPhotoId).fold(
                    onSuccess = { photo ->
                        val fresh = photo.toResultUiState(isStale = false, luxValue = luxFor(photo))
                        _aiResultState.value = fresh ?: PolePhotoResultUiState.Empty
                    },
                    onFailure = { e ->
                        _aiResultState.value =
                            cached ?: PolePhotoResultUiState.Error(
                                message = e.message ?: "Không tải được kết quả AI",
                                isNetworkError = e is IOException,
                            )
                    },
                )
            }
        }

        // null means no usable cached AI result yet (aiStatus itself is null - never fetched).
        // Both "available" and "unavailable" map to Success now - even when the AI model failed
        // entirely (cvState null), the Field Engineer must still be able to review it (business
        // rule D-2, ticket PHOTO-REVIEW) - there is no longer a separate "AI failed" dead end.
        private suspend fun luxFor(photo: LocalPolePhotoEntity): Double? =
            luxReadingRepository.observeReadings(workOrderId).first().latestForPhoto(photo.photoId)?.luxValue

        private fun LocalPolePhotoEntity.toResultUiState(
            isStale: Boolean,
            luxValue: Double?,
        ): PolePhotoResultUiState? =
            if (aiStatus == null) {
                null
            } else {
                PolePhotoResultUiState.Success(
                    filePath = filePath,
                    cvState = cvState,
                    confidence = confidence,
                    modelVersion = modelVersion,
                    analyzedAt = analyzedAt,
                    finalCvState = finalCvState,
                    reviewNote = reviewNote,
                    reviewedBy = reviewedBy,
                    reviewedAt = reviewedAt,
                    decision = decision,
                    decisionNote = decisionNote,
                    decidedBy = decidedBy,
                    decidedAt = decidedAt,
                    draftFinalCvState = draftFinalCvState,
                    draftReviewNote = draftReviewNote,
                    isStale = isStale,
                    mediaType = mediaType,
                    videoUrl = videoUrl,
                    luxValue = luxValue,
                )
            }

        suspend fun currentAuthorizationHeader(): String? =
            tokenStore.currentTokens()?.let { "Bearer ${it.accessToken}" }

        fun onDismissAiResult() {
            _aiResultState.value = null
        }

        suspend fun withdrawResult(serverPhotoId: String) {
            polePhotoRepository.withdrawResult(workOrderId, serverPhotoId)
            onDismissAiResult()
        }

        // Used to just navigate back right away (PoleListRoute's onDone), leaving the work order
        // stuck "in_progress" forever - there was no other call site for POST
        // /work-orders/{id}/complete for a survey work order.
        fun onComplete() {
            val state = _uiState.value as? PoleListUiState.Success ?: return
            if (!state.canComplete) return
            viewModelScope.launch {
                val pendingReviews = polePhotoRepository.enqueuePendingReviews(workOrderId)
                completionRepository.submitCompletion(
                    workOrderId = workOrderId,
                    taskKind = "survey",
                    reportNote = "Đã xác nhận kết quả khảo sát ${state.confirmedCount} cột đèn.",
                    materialsUsed = null,
                    faultOutcomes = null,
                )
                _surveyCompleted.tryEmit(pendingReviews)
            }
        }

        // Saves the Field Engineer's correction locally only - sent later, in bulk, by
        // enqueuePendingReviews when "Hoan tat" is tapped (ticket batch-submit-review, 2026-11-10).
        fun onSubmitReview(
            serverPhotoId: String,
            finalCvState: String,
            reviewNote: String?,
        ) {
            val current = _aiResultState.value as? PolePhotoResultUiState.Success ?: return
            viewModelScope.launch {
                polePhotoRepository.saveDraftReview(serverPhotoId, finalCvState, reviewNote)
                _aiResultState.value = current.copy(draftFinalCvState = finalCvState, draftReviewNote = reviewNote)
            }
        }

        private data class PoleSyncInfo(
            val photoId: String,
            val serverPhotoId: String?,
            val status: String?,
            val hasPendingReview: Boolean,
            val hasPendingDecision: Boolean,
            val aiVerdict: PoleAiVerdict?,
            val isConfirmed: Boolean,
        )

        private fun load() {
            viewModelScope.launch {
                completionRepository.releaseReturnedCompletions(listOf(workOrderId))
                poleListRepository.poles(workOrderId).fold(
                    onSuccess = { result ->
                        val poles = result.poles
                        if (poles.isEmpty()) {
                            _uiState.value = PoleListUiState.Empty
                            return@launch
                        }
                        val infoByPoleFlow =
                            polePhotoRepository
                                .observePhotos(workOrderId)
                                .flatMapLatest { photos ->
                                    val latestByPole = photos.groupBy { it.poleId }.mapValues { it.value.first() }
                                    polePhotoRepository
                                        .observeUploadStatuses(latestByPole.values.map { it.photoId })
                                        .map { statuses ->
                                            latestByPole.mapValues { (_, photo) ->
                                                PoleSyncInfo(
                                                    photoId = photo.photoId,
                                                    serverPhotoId = photo.serverPhotoId,
                                                    status = statuses[photo.photoId],
                                                    hasPendingReview = photo.draftFinalCvState != null,
                                                    isConfirmed =
                                                        isConclusive(photo.draftFinalCvState ?: photo.finalCvState),
                                                    hasPendingDecision =
                                                        photo.reviewedBy != null && photo.decision == null,
                                                    aiVerdict =
                                                        poleAiVerdict(
                                                            photo.aiStatus,
                                                            photo.cvState,
                                                            photo.finalCvState,
                                                            photo.draftFinalCvState,
                                                        ),
                                                )
                                            }
                                        }
                                }
                        combine(
                            infoByPoleFlow,
                            completionRepository.observeCompletion(workOrderId),
                            luxReadingRepository.observeReadings(workOrderId),
                        ) { infoByPole, completion, luxReadings ->
                            PoleListUiState.Success(
                                poles =
                                    poles.map { pole ->
                                        val info = infoByPole[pole.poleId]
                                        PoleListRowState(
                                            poleId = pole.poleId,
                                            position = pole.position,
                                            identification = pole,
                                            fixtureStatus = pole.fixtureStatus,
                                            serverPhotoId = info?.serverPhotoId,
                                            syncStatus = info?.status?.let(::toSyncStatus),
                                            hasPendingReview = info?.hasPendingReview ?: false,
                                            hasPendingDecision = info?.hasPendingDecision ?: false,
                                            aiVerdict = info?.aiVerdict,
                                            isConfirmed = info?.isConfirmed == true && info.status == "done",
                                            lux =
                                                info?.let { luxReadings.latestForPhoto(it.photoId) }?.let { reading ->
                                                    PoleLuxInfo(reading.luxValue, reading.syncStatus == "failed")
                                                },
                                        )
                                    },
                                isStale = result.isStale,
                                isCompleted = completion != null,
                            )
                        }
                            .catch { e -> _uiState.value = PoleListUiState.Error(e.message ?: "Lỗi", e is IOException) }
                            .collect { _uiState.value = it }
                    },
                    onFailure = { e ->
                        _uiState.value =
                            PoleListUiState.Error(
                                message = e.message ?: "Không tải được danh sách cột",
                                isNetworkError = e is IOException,
                            )
                    },
                )
            }
        }

        private fun toSyncStatus(status: String): SyncStatus =
            when (status) {
                "syncing" -> SyncStatus.SYNCING
                "failed" -> SyncStatus.FAILED
                "conflict" -> SyncStatus.CONFLICT
                "done" -> SyncStatus.DONE
                else -> SyncStatus.QUEUED_ONLINE
            }

        companion object {
            const val WORK_ORDER_ID_ARG = "workOrderId"
        }
    }
