package com.luxmap.feature.map.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luxmap.feature.map.data.MapRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MapViewModel
    @Inject
    constructor(
        repository: MapRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<MapUiState>(MapUiState.Loading)
        val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

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
