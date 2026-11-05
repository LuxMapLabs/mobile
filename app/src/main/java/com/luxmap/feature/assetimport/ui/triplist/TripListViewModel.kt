package com.luxmap.feature.assetimport.ui.triplist

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

data class TripPoleItem(
    val poleEntryId: String,
    val seq: Int,
    val photoFilePath: String,
    val timeLabel: String,
    val note: String,
    val manuallyAdjusted: Boolean,
    val coordLabel: String,
    val hasCoord: Boolean,
)

sealed interface TripListUiState {
    data object Loading : TripListUiState

    data class Success(
        val areaLabel: String,
        val startedAtLabel: String,
        val poles: List<TripPoleItem>,
    ) : TripListUiState

    data class Empty(
        val areaLabel: String,
        val startedAtLabel: String,
    ) : TripListUiState

    data class Error(val message: String) : TripListUiState
}

@HiltViewModel
class TripListViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        repository: AssetImportRepository,
    ) : ViewModel() {
        private val tripId: String = checkNotNull(savedStateHandle[TRIP_ID_ARG])

        val uiState: StateFlow<TripListUiState> =
            combine(repository.observeTrip(tripId), repository.observePoles(tripId)) { trip, poles ->
                if (trip == null) {
                    TripListUiState.Error("Không tìm thấy chuyến này")
                } else {
                    val areaLabel = trip.areaCode + " · " + trip.areaTitle
                    val startedAtLabel = DateFormatUtils.formatTimeOnly(trip.startedAt)
                    if (poles.isEmpty()) {
                        TripListUiState.Empty(areaLabel, startedAtLabel)
                    } else {
                        TripListUiState.Success(areaLabel, startedAtLabel, poles.map { it.toItem() })
                    }
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TripListUiState.Loading)

        companion object {
            const val TRIP_ID_ARG = "tripId"
        }
    }

private fun ImportPole.toItem() =
    TripPoleItem(
        poleEntryId = poleEntryId,
        seq = seq,
        photoFilePath = photoFilePath,
        timeLabel = DateFormatUtils.formatTimeOnly(capturedAt),
        note = note,
        manuallyAdjusted = manuallyAdjusted,
        coordLabel = if (lat != null && lng != null) "%.5f, %.5f".format(lat, lng) else "Thiếu toạ độ",
        hasCoord = lat != null && lng != null,
    )
