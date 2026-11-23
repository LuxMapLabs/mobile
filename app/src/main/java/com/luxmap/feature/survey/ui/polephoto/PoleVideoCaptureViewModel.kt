package com.luxmap.feature.survey.ui.polephoto

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luxmap.core.location.LocationTracker
import com.luxmap.feature.survey.data.LuxReadingRepository
import com.luxmap.feature.survey.data.PolePhotoRepository
import com.luxmap.feature.survey.ui.polephoto.lux.LuxCardUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class PoleVideoCaptureViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val repository: PolePhotoRepository,
        private val locationTracker: LocationTracker,
        private val luxReadingRepository: LuxReadingRepository,
    ) : ViewModel() {
        private val workOrderId: String = checkNotNull(savedStateHandle[WORK_ORDER_ID_ARG])
        private val poleId: String = checkNotNull(savedStateHandle[POLE_ID_ARG])
        private val source: String? = savedStateHandle["source"]
        private val videoFileName: String = UUID.randomUUID().toString()
        private val _locationMissing = MutableStateFlow(false)
        val locationMissing: StateFlow<Boolean> = _locationMissing.asStateFlow()

        fun outputFile(filesDir: File): File {
            val dir = File(filesDir, "pole-videos/$workOrderId")
            dir.mkdirs()
            val prefix = if (source.equals("gallery", ignoreCase = true)) GALLERY_MEDIA_PREFIX else ""
            return File(dir, "$prefix$videoFileName.mp4")
        }

        fun onVideoCaptured(
            file: File,
            lux: LuxCardUiState.Recorded? = null,
            onDone: () -> Unit,
        ) {
            _locationMissing.value = false
            viewModelScope.launch {
                val location = withTimeoutOrNull(LOCATION_TIMEOUT_MS) { locationTracker.getCurrentLocation() }
                if (location == null) {
                    _locationMissing.value = true
                    return@launch
                }
                val saved =
                    runCatching {
                        repository.captureVideo(
                            workOrderId = workOrderId,
                            poleId = poleId,
                            filePath = file.absolutePath,
                            lat = location.latitude,
                            lng = location.longitude,
                        )
                    }.getOrNull()
                if (saved != null && lux != null) {
                    runCatching {
                        luxReadingRepository.record(workOrderId, poleId, saved.photoId, lux.lux, lux.measuredAt)
                    }
                }
                onDone()
            }
        }

        companion object {
            const val WORK_ORDER_ID_ARG = "workOrderId"
            const val POLE_ID_ARG = "poleId"
            private const val LOCATION_TIMEOUT_MS = 10_000L
        }
    }
