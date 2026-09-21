package com.luxmap.feature.auth.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luxmap.feature.auth.data.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

// Lets NavGraph know when the session ends (user logout, or the token refresh failing), so the
// app can go back to Login from one place.
@HiltViewModel
class SessionViewModel
    @Inject
    constructor(
        authRepository: AuthRepository,
    ) : ViewModel() {
        // null = not known yet. NavGraph must not treat "not known yet" as "logged out".
        val isLoggedIn: StateFlow<Boolean?> =
            authRepository.observeIsLoggedIn().stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                null,
            )

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
