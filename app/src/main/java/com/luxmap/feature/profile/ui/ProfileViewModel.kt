package com.luxmap.feature.profile.ui

import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luxmap.BuildConfig
import com.luxmap.core.ble.BleConnectionState
import com.luxmap.core.ble.LuxSensorBleClient
import com.luxmap.core.network.ConnectivityObserver
import com.luxmap.core.sync.SyncQueueManager
import com.luxmap.feature.auth.data.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel
    @Inject
    constructor(
        private val authRepository: AuthRepository,
        syncQueueManager: SyncQueueManager,
        connectivityObserver: ConnectivityObserver,
        bleClient: LuxSensorBleClient,
    ) : ViewModel() {
        val username: StateFlow<String?> =
            authRepository.observeUsername().stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                null,
            )

        val deviceModel: String = "${Build.MANUFACTURER} ${Build.MODEL}"
        val appVersion: String = BuildConfig.VERSION_NAME

        val bleConnectionState: StateFlow<BleConnectionState> = bleClient.connectionState

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

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
