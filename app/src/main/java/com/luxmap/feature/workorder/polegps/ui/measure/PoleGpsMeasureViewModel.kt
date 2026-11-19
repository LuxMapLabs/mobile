package com.luxmap.feature.workorder.polegps.ui.measure

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luxmap.core.camera.PhotoGpsExif
import com.luxmap.feature.workorder.polegps.data.GpsClock
import com.luxmap.feature.workorder.polegps.data.GpsFix
import com.luxmap.feature.workorder.polegps.data.GpsFixQuality
import com.luxmap.feature.workorder.polegps.data.GpsFixSource
import com.luxmap.feature.workorder.polegps.data.PoleGpsRepository
import com.luxmap.feature.workorder.polegps.data.PoleReference
import com.luxmap.feature.workorder.polegps.data.quality
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
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
        private val clock: GpsClock,
    ) : ViewModel() {
        private val workOrderId: String = checkNotNull(savedStateHandle["workOrderId"])
        private val poleId: String = checkNotNull(savedStateHandle["poleId"])
        private val _state = MutableStateFlow<PoleGpsMeasureUiState>(PoleGpsMeasureUiState.Loading)
        val state: StateFlow<PoleGpsMeasureUiState> = _state.asStateFlow()
        private val _currentFix = MutableStateFlow<GpsFix?>(null)
        val currentFix: StateFlow<GpsFix?> = _currentFix.asStateFlow()
        private var locationJob: Job? = null
        private var captureJob: Job? = null
        private val _pendingConfirmation = MutableStateFlow<GpsFix?>(null)
        val pendingConfirmation: StateFlow<GpsFix?> = _pendingConfirmation.asStateFlow()
        private val _isSaving = MutableStateFlow(false)
        val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()
        private var pendingPhoto: File? = null
        private var pendingDone: (() -> Unit)? = null
        private var savedPhoto: String? = null
        private val _locationMissing = MutableStateFlow(false)
        val locationMissing: StateFlow<Boolean> = _locationMissing.asStateFlow()

        fun startWatchingLocation() {
            if (locationJob?.isActive == true) return
            locationJob =
                viewModelScope.launch {
                    fixSource.fixes().catch { _locationMissing.value = true }.collect { fix ->
                        if (fix.isUsable()) _currentFix.value = fix
                    }
                }
        }

        fun stopWatchingLocation() {
            locationJob?.cancel()
            locationJob = null
            captureJob?.cancel()
            captureJob = null
            _currentFix.value = null
            _pendingConfirmation.value = null
            pendingPhoto = null
            pendingDone = null
            _isSaving.value = false
        }

        fun onCameraFailure() {
            _state.value = PoleGpsMeasureUiState.Error("Không mở được camera. Vui lòng thử lại.")
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
            stopWatchingLocation()
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
            if (_isSaving.value || savedPhoto == file.absolutePath) return
            _locationMissing.value = false
            _pendingConfirmation.value = null
            pendingPhoto = file
            pendingDone = onDone
            if (!hasLocationPermission) {
                stopWatchingLocation()
                _locationMissing.value = true
                return
            }
            startWatchingLocation()
            _isSaving.value = true
            captureJob =
                viewModelScope.launch {
                    try {
                        val fix =
                            withTimeoutOrNull(FIX_TIMEOUT_MILLIS) {
                                currentFix.first { it?.isUsable() == true }
                            }
                        if (fix == null) {
                            _locationMissing.value = true
                        } else if (fix.quality(clock.elapsedRealtimeNanos()) == GpsFixQuality.LOW_ACCURACY) {
                            _pendingConfirmation.value = fix
                        } else {
                            persist(file, fix, onDone)
                        }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        _locationMissing.value = true
                    } finally {
                        _isSaving.value = false
                    }
                }
        }

        fun confirmLowAccuracy() {
            if (_isSaving.value) return
            val fix = _pendingConfirmation.value ?: return
            val file = pendingPhoto ?: return
            val onDone = pendingDone ?: return
            _pendingConfirmation.value = null
            if (!fix.isUsable()) {
                _locationMissing.value = true
                return
            }
            _isSaving.value = true
            captureJob =
                viewModelScope.launch {
                    try {
                        persist(file, fix, onDone)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        _locationMissing.value = true
                    } finally {
                        _isSaving.value = false
                    }
                }
        }

        fun retryMeasurement() {
            val file = pendingPhoto ?: return
            val onDone = pendingDone ?: return
            _pendingConfirmation.value = null
            _currentFix.value = null
            onPhotoCaptured(file, true, onDone)
        }

        private fun GpsFix.isUsable(): Boolean =
            when (quality(clock.elapsedRealtimeNanos())) {
                GpsFixQuality.ACCEPTABLE, GpsFixQuality.LOW_ACCURACY -> true
                GpsFixQuality.INVALID, GpsFixQuality.STALE -> false
            }

        private suspend fun persist(
            file: File,
            fix: GpsFix,
            onDone: () -> Unit,
        ) {
            if (!fix.isUsable() || savedPhoto == file.absolutePath) {
                _locationMissing.value = true
                return
            }
            exif.write(file, fix.lat, fix.lng)
            val (lat, lng) = exif.read(file) ?: (fix.lat to fix.lng)
            require(fix.copy(lat = lat, lng = lng).isUsable())
            repository.saveCapturedPhoto(
                workOrderId = workOrderId,
                poleId = poleId,
                filePath = file.absolutePath,
                lat = lat,
                lng = lng,
                accuracyM = fix.accuracyM,
                capturedAt = fix.capturedAt,
            )
            savedPhoto = file.absolutePath
            pendingPhoto = null
            pendingDone = null
            onDone()
        }

        private fun poleLabel(reference: PoleReference): String =
            reference.position?.let { "Cột $it (${reference.poleId})" } ?: reference.poleId

        companion object {
            const val FIX_TIMEOUT_MILLIS = 10_000L
        }
    }
