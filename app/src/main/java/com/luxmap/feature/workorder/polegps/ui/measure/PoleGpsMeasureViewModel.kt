package com.luxmap.feature.workorder.polegps.ui.measure

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luxmap.feature.workorder.polegps.data.GpsFix
import com.luxmap.feature.workorder.polegps.data.GpsFixSource
import com.luxmap.feature.workorder.polegps.data.MeasurementSource
import com.luxmap.feature.workorder.polegps.data.PoleGpsRepository
import com.luxmap.feature.workorder.polegps.data.PoleMeasurement
import com.luxmap.feature.workorder.polegps.data.PoleReference
import com.luxmap.feature.workorder.polegps.domain.FixQuality
import com.luxmap.feature.workorder.polegps.domain.PoleFixGate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PoleGpsMeasureViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val repository: PoleGpsRepository,
        private val fixSource: GpsFixSource,
    ) : ViewModel() {
        private val workOrderId: String = checkNotNull(savedStateHandle["workOrderId"])
        private val poleId: String = checkNotNull(savedStateHandle["poleId"])
        private val gate = PoleFixGate()
        private val _state = MutableStateFlow<PoleGpsMeasureUiState>(PoleGpsMeasureUiState.Loading)
        val state: StateFlow<PoleGpsMeasureUiState> = _state
        private var collecting: Job? = null
        private var latestFix: GpsFix? = null

        fun onPermissionGranted() {
            collecting?.cancel()
            collecting =
                viewModelScope.launch {
                    val reference = repository.poleReference(workOrderId, poleId)
                    if (reference == null) {
                        _state.value = PoleGpsMeasureUiState.Empty
                        return@launch
                    }
                    val stored = repository.allMeasurements(workOrderId).firstOrNull { it.poleId == poleId }
                    _state.value =
                        PoleGpsMeasureUiState.Success(
                            poleLabel = poleLabel(reference),
                            previous = reference,
                            liveFix = null,
                            quality = FixQuality.WAITING,
                            canSave = false,
                            saved = false,
                            stored = stored,
                        )
                    fixSource
                        .fixes()
                        .catch {
                            val message = "Không đọc được GPS. Hãy bật định vị và thử lại."
                            _state.value = PoleGpsMeasureUiState.Error(message)
                        }
                        .collect { onFix(it) }
                }
        }

        fun onPermissionDenied() {
            collecting?.cancel()
            _state.value = PoleGpsMeasureUiState.Error("Cần cấp quyền vị trí để đo GPS cột đèn.")
        }

        fun onSave() {
            val current = _state.value as? PoleGpsMeasureUiState.Success ?: return
            val fix = latestFix
            if (!current.canSave || fix == null) return
            viewModelScope.launch {
                repository.saveCapturedPhoto(workOrderId, poleId, "", fix.lat, fix.lng, fix.accuracyM, fix.capturedAt)
                _state.value =
                    current.copy(
                        saved = true,
                        stored = fix.toMeasurement(),
                    )
            }
        }

        fun onMeasureAgain() {
            val current = _state.value as? PoleGpsMeasureUiState.Success ?: return
            gate.reset()
            latestFix = null
            _state.value = current.copy(liveFix = null, quality = FixQuality.WAITING, canSave = false, saved = false)
        }

        private fun onFix(fix: GpsFix) {
            val current = _state.value as? PoleGpsMeasureUiState.Success ?: return
            if (current.saved) return
            latestFix = fix
            val quality = gate.accept(fix)
            _state.value = current.copy(liveFix = fix, quality = quality, canSave = quality == FixQuality.READY)
        }

        private fun poleLabel(reference: PoleReference): String =
            reference.position?.let { "Cột $it · ${reference.poleId}" } ?: reference.poleId

        private fun GpsFix.toMeasurement() =
            PoleMeasurement(
                poleId = poleId,
                lat = lat,
                lng = lng,
                accuracyM = accuracyM,
                capturedAt = capturedAt,
                source = MeasurementSource.DEVICE,
            )
    }
