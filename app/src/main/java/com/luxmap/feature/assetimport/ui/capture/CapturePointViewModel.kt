package com.luxmap.feature.assetimport.ui.capture

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luxmap.core.location.LocationTracker
import com.luxmap.feature.assetimport.data.AssetImportRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import javax.inject.Inject

data class PendingShot(
    val file: File,
    val lat: Double?,
    val lng: Double?,
)

private data class Draft(
    val pendingShot: PendingShot? = null,
    val manuallyAdjusted: Boolean = false,
    val note: String = "",
)

sealed interface CapturePointUiState {
    data object Loading : CapturePointUiState

    data class Ready(
        val poleCount: Int,
        val nextSeq: Int,
        val pendingShot: PendingShot?,
        val manuallyAdjusted: Boolean,
        val note: String,
        val isSaving: Boolean,
        val justSavedSeq: Int?,
    ) : CapturePointUiState

    data class Error(val message: String) : CapturePointUiState
}

@HiltViewModel
class CapturePointViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val repository: AssetImportRepository,
        private val locationTracker: LocationTracker,
    ) : ViewModel() {
        private val tripId: String = checkNotNull(savedStateHandle[TRIP_ID_ARG])
        val pendingOpId: String = UUID.randomUUID().toString()

        private val draft = MutableStateFlow(Draft())
        private val isSaving = MutableStateFlow(false)
        private val justSavedSeq = MutableStateFlow<Int?>(null)

        val uiState: StateFlow<CapturePointUiState> =
            combine(
                repository.observePoles(tripId),
                draft,
                isSaving,
                justSavedSeq,
            ) { poles, draftValue, savingValue, savedSeq ->
                CapturePointUiState.Ready(
                    poleCount = poles.size,
                    nextSeq = poles.size + 1,
                    pendingShot = draftValue.pendingShot,
                    manuallyAdjusted = draftValue.manuallyAdjusted,
                    note = draftValue.note,
                    isSaving = savingValue,
                    justSavedSeq = savedSeq,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CapturePointUiState.Loading)

        fun outputFile(filesDir: File): File {
            val dir = File(filesDir, "asset-import/$tripId")
            dir.mkdirs()
            return File(dir, "$pendingOpId.jpg")
        }

        fun onPhotoCaptured(file: File) {
            viewModelScope.launch {
                val location = locationTracker.getCurrentLocation()
                draft.value = Draft(pendingShot = PendingShot(file, location?.latitude, location?.longitude))
            }
        }

        fun onRetake() {
            draft.value.pendingShot?.file?.delete()
            draft.value = Draft()
        }

        fun onNoteChanged(value: String) {
            draft.value = draft.value.copy(note = value)
        }

        fun onSave() {
            val current = uiState.value as? CapturePointUiState.Ready ?: return
            val shot = current.pendingShot ?: return
            if (current.note.isBlank() || current.isSaving) return
            isSaving.value = true
            val savedSeq = current.nextSeq
            viewModelScope.launch {
                repository.addPole(
                    tripId = tripId,
                    photoFilePath = shot.file.absolutePath,
                    lat = shot.lat,
                    lng = shot.lng,
                    manuallyAdjusted = current.manuallyAdjusted,
                    note = current.note.trim(),
                )
                draft.value = Draft()
                isSaving.value = false
                justSavedSeq.value = savedSeq
            }
        }

        companion object {
            const val TRIP_ID_ARG = "tripId"
        }
    }
