package com.luxmap.feature.profile.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luxmap.feature.auth.data.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel
    @Inject
    constructor(
        private val authRepository: AuthRepository,
    ) : ViewModel() {
        val username: StateFlow<String?> =
            authRepository.observeUsername().stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                null,
            )

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
