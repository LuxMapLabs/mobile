package com.luxmap.feature.survey.ui.polephoto

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luxmap.core.network.errorCodeOrNull
import com.luxmap.core.theme.SyncStatus
import com.luxmap.feature.survey.data.PoleListRepository
import com.luxmap.feature.survey.data.PolePhotoRepository
import com.luxmap.feature.survey.data.entity.LocalPolePhotoEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import retrofit2.HttpException
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PoleListViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val poleListRepository: PoleListRepository,
        private val polePhotoRepository: PolePhotoRepository,
    ) : ViewModel() {
        private val workOrderId: String = checkNotNull(savedStateHandle[WORK_ORDER_ID_ARG])
        private val _uiState = MutableStateFlow<PoleListUiState>(PoleListUiState.Loading)
        val uiState: StateFlow<PoleListUiState> = _uiState.asStateFlow()

        private val _aiResultState = MutableStateFlow<PolePhotoResultUiState?>(null)
        val aiResultState: StateFlow<PolePhotoResultUiState?> = _aiResultState.asStateFlow()

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
                val cached = polePhotoRepository.cachedResult(serverPhotoId)?.toResultUiState(isStale = true)
                _aiResultState.value = cached ?: PolePhotoResultUiState.Loading

                polePhotoRepository.fetchResult(workOrderId, serverPhotoId).fold(
                    onSuccess = { photo ->
                        _aiResultState.value = photo.toResultUiState(isStale = false) ?: PolePhotoResultUiState.Empty
                    },
                    onFailure = { e ->
                        _aiResultState.value =
                            cached ?: PolePhotoResultUiState.Error(e.message ?: "Không tải được kết quả AI")
                    },
                )
            }
        }

        // null means no usable cached AI result yet (aiStatus itself is null - never fetched).
        // Both "available" and "unavailable" map to Success now - even when the AI model failed
        // entirely (cvState null), the Field Engineer must still be able to review it (business
        // rule D-2, ticket PHOTO-REVIEW) - there is no longer a separate "AI failed" dead end.
        private fun LocalPolePhotoEntity.toResultUiState(isStale: Boolean): PolePhotoResultUiState? =
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
                    isStale = isStale,
                )
            }

        fun onDismissAiResult() {
            _aiResultState.value = null
        }

        // Field Engineer corrects/confirms cvState before a Manager decides (ticket PHOTO-REVIEW,
        // 2026-10-20). Direct call like fetchResult/start - not queued through sync_queue, since
        // it is a foreground state-correction action, not a file upload needing offline resilience.
        fun onSubmitReview(
            serverPhotoId: String,
            finalCvState: String,
            reviewNote: String?,
        ) {
            val current = _aiResultState.value as? PolePhotoResultUiState.Success ?: return
            _aiResultState.value = current.copy(isSubmittingReview = true, reviewError = null)
            viewModelScope.launch {
                polePhotoRepository.reviewPhoto(workOrderId, serverPhotoId, finalCvState, reviewNote).fold(
                    onSuccess = { photo ->
                        _aiResultState.value = photo.toResultUiState(isStale = false) ?: PolePhotoResultUiState.Empty
                    },
                    onFailure = { e ->
                        val latest = _aiResultState.value as? PolePhotoResultUiState.Success ?: current
                        _aiResultState.value =
                            latest.copy(isSubmittingReview = false, reviewError = reviewErrorMessage(e))
                    },
                )
            }
        }

        private fun reviewErrorMessage(e: Throwable): String =
            when ((e as? HttpException)?.errorCodeOrNull()) {
                "POLE_PHOTO_ALREADY_DECIDED" -> "Ảnh này đã được Kỹ sư bảo trì công bố, không thể sửa nhãn nữa"
                "WORK_ORDER_NOT_IN_PROGRESS" -> "Phiếu công việc không còn ở trạng thái đang xử lý"
                else -> e.message ?: "Không gửi được, thử lại"
            }

        private fun load() {
            viewModelScope.launch {
                poleListRepository.poles(workOrderId).fold(
                    onSuccess = { result ->
                        val poles = result.poles
                        if (poles.isEmpty()) {
                            _uiState.value = PoleListUiState.Empty
                            return@launch
                        }
                        polePhotoRepository
                            .observePhotos(workOrderId)
                            .flatMapLatest { photos ->
                                val latestByPole = photos.groupBy { it.poleId }.mapValues { it.value.first() }
                                polePhotoRepository
                                    .observeUploadStatuses(latestByPole.values.map { it.photoId })
                                    .map {
                                            statuses ->
                                        // Keyed by local photoId (sync_queue.client_op_id) to look up status, but
                                        // the row only needs serverPhotoId - the local id is never shown in the UI.
                                        latestByPole.mapValues { (_, photo) ->
                                            photo.serverPhotoId to statuses[photo.photoId]
                                        }
                                    }
                            }
                            .catch { e -> _uiState.value = PoleListUiState.Error(e.message ?: "Lỗi") }
                            .collect { infoByPole ->
                                _uiState.value =
                                    PoleListUiState.Success(
                                        poles =
                                            poles.map { pole ->
                                                val (serverPhotoId, status) = infoByPole[pole.poleId] ?: (null to null)
                                                PoleListRowState(
                                                    poleId = pole.poleId,
                                                    position = pole.position,
                                                    fixtureStatus = pole.fixtureStatus,
                                                    serverPhotoId = serverPhotoId,
                                                    syncStatus = status?.let(::toSyncStatus),
                                                )
                                            },
                                        isStale = result.isStale,
                                    )
                            }
                    },
                    onFailure = { e ->
                        _uiState.value = PoleListUiState.Error(e.message ?: "Không tải được danh sách cột")
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
