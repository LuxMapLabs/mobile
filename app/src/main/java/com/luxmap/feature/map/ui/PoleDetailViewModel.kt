package com.luxmap.feature.map.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luxmap.feature.map.data.PoleDetailRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

@HiltViewModel
class PoleDetailViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val repository: PoleDetailRepository,
    ) : ViewModel() {
        private val poleId: String = checkNotNull(savedStateHandle[POLE_ID_ARG])

        private val _uiState = MutableStateFlow<PoleDetailUiState>(PoleDetailUiState.Loading)
        val uiState: StateFlow<PoleDetailUiState> = _uiState.asStateFlow()

        private var loadJob: Job? = null

        init {
            load()
        }

        fun retry() {
            _uiState.value = PoleDetailUiState.Loading
            load()
        }

        private fun load() {
            loadJob?.cancel()
            loadJob =
                viewModelScope.launch {
                    repository
                        .observePoleDetail(poleId)
                        .catch { e ->
                            _uiState.value =
                                PoleDetailUiState.Error(
                                    message = e.message ?: "Không tải được dữ liệu cột đèn",
                                    isNetworkError = e is IOException,
                                )
                        }.collect { detail ->
                            _uiState.value =
                                if (detail == null) {
                                    PoleDetailUiState.Empty
                                } else {
                                    PoleDetailUiState.Success(detail = detail)
                                }
                        }
                }
        }

        companion object {
            const val POLE_ID_ARG = "poleId"
        }
    }
