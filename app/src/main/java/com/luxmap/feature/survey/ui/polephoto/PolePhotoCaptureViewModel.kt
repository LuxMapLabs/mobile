package com.luxmap.feature.survey.ui.polephoto

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luxmap.core.location.LocationTracker
import com.luxmap.feature.survey.data.PolePhotoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class PolePhotoCaptureViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val repository: PolePhotoRepository,
        private val locationTracker: LocationTracker,
    ) : ViewModel() {
        private val workOrderId: String = checkNotNull(savedStateHandle[WORK_ORDER_ID_ARG])
        private val poleId: String = checkNotNull(savedStateHandle[POLE_ID_ARG])
        private val photoFileName: String = UUID.randomUUID().toString()

        fun outputFile(filesDir: File): File {
            val dir = File(filesDir, "pole-photos/$workOrderId")
            dir.mkdirs()
            return File(dir, "$photoFileName.jpg")
        }

        fun onPhotoCaptured(
            file: File,
            onDone: () -> Unit,
        ) {
            viewModelScope.launch {
                val location = locationTracker.getCurrentLocation()
                runCatching {
                    repository.capture(
                        workOrderId = workOrderId,
                        poleId = poleId,
                        filePath = file.absolutePath,
                        lat = location?.latitude ?: 0.0,
                        lng = location?.longitude ?: 0.0,
                    )
                }
                onDone()
            }
        }

        companion object {
            const val WORK_ORDER_ID_ARG = "workOrderId"
            const val POLE_ID_ARG = "poleId"
        }
    }
