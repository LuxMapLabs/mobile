package com.luxmap.feature.map.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luxmap.core.theme.AssetCondition
import com.luxmap.feature.map.data.GisMapDataset
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

        // Status filter (FM-36: "Lọc theo trạng thái đèn") — empty set means no filter, show
        // every pole. Read by the layer-filter bottom sheet and by the KPI "cần xử lý" chip.
        private val _statusFilter = MutableStateFlow<Set<AssetCondition>>(emptySet())
        val statusFilter: StateFlow<Set<AssetCondition>> = _statusFilter.asStateFlow()

        init {
            viewModelScope.launch {
                repository
                    .observeGisMapDataset()
                    .combine(_statusFilter) { dataset, filter -> dataset to filter }
                    .catch { e ->
                        _uiState.value = MapUiState.Error(e.message ?: "Không tải được dữ liệu bản đồ")
                    }.collect { (dataset, filter) ->
                        _uiState.value =
                            if (dataset.poles.isEmpty()) {
                                MapUiState.Empty
                            } else {
                                MapUiState.Success(
                                    dataset = dataset,
                                    filteredDataset = dataset.filterByStatus(filter),
                                )
                            }
                    }
            }
        }

        fun setStatusFilter(filter: Set<AssetCondition>) {
            _statusFilter.value = filter
        }
    }

private fun GisMapDataset.filterByStatus(filter: Set<AssetCondition>): GisMapDataset =
    if (filter.isEmpty()) this else copy(poles = poles.filter { it.fixtureStatus in filter })
