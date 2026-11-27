package com.luxmap.feature.survey.ui.polephoto

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luxmap.core.location.LocationTracker
import com.luxmap.feature.survey.data.LuxReadingRepository
import com.luxmap.feature.survey.data.PoleListItem
import com.luxmap.feature.survey.data.PoleListRepository
import com.luxmap.feature.survey.data.PolePhotoRepository
import com.luxmap.feature.survey.ui.polephoto.lux.LuxCardUiState
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

// Where the pole being captured sits on its route, e.g. "Cột 3/20".
data class RouteProgress(
    val index: Int,
    val total: Int,
)

@HiltViewModel
class PolePhotoCaptureViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val repository: PolePhotoRepository,
        private val locationTracker: LocationTracker,
        private val luxReadingRepository: LuxReadingRepository,
        private val poleListRepository: PoleListRepository,
    ) : ViewModel() {
        private val workOrderId: String = checkNotNull(savedStateHandle[WORK_ORDER_ID_ARG])
        private val poleId: String = checkNotNull(savedStateHandle[POLE_ID_ARG])
        private val source: String? = savedStateHandle["source"]

        // Route mode: after a pole is saved or skipped, hand back the next pole still missing
        // so the camera opens again for it, instead of returning to the pole list each time.
        val isRouteMode: Boolean = savedStateHandle.get<Boolean>(ROUTE_ARG) ?: false
        private val photoFileName: String = UUID.randomUUID().toString()
        private val _locationMissing = MutableStateFlow(false)
        val locationMissing: StateFlow<Boolean> = _locationMissing.asStateFlow()
        private val _progress = MutableStateFlow<RouteProgress?>(null)
        val progress: StateFlow<RouteProgress?> = _progress.asStateFlow()
        private var routePoles: List<PoleListItem>? = null

        init {
            viewModelScope.launch {
                val poles = loadRoutePoles()
                val index = poles.indexOfFirst { it.poleId == poleId }
                if (index >= 0) _progress.value = RouteProgress(index + 1, poles.size)
            }
        }

        fun outputFile(filesDir: File): File {
            val dir = File(filesDir, "pole-photos/$workOrderId")
            dir.mkdirs()
            val prefix = if (source.equals("gallery", ignoreCase = true)) GALLERY_MEDIA_PREFIX else ""
            return File(dir, "$prefix$photoFileName.jpg")
        }

        // onDone receives the next pole to capture in route mode, or null to go back to the list.
        fun onPhotoCaptured(
            file: File,
            observation: String,
            lux: LuxCardUiState.Recorded? = null,
            onDone: (nextPoleId: String?) -> Unit,
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
                        repository.capture(
                            workOrderId = workOrderId,
                            poleId = poleId,
                            filePath = file.absolutePath,
                            lat = location.latitude,
                            lng = location.longitude,
                            observation = observation,
                        )
                    }.getOrNull()
                if (saved != null && lux != null) {
                    runCatching {
                        luxReadingRepository.record(
                            workOrderId,
                            poleId,
                            saved.photoId,
                            lux.lux,
                            lux.measuredAt,
                            lux.samples,
                            lux.moduleId,
                        )
                    }
                }
                onDone(nextPoleId())
            }
        }

        fun onSkip(
            reason: String,
            onDone: (nextPoleId: String?) -> Unit,
        ) {
            viewModelScope.launch {
                repository.skipPole(workOrderId, poleId, reason)
                onDone(nextPoleId())
            }
        }

        private suspend fun loadRoutePoles(): List<PoleListItem> =
            routePoles ?: poleListRepository.poles(workOrderId).getOrNull()?.poles.orEmpty().also { routePoles = it }

        // The next pole along the route with neither a photo nor a skip, wrapping around to the
        // start so a pole passed over earlier is not forgotten. Null outside route mode.
        private suspend fun nextPoleId(): String? {
            if (!isRouteMode) return null
            val poles = loadRoutePoles()
            // Same rule as PoleListRowState.isCovered: a labelled photo or a confirmed review on
            // the pole's latest media, or a skip. An unconfirmed older photo/video still needs it.
            val latestByPole = repository.observePhotos(workOrderId).first().distinctBy { it.poleId }
            val done =
                latestByPole
                    .filter { it.engineerObservation != null || isConclusive(it.draftFinalCvState ?: it.finalCvState) }
                    .map { it.poleId }
                    .toSet() + repository.observeSkips(workOrderId).first().map { it.poleId }
            val start = poles.indexOfFirst { it.poleId == poleId } + 1
            return (poles.drop(start) + poles.take(start))
                .firstOrNull { it.poleId != poleId && it.poleId !in done }
                ?.poleId
        }

        companion object {
            const val WORK_ORDER_ID_ARG = "workOrderId"
            const val POLE_ID_ARG = "poleId"
            const val ROUTE_ARG = "route"
            private const val LOCATION_TIMEOUT_MS = 10_000L
        }
    }
