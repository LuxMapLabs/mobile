package com.luxmap.feature.history.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luxmap.feature.history.data.HistoryRepository
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
class HistoryViewModel
    @Inject
    constructor(
        private val repository: HistoryRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<HistoryUiState>(HistoryUiState.Loading)
        val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

        private var loadJob: Job? = null
        private var hasResumed = false

        init {
            load()
        }

        fun retry() {
            load()
        }

        fun onResume() {
            if (hasResumed) load()
            hasResumed = true
        }

        private fun load() {
            loadJob?.cancel()
            _uiState.value = HistoryUiState.Loading
            loadJob =
                viewModelScope.launch {
                    repository.observeHistory()
                        .catch { e ->
                            _uiState.value =
                                HistoryUiState.Error(
                                    message = e.message ?: "Không tải được lịch sử",
                                    isNetworkError = e is IOException,
                                )
                        }.collect { result ->
                            _uiState.value =
                                if (result.items.isEmpty()) {
                                    HistoryUiState.Empty
                                } else {
                                    HistoryUiState.Success(
                                        items = result.items,
                                        isStale = result.isStale,
                                        lastSyncedAt = result.syncedAt,
                                    )
                                }
                        }
                }
        }
    }
