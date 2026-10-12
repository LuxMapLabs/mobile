package com.luxmap.feature.survey.ui.submit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luxmap.core.network.ConnectivityObserver
import com.luxmap.core.sync.SyncScheduler
import com.luxmap.core.sync.UploadPreferences
import com.luxmap.feature.survey.data.SessionFileStatus
import com.luxmap.feature.survey.data.SessionUploadSummary
import com.luxmap.feature.survey.data.UploadProgress
import com.luxmap.feature.survey.data.UploadRepository
import com.luxmap.feature.survey.data.dao.SurveySessionDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SubmitViewModel
    @Inject
    constructor(
        private val repository: UploadRepository,
        private val sessionDao: SurveySessionDao,
        private val connectivityObserver: ConnectivityObserver,
        private val uploadPreferences: UploadPreferences,
        private val syncScheduler: SyncScheduler,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<SubmitUiState>(SubmitUiState.Idle)
        val uiState: StateFlow<SubmitUiState> = _uiState.asStateFlow()

        private val _fileStatuses = MutableStateFlow<List<SessionFileStatus>>(emptyList())
        val fileStatuses: StateFlow<List<SessionFileStatus>> = _fileStatuses.asStateFlow()

        private val _summary = MutableStateFlow<SessionUploadSummary?>(null)
        val summary: StateFlow<SessionUploadSummary?> = _summary.asStateFlow()

        val wifiOnlyUpload: StateFlow<Boolean> =
            uploadPreferences.observeWifiOnlyUpload().stateIn(viewModelScope, SharingStarted.Eagerly, false)

        // Writing the flag alone only affects the next one-time trigger (WorkManagerSyncTrigger
        // reads it fresh every time). The periodic job stays scheduled with whatever NetworkType
        // it started with until something calls reschedulePeriodicSync - this toggle is that
        // something, so flipping it takes effect immediately, not just "next app launch".
        fun onWifiOnlyToggled(enabled: Boolean) {
            viewModelScope.launch {
                uploadPreferences.setWifiOnlyUpload(enabled)
                syncScheduler.reschedulePeriodicSync()
            }
        }

        fun onSubmit(sessionId: String) {
            viewModelScope.launch {
                val session = sessionDao.sessionById(sessionId)
                if (session == null || session.recordingState != "packaged") {
                    _uiState.value = SubmitUiState.NotPackagedYet
                    return@launch
                }

                _summary.value = repository.sessionSummary(sessionId)
                launch { repository.observeFileStatuses(sessionId).collect { _fileStatuses.value = it } }

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
                                is UploadProgress.WaitingForWifi -> SubmitUiState.WaitingForWifi
                            }
                    }
            }
        }
    }
