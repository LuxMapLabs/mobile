package com.luxmap.feature.map.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luxmap.feature.map.data.PoleDetailRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PoleDetailViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        repository: PoleDetailRepository,
    ) : ViewModel() {
        private val poleId: String = checkNotNull(savedStateHandle[POLE_ID_ARG])

        private val _uiState = MutableStateFlow<PoleDetailUiState>(PoleDetailUiState.Loading)
        val uiState: StateFlow<PoleDetailUiState> = _uiState.asStateFlow()

        init {
            viewModelScope.launch {
                repository
                    .observePoleDetail(poleId)
                    .catch { e ->
                        _uiState.value = PoleDetailUiState.Error(e.message ?: "Không tải được dữ liệu cột đèn")
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
