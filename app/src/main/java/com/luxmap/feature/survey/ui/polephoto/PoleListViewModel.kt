package com.luxmap.feature.survey.ui.polephoto

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luxmap.core.theme.SyncStatus
import com.luxmap.feature.survey.data.PoleListRepository
import com.luxmap.feature.survey.data.PolePhotoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
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

        fun onViewAiResult(photoId: String) {
            _aiResultState.value = PolePhotoResultUiState.Loading
            viewModelScope.launch {
                polePhotoRepository.fetchResult(workOrderId, photoId).fold(
                    onSuccess = { photo ->
                        _aiResultState.value =
                            if (photo.aiStatus == "available") {
                                PolePhotoResultUiState.Success(
                                    cvState = photo.cvState ?: "inconclusive",
                                    confidence = photo.confidence,
                                    modelVersion = photo.modelVersion,
                                    analyzedAt = photo.analyzedAt,
                                    decision = photo.decision,
                                    decisionNote = photo.decisionNote,
                                    decidedBy = photo.decidedBy,
                                    decidedAt = photo.decidedAt,
                                )
                            } else {
                                PolePhotoResultUiState.Empty
                            }
                    },
                    onFailure = { e ->
                        _aiResultState.value = PolePhotoResultUiState.Error(e.message ?: "Không tải được kết quả AI")
                    },
                )
            }
        }

        fun onDismissAiResult() {
            _aiResultState.value = null
        }

        private fun load() {
            viewModelScope.launch {
                poleListRepository.poles(workOrderId).fold(
                    onSuccess = { poles ->
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
                                        latestByPole.mapValues { (_, photo) ->
                                            photo.photoId to statuses[photo.photoId]
                                        }
                                    }
                            }
                            .catch { e -> _uiState.value = PoleListUiState.Error(e.message ?: "Lỗi") }
                            .collect { infoByPole ->
                                _uiState.value =
                                    PoleListUiState.Success(
                                        poles.map { pole ->
                                            val (photoId, status) = infoByPole[pole.poleId] ?: (null to null)
                                            PoleListRowState(
                                                poleId = pole.poleId,
                                                position = pole.position,
                                                fixtureStatus = pole.fixtureStatus,
                                                photoId = photoId,
                                                syncStatus = status?.let(::toSyncStatus),
                                            )
                                        },
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
