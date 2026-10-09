package com.luxmap.feature.survey.ui.submit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luxmap.core.network.ConnectivityObserver
import com.luxmap.feature.survey.data.UploadProgress
import com.luxmap.feature.survey.data.UploadRepository
import com.luxmap.feature.survey.data.dao.SurveySessionDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SubmitViewModel
    @Inject
    constructor(
        private val repository: UploadRepository,
        private val sessionDao: SurveySessionDao,
        private val connectivityObserver: ConnectivityObserver,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<SubmitUiState>(SubmitUiState.Idle)
        val uiState: StateFlow<SubmitUiState> = _uiState.asStateFlow()

        fun onSubmit(sessionId: String) {
            viewModelScope.launch {
                val session = sessionDao.sessionById(sessionId)
                if (session == null || session.recordingState != "packaged") {
                    _uiState.value = SubmitUiState.NotPackagedYet
                    return@launch
                }

                repository
                    .uploadSession(sessionId)
                    .catch { e -> _uiState.value = SubmitUiState.Failed(e.message ?: "Tải lên thất bại") }
                    .collect { progress ->
                        _uiState.value =
                            when (progress) {
                                is UploadProgress.InProgress -> {
                                    val isOffline = !connectivityObserver.isOnline.first()
                                    SubmitUiState.Uploading(progress.bytesSent, progress.totalBytes, isOffline)
                                }
                                is UploadProgress.Done -> SubmitUiState.Done
                                is UploadProgress.Failed -> SubmitUiState.Failed(progress.reason)
                                is UploadProgress.Conflict -> SubmitUiState.Conflict(progress.reason)
                            }
                    }
            }
        }
    }
