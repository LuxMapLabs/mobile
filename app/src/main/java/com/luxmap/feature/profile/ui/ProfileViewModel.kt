package com.luxmap.feature.profile.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luxmap.core.network.ConnectivityObserver
import com.luxmap.core.sync.SyncQueueManager
import com.luxmap.feature.auth.data.AuthRepository
import com.luxmap.feature.profile.data.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel
    @Inject
    constructor(
        private val authRepository: AuthRepository,
        private val profileRepository: ProfileRepository,
        syncQueueManager: SyncQueueManager,
        connectivityObserver: ConnectivityObserver,
    ) : ViewModel() {
        private val _profileState = MutableStateFlow<ProfileUiState>(ProfileUiState.Loading)
        val profileState: StateFlow<ProfileUiState> = _profileState.asStateFlow()

        private var loadJob: Job? = null

        init {
            loadProfile()
        }

        fun retry() {
            _profileState.value = ProfileUiState.Loading
            loadProfile()
        }

        private fun loadProfile() {
            loadJob?.cancel()
            loadJob =
                viewModelScope.launch {
                    val result = profileRepository.fetchCurrentUser()
                    ensureActive()
                    _profileState.value =
                        result.fold(
                            onSuccess = { ProfileUiState.Success(it) },
                            onFailure = {
                                ProfileUiState.Error(
                                    message = it.message ?: "Không tải được thông tin cá nhân",
                                    isNetworkError = it is IOException,
                                )
                            },
                        )
                }
        }

        val pendingSyncCount: StateFlow<Int> =
            syncQueueManager.pendingCount.stateIn(viewModelScope, SharingStarted.Eagerly, 0)
        val errorSyncCount: StateFlow<Int> =
            syncQueueManager.errorCount.stateIn(viewModelScope, SharingStarted.Eagerly, 0)
        val lastSyncedAt: StateFlow<Instant?> =
            syncQueueManager.lastSyncedAt.stateIn(viewModelScope, SharingStarted.Eagerly, null)
        val isOnline: StateFlow<Boolean> =
            connectivityObserver.isOnline.stateIn(viewModelScope, SharingStarted.Eagerly, true)

        var isLoggingOut by mutableStateOf(false)
            private set

        // This only clears the session. Going back to the Login screen is done by NavGraph when
        // it sees that the session ended, so the same path also covers a forced logout.
        fun onConfirmLogout() {
            if (isLoggingOut) return
            viewModelScope.launch {
                isLoggingOut = true
                try {
                    authRepository.logout()
                } finally {
                    isLoggingOut = false
                }
            }
        }
    }
