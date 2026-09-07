package com.luxmap.feature.map.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luxmap.core.location.LocationTracker
import com.luxmap.feature.map.data.MapRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.maplibre.android.geometry.LatLng
import javax.inject.Inject

@HiltViewModel
class MapViewModel
    @Inject
    constructor(
        repository: MapRepository,
        private val locationTracker: LocationTracker,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<MapUiState>(MapUiState.Loading)
        val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

        // One-shot event (không phải state) — camera chỉ cần bay tới vị trí đúng 1 lần mỗi
        // lần bấm nút, không phát lại khi UI recompose.
        private val _locateMeEvent = Channel<LatLng>(Channel.BUFFERED)
        val locateMeEvent: Flow<LatLng> = _locateMeEvent.receiveAsFlow()

        fun onLocateMeClicked() {
            viewModelScope.launch {
                locationTracker.getCurrentLocation()?.let { _locateMeEvent.send(it) }
            }
        }

        init {
            viewModelScope.launch {
                repository
                    .observePoles()
                    .combine(repository.observeRoadSegments()) { poles, roadSegments ->
                        poles to roadSegments
                    }.catch { e ->
                        _uiState.value = MapUiState.Error(e.message ?: "Không tải được dữ liệu bản đồ")
                    }.collect { (poles, roadSegments) ->
                        _uiState.value =
                            if (poles.isEmpty()) {
                                MapUiState.Empty
                            } else {
                                MapUiState.Success(poles = poles, roadSegments = roadSegments)
                            }
                    }
            }
        }
    }
