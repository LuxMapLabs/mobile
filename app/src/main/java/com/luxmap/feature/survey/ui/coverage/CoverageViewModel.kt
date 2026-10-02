package com.luxmap.feature.survey.ui.coverage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luxmap.feature.survey.data.SurveyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CoverageViewModel
    @Inject
    constructor(
        private val repository: SurveyRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<CoverageUiState>(CoverageUiState.Loading)
        val uiState: StateFlow<CoverageUiState> = _uiState.asStateFlow()

        // One-shot: the screen collects this to navigate back into F04 once the old session is
        // gone, instead of this ViewModel reaching into a NavController it does not own.
        private val _redoCompleted = MutableSharedFlow<String>(extraBufferCapacity = 1)
        val redoCompleted: SharedFlow<String> = _redoCompleted.asSharedFlow()

        fun loadSegments(sessionId: String) {
            viewModelScope.launch {
                runCatching { repository.segmentFilePathsFor(sessionId) }
                    .onSuccess { paths ->
                        _uiState.value =
                            if (paths.isEmpty()) CoverageUiState.Empty else CoverageUiState.Success(paths)
                    }
                    .onFailure { error ->
                        _uiState.value = CoverageUiState.Error(error.message ?: "Không đọc được phiên khảo sát")
                    }
            }
        }

        // Called when the player hits a mid-playback error (e.g. a segment file missing on
        // disk). Kept as a flag on the existing Success state, not a new top-level state - same
        // pattern CaptureUiState.Recording uses for gpsSignalLost/bleGapDetected.
        fun onPlayerError(message: String) {
            (_uiState.value as? CoverageUiState.Success)?.let { current ->
                _uiState.value = current.copy(playerErrorMessage = message)
            }
        }

        fun onRedoConfirmed(sessionId: String) {
            viewModelScope.launch {
                val surveySweepId = repository.discardSession(sessionId)
                _redoCompleted.emit(surveySweepId)
            }
        }
    }
