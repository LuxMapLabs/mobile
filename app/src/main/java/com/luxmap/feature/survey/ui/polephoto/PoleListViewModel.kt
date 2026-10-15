package com.luxmap.feature.survey.ui.polephoto

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luxmap.feature.survey.data.PoleListRepository
import com.luxmap.feature.survey.data.PolePhotoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

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

        init {
            load()
        }

        fun onRetry() {
            _uiState.value = PoleListUiState.Loading
            load()
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
                            .catch { e -> _uiState.value = PoleListUiState.Error(e.message ?: "Lỗi") }
                            .collect { photos ->
                                val capturedIds = photos.map { it.poleId }.toSet()
                                _uiState.value =
                                    PoleListUiState.Success(
                                        poles.map { pole ->
                                            PoleListRowState(
                                                poleId = pole.poleId,
                                                position = pole.position,
                                                fixtureStatus = pole.fixtureStatus,
                                                isCaptured = pole.poleId in capturedIds,
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

        companion object {
            const val WORK_ORDER_ID_ARG = "workOrderId"
        }
    }
