package com.luxmap.feature.assetimport.ui.review

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luxmap.core.common.DateFormatUtils
import com.luxmap.feature.assetimport.data.AssetImportRepository
import com.luxmap.feature.assetimport.data.ImportPole
import com.luxmap.feature.assetimport.domain.CalculateTripWarningsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ReviewTab { LIST, MAP }

data class ReviewPoleItem(
    val poleEntryId: String,
    val seq: Int,
    val photoFilePath: String,
    val timeLabel: String,
    val note: String,
    val coordLabel: String,
    val hasCoord: Boolean,
    val warningLabel: String?,
)

sealed interface ReviewSubmitUiState {
    data object Loading : ReviewSubmitUiState

    data class Success(
        val tab: ReviewTab,
        val poles: List<ReviewPoleItem>,
        val warningMessages: List<String>,
        val isSubmitting: Boolean,
    ) : ReviewSubmitUiState

    data class Error(val message: String) : ReviewSubmitUiState
}

@HiltViewModel
class ReviewSubmitViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val repository: AssetImportRepository,
        private val calculateWarnings: CalculateTripWarningsUseCase,
    ) : ViewModel() {
        private val tripId: String = checkNotNull(savedStateHandle[TRIP_ID_ARG])
        private val tab = MutableStateFlow(ReviewTab.LIST)
        private val isSubmitting = MutableStateFlow(false)

        val uiState: StateFlow<ReviewSubmitUiState> =
            combine(repository.observePoles(tripId), tab, isSubmitting) { poles, tabValue, submitting ->
                val warningsResult = calculateWarnings(poles)
                ReviewSubmitUiState.Success(
                    tab = tabValue,
                    poles = poles.map { it.toItem(warningsResult.perPoleMessages[it.poleEntryId]) },
                    warningMessages = warningsResult.warnings.map { it.message },
                    isSubmitting = submitting,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReviewSubmitUiState.Loading)

        fun onSelectTab(value: ReviewTab) {
            tab.value = value
        }

        fun onDeletePole(poleEntryId: String) {
            viewModelScope.launch { repository.deletePole(poleEntryId) }
        }

        fun onSubmit(onSubmitted: () -> Unit) {
            if (isSubmitting.value) return
            isSubmitting.value = true
            viewModelScope.launch {
                repository.submitTrip(tripId)
                isSubmitting.value = false
                onSubmitted()
            }
        }

        companion object {
            const val TRIP_ID_ARG = "tripId"
        }
    }

private fun ImportPole.toItem(warningParts: List<String>?) =
    ReviewPoleItem(
        poleEntryId = poleEntryId,
        seq = seq,
        photoFilePath = photoFilePath,
        timeLabel = DateFormatUtils.formatTimeOnly(capturedAt),
        note = note,
        coordLabel = if (lat != null && lng != null) "%.5f, %.5f".format(lat, lng) else "Thiếu toạ độ",
        hasCoord = lat != null && lng != null,
        warningLabel = warningParts?.joinToString(" · "),
    )
