package com.luxmap.feature.workorder.ui.completion

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luxmap.core.location.LocationTracker
import com.luxmap.feature.workorder.data.WorkOrderCompletionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class EvidenceCaptureViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val repository: WorkOrderCompletionRepository,
        private val locationTracker: LocationTracker,
    ) : ViewModel() {
        private val workOrderId: String = checkNotNull(savedStateHandle[WORK_ORDER_ID_ARG])
        val clientOpId: String = UUID.randomUUID().toString()

        fun outputFile(filesDir: File): File {
            val dir = File(filesDir, "evidence/$workOrderId")
            dir.mkdirs()
            return File(dir, "$clientOpId.jpg")
        }

        fun onPhotoCaptured(
            file: File,
            onDone: () -> Unit,
        ) {
            viewModelScope.launch {
                val location = locationTracker.getCurrentLocation()
                repository.captureAfterEvidence(
                    workOrderId = workOrderId,
                    clientOpId = clientOpId,
                    filePath = file.absolutePath,
                    lat = location?.latitude ?: 0.0,
                    lng = location?.longitude ?: 0.0,
                    capturedAt = Instant.now(),
                )
                onDone()
            }
        }

        companion object {
            const val WORK_ORDER_ID_ARG = "workOrderId"
        }
    }
