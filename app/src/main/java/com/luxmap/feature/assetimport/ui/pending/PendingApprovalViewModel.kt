package com.luxmap.feature.assetimport.ui.pending

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luxmap.core.common.DateFormatUtils
import com.luxmap.feature.assetimport.data.AssetImportRepository
import com.luxmap.feature.assetimport.data.ImportPole
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class PendingPoleItem(
    val poleEntryId: String,
    val seq: Int,
    val photoFilePath: String,
    val note: String,
)

sealed interface PendingApprovalUiState {
    data object Loading : PendingApprovalUiState

    data class Success(
        val areaLabel: String,
        val submittedAtLabel: String,
        val poles: List<PendingPoleItem>,
        val firstSeq: Int,
        val lastSeq: Int,
    ) : PendingApprovalUiState

    data class Error(val message: String) : PendingApprovalUiState
}

@HiltViewModel
class PendingApprovalViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        repository: AssetImportRepository,
    ) : ViewModel() {
        private val tripId: String = checkNotNull(savedStateHandle[TRIP_ID_ARG])

        val uiState: StateFlow<PendingApprovalUiState> =
            combine(repository.observeTrip(tripId), repository.observePoles(tripId)) { trip, poles ->
                if (trip == null || poles.isEmpty()) {
                    PendingApprovalUiState.Error("Không tìm thấy chuyến này")
                } else {
                    PendingApprovalUiState.Success(
                        areaLabel = trip.areaCode + " · " + trip.areaTitle,
                        submittedAtLabel = DateFormatUtils.formatTimeOnly(trip.submittedAt),
                        poles = poles.map { it.toItem() },
                        firstSeq = poles.first().seq,
                        lastSeq = poles.last().seq,
                    )
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PendingApprovalUiState.Loading)

        companion object {
            const val TRIP_ID_ARG = "tripId"
        }
    }

private fun ImportPole.toItem() =
    PendingPoleItem(poleEntryId = poleEntryId, seq = seq, photoFilePath = photoFilePath, note = note)
