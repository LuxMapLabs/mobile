package com.luxmap.feature.home.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luxmap.core.network.ConnectivityObserver
import com.luxmap.feature.auth.data.AuthRepository
import com.luxmap.feature.home.data.HomeRepository
import com.luxmap.feature.workorder.data.WorkOrderCompletionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        private val repository: HomeRepository,
        private val completionRepository: WorkOrderCompletionRepository,
        authRepository: AuthRepository,
        connectivityObserver: ConnectivityObserver,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
        val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

        // Reuse the profile returned by Home's existing /auth/me request, even when tasks are empty.
        private val _fullName = MutableStateFlow<String?>(null)
        val fullName: StateFlow<String?> = _fullName.asStateFlow()
        val username: StateFlow<String?> =
            authRepository.observeUsername().stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                null,
            )

        private var loadJob: Job? = null
        private var hasResumed = false

        init {
            load()
            // Coming back into coverage reloads a failed or offline Home on its own, instead of
            // waiting for the engineer to find the "Thử lại" button.
            viewModelScope.launch {
                connectivityObserver.isOnline.distinctUntilChanged().drop(1).filter { it }.collect {
                    val state = _uiState.value
                    if (state is HomeUiState.Error || (state is HomeUiState.Success && state.isStale)) load()
                }
            }
        }

        fun retry() {
            load()
        }

        fun onResume() {
            if (hasResumed) load()
            hasResumed = true
        }

        private fun load() {
            loadJob?.cancel()
            _uiState.value = HomeUiState.Loading
            loadJob =
                viewModelScope.launch {
                    combine(
                        repository.observeHomeData(),
                        completionRepository.observeCompletionSyncStates(),
                    ) { data, syncStates -> data to syncStates }
                        .catch { e ->
                            _uiState.value =
                                HomeUiState.Error(
                                    message = e.message ?: "Không tải được dữ liệu",
                                    isNetworkError = e is IOException,
                                )
                        }.collect { (data, syncStates) ->
                            _fullName.value = data.fullName
                            _uiState.value =
                                if (data.visibleTasks().isEmpty()) {
                                    HomeUiState.Empty
                                } else {
                                    HomeUiState.Success(
                                        data = data,
                                        isStale = data.cachedAt != null,
                                        lastSyncedAt = data.cachedAt ?: Instant.now(),
                                        completionSyncStates = syncStates,
                                    )
                                }
                        }
                }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
