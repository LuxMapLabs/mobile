package com.luxmap.feature.workorder.ui.completion

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luxmap.core.location.LocationTracker
import com.luxmap.feature.workorder.data.EvidenceKind
import com.luxmap.feature.workorder.data.WorkOrderCompletionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

internal data class EvidenceCaptureState(
    val busy: Boolean = false,
    val added: Int = 0,
    val failed: Int = 0,
    val awaitingLocation: Int = 0,
    val locationUnavailable: Boolean = false,
)

@HiltViewModel
class EvidenceCaptureViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val repository: WorkOrderCompletionRepository,
        private val locationTracker: LocationTracker,
    ) : ViewModel() {
        private val workOrderId: String = checkNotNull(savedStateHandle[WORK_ORDER_ID_ARG])
        private val kind =
            EvidenceKind.entries.firstOrNull { it.wireValue == savedStateHandle.get<String>("kind") }
                ?: EvidenceKind.AFTER
        val clientOpId: String =
            savedStateHandle.get<String>("cameraOperationId") ?: UUID.randomUUID().toString().also {
                savedStateHandle["cameraOperationId"] = it
            }
        private val missingLocation = mutableListOf<PendingCameraPhoto>()
        private val _state = MutableStateFlow(EvidenceCaptureState())
        internal val state = _state.asStateFlow()

        fun outputFile(filesDir: File): File = evidenceFile(filesDir, clientOpId)

        private fun evidenceFile(
            filesDir: File,
            operationId: String,
        ): File {
            val dir = File(filesDir, "evidence/$workOrderId")
            dir.mkdirs()
            return File(dir, "$operationId.jpg")
        }

        fun onPhotoCaptured(
            file: File,
            onDone: () -> Unit,
        ) {
            _state.value = EvidenceCaptureState(busy = true)
            viewModelScope.launch {
                val capturedAt = Instant.now()
                var located = false
                val saved =
                    saveFile(file) {
                        val location = withTimeoutOrNull(10_000L) { locationTracker.getCurrentLocation() }
                        if (location == null) {
                            missingLocation += PendingCameraPhoto(file, capturedAt)
                        } else {
                            persist(file, capturedAt, location.latitude to location.longitude)
                            located = true
                        }
                    }
                _state.value =
                    EvidenceCaptureState(
                        failed = if (saved) 0 else 1,
                        awaitingLocation = missingLocation.size,
                    )
                if (saved && located) onDone()
            }
        }

        internal fun useCurrentLocation() {
            if (missingLocation.isEmpty() || _state.value.busy) return
            val previous = _state.value
            _state.value = previous.copy(busy = true, locationUnavailable = false)
            viewModelScope.launch {
                val location = withTimeoutOrNull(10_000L) { locationTracker.getCurrentLocation() }
                if (location == null) {
                    _state.value = previous.copy(locationUnavailable = true)
                    return@launch
                }
                val coordinates = location.latitude to location.longitude
                var result =
                    previous.copy(
                        awaitingLocation = 0,
                        locationUnavailable = false,
                    )
                val iterator = missingLocation.iterator()
                while (iterator.hasNext()) {
                    val photo = iterator.next()
                    val saved =
                        saveFile(photo.file) {
                            persist(photo.file, photo.capturedAt, coordinates)
                        }
                    result =
                        if (saved) result.copy(added = result.added + 1) else result.copy(failed = result.failed + 1)
                    iterator.remove()
                }
                _state.value = result
            }
        }

        internal fun skipPhotosWithoutLocation() {
            val previous = _state.value
            if (previous.busy) return
            missingLocation.forEach { it.file.delete() }
            missingLocation.clear()
            _state.value =
                previous.copy(
                    awaitingLocation = 0,
                    failed = previous.failed + previous.awaitingLocation,
                    locationUnavailable = false,
                )
        }

        private suspend fun persist(
            file: File,
            capturedAt: Instant,
            coordinates: Pair<Double, Double>,
        ) {
            require(file.length() in 1..MAX_EVIDENCE_BYTES)
            repository.captureEvidence(
                workOrderId = workOrderId,
                kind = kind,
                clientOpId = file.nameWithoutExtension,
                filePath = file.absolutePath,
                lat = coordinates.first,
                lng = coordinates.second,
                capturedAt = capturedAt,
            )
        }

        private suspend fun saveFile(
            file: File,
            action: suspend () -> Unit,
        ): Boolean =
            try {
                action()
                true
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                file.delete()
                false
            }

        override fun onCleared() {
            missingLocation.forEach { it.file.delete() }
        }

        companion object {
            const val WORK_ORDER_ID_ARG = "workOrderId"
        }
    }

private const val MAX_EVIDENCE_BYTES = 16L * 1024 * 1024

private data class PendingCameraPhoto(val file: File, val capturedAt: Instant)
