package com.luxmap.feature.polelocation.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luxmap.feature.polelocation.data.PoleLocationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PoleLocationListViewModel
    @Inject
    constructor(
        private val repository: PoleLocationRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<PoleLocationListUiState>(PoleLocationListUiState.Loading)
        val uiState: StateFlow<PoleLocationListUiState> = _uiState.asStateFlow()

        init {
            load()
        }

        fun onRetry() {
            _uiState.value = PoleLocationListUiState.Loading
            load()
        }

        private fun load() {
            viewModelScope.launch {
                repository.polesMissingLocation().fold(
                    onSuccess = { items ->
                        _uiState.value =
                            if (items.isEmpty()) {
                                PoleLocationListUiState.Empty
                            } else {
                                PoleLocationListUiState.Success(items)
                            }
                    },
                    onFailure = { e ->
                        _uiState.value = PoleLocationListUiState.Error(e.message ?: "Không tải được danh sách cột")
                    },
                )
            }
        }
    }
