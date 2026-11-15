package com.luxmap.feature.polelocation.ui.capture

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luxmap.feature.polelocation.data.PoleLocationExif
import com.luxmap.feature.polelocation.data.PoleLocationRepository
import com.luxmap.feature.workorder.polegps.data.GpsFix
import com.luxmap.feature.workorder.polegps.data.GpsFixSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class PoleLocationCaptureViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val repository: PoleLocationRepository,
        private val fixSource: GpsFixSource,
        private val exif: PoleLocationExif,
    ) : ViewModel() {
        val poleId: String = checkNotNull(savedStateHandle["poleId"])
        private val _currentFix = MutableStateFlow<GpsFix?>(null)
        val currentFix: StateFlow<GpsFix?> = _currentFix.asStateFlow()
        private val _errorMessage = MutableStateFlow<String?>(null)
        val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
        private var watchingLocation = false

        // Started as soon as permission is known, in parallel with the camera - same reasoning
        // as PoleGpsMeasureViewModel: a fix already has a few seconds to settle by the time the
        // engineer reviews the photo and taps "Dùng ảnh này".
        fun startWatchingLocation() {
            if (watchingLocation) return
            watchingLocation = true
            viewModelScope.launch {
                fixSource.fixes().collect { _currentFix.value = it }
            }
        }

        fun outputFile(filesDir: File): File {
            val dir = File(filesDir, "pole-location/$poleId")
            dir.mkdirs()
            return File(dir, "${UUID.randomUUID()}.jpg")
        }

        // This feature writes PUT .../location straight to the map with no Manager review (unlike
        // the WorkOrder pole-GPS flow), so a missing fix must stop the submit - a 0.0/0.0
        // placeholder is not an acceptable fallback here.
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
                if (fix == null) {
                    _errorMessage.value = "Không lấy được vị trí GPS, vui lòng thử lại."
                    return@launch
                }
                exif.write(file, fix.lat, fix.lng)
                val (lat, lng) = exif.read(file) ?: (fix.lat to fix.lng)
                repository.submit(poleId, file.absolutePath, lat, lng, fix.capturedAt)
                onDone()
            }
        }

        fun dismissError() {
            _errorMessage.value = null
        }

        companion object {
            const val FIX_TIMEOUT_MILLIS = 10_000L
        }
    }
