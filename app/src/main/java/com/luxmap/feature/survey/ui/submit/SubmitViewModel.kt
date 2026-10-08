package com.luxmap.feature.survey.ui.submit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luxmap.feature.survey.data.UploadProgress
import com.luxmap.feature.survey.data.UploadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SubmitViewModel
    @Inject
    constructor(
        private val repository: UploadRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<SubmitUiState>(SubmitUiState.Idle)
        val uiState: StateFlow<SubmitUiState> = _uiState.asStateFlow()

        fun onSubmit(sessionId: String) {
            viewModelScope.launch {
                repository
                    .uploadSession(sessionId)
                    .catch { e -> _uiState.value = SubmitUiState.Error(e.message ?: "Tải lên thất bại") }
                    .collect { progress ->
                        _uiState.value =
                            when (progress) {
                                is UploadProgress.InProgress ->
                                    SubmitUiState.Uploading(progress.bytesSent, progress.totalBytes)
                                is UploadProgress.Done -> SubmitUiState.Done
                                is UploadProgress.Failed -> SubmitUiState.Error(progress.reason)
                                is UploadProgress.Conflict -> SubmitUiState.Error(progress.reason)
                            }
                    }
            }
        }
    }
