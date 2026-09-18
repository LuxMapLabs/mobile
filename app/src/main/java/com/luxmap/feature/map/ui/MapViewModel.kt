package com.luxmap.feature.map.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luxmap.core.network.ConnectivityObserver
import com.luxmap.core.theme.AssetCondition
import com.luxmap.feature.map.data.GisMapDataset
import com.luxmap.feature.map.data.MapRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MapViewModel
    @Inject
    constructor(
        repository: MapRepository,
        connectivityObserver: ConnectivityObserver,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<MapUiState>(MapUiState.Loading)
        val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

        // Offline basemap banner (F12/FM-38) — starts true (assume online) so the banner doesn't
        // flash on screen before the first connectivity callback fires. Pole/route data comes
        // from `repository` above regardless of this, so it keeps rendering while offline.
        val isOnline: StateFlow<Boolean> =
            connectivityObserver.isOnline.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), true)

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

        // Search (FM-36) — matches against the full dataset (see MapSearchResults) so a hidden,
        // filtered-out pole/route can still be found and jumped to.
        private val _searchQuery = MutableStateFlow("")
        val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

        private val _searchTarget = MutableStateFlow(MapSearchTarget.POLE)
        val searchTarget: StateFlow<MapSearchTarget> = _searchTarget.asStateFlow()

        val searchResults: StateFlow<MapSearchResults> =
            combine(uiState, _searchQuery, _searchTarget) { state, query, target ->
                val dataset = (state as? MapUiState.Success)?.dataset
                if (dataset == null || query.isBlank()) {
                    MapSearchResults()
                } else {
                    dataset.matching(query, target)
                }
            }.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                MapSearchResults(),
            )

        fun setSearchQuery(query: String) {
            _searchQuery.value = query
        }

        fun setSearchTarget(target: MapSearchTarget) {
            _searchTarget.value = target
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }

private fun GisMapDataset.matching(
    query: String,
    target: MapSearchTarget,
): MapSearchResults =
    when (target) {
        MapSearchTarget.POLE ->
            MapSearchResults(poles = poles.filter { it.poleId.contains(query, ignoreCase = true) })
        MapSearchTarget.ROUTE ->
            MapSearchResults(
                segments =
                    segments.filter {
                        it.name.contains(query, ignoreCase = true) || it.segmentId.contains(query, ignoreCase = true)
                    },
            )
        // Not wired yet — see FM-35 note, "atlas" is not a confirmed field/endpoint.
        MapSearchTarget.ATLAS -> MapSearchResults()
    }

private fun GisMapDataset.filterByStatus(filter: Set<AssetCondition>): GisMapDataset =
    if (filter.isEmpty()) this else copy(poles = poles.filter { it.fixtureStatus in filter })
