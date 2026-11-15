package com.luxmap.feature.workorder.polegps.ui.measure

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luxmap.core.camera.PhotoGpsExif
import com.luxmap.feature.workorder.polegps.data.GpsFix
import com.luxmap.feature.workorder.polegps.data.GpsFixSource
import com.luxmap.feature.workorder.polegps.data.PoleGpsRepository
import com.luxmap.feature.workorder.polegps.data.PoleReference
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class PoleGpsMeasureViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val repository: PoleGpsRepository,
        private val fixSource: GpsFixSource,
        private val exif: PhotoGpsExif,
    ) : ViewModel() {
        private val workOrderId: String = checkNotNull(savedStateHandle["workOrderId"])
        private val poleId: String = checkNotNull(savedStateHandle["poleId"])
        private val _state = MutableStateFlow<PoleGpsMeasureUiState>(PoleGpsMeasureUiState.Loading)
        val state: StateFlow<PoleGpsMeasureUiState> = _state.asStateFlow()
        private val _currentFix = MutableStateFlow<GpsFix?>(null)
        val currentFix: StateFlow<GpsFix?> = _currentFix.asStateFlow()
        private var watchingLocation = false

        // Starts as soon as permission is known, in parallel with the camera, instead of only
        // being asked for once the photo is confirmed - a fix already has a few seconds to settle
        // by the time the engineer reviews the photo and taps "Dùng ảnh này".
        fun startWatchingLocation() {
            if (watchingLocation) return
            watchingLocation = true
            viewModelScope.launch {
                fixSource.fixes().collect { _currentFix.value = it }
            }
        }

        fun onPermissionGranted() {
            viewModelScope.launch {
                val reference = repository.poleReference(workOrderId, poleId)
                _state.value =
                    if (reference == null) {
                        PoleGpsMeasureUiState.Empty
                    } else {
                        PoleGpsMeasureUiState.Success(poleLabel(reference), reference)
                    }
            }
        }

        fun onPermissionDenied() {
            _state.value = PoleGpsMeasureUiState.Error("Cần cấp quyền vị trí để chụp ảnh cột đèn.")
        }

        fun outputFile(filesDir: File): File {
            val dir = File(filesDir, "pole-gps/$workOrderId")
            dir.mkdirs()
            return File(dir, "$poleId-${UUID.randomUUID()}.jpg")
        }

        fun onPhotoCaptured(
            file: File,
            hasLocationPermission: Boolean,
            onDone: () -> Unit,
        ) {
            viewModelScope.launch {
                val fix =
                    if (hasLocationPermission) {
                        currentFix.value ?: withTimeoutOrNull(FIX_TIMEOUT_MILLIS) { fixSource.fixes().first() }
                    } else {
                        null
                    }
                val (lat, lng) =
                    if (fix != null) {
                        exif.write(file, fix.lat, fix.lng)
                        exif.read(file) ?: (fix.lat to fix.lng)
                    } else {
                        0.0 to 0.0
                    }
                repository.saveCapturedPhoto(
                    workOrderId = workOrderId,
                    poleId = poleId,
                    filePath = file.absolutePath,
                    lat = lat,
                    lng = lng,
                    accuracyM = fix?.accuracyM ?: 0.0,
                    capturedAt = fix?.capturedAt ?: Instant.now(),
                )
                onDone()
            }
        }

        private fun poleLabel(reference: PoleReference): String =
            reference.position?.let { "Cột $it · ${reference.poleId}" } ?: reference.poleId

        companion object {
            const val FIX_TIMEOUT_MILLIS = 10_000L
        }
    }
