package com.luxmap.feature.auth.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luxmap.core.network.ConnectivityObserver
import com.luxmap.feature.auth.data.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel
    @Inject
    constructor(
        private val authRepository: AuthRepository,
        connectivityObserver: ConnectivityObserver,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
        val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

        // Drives the offline notice on Login (separate from LoginUiState — same precedent as
        // MapViewModel.isOnline: connectivity is not one of the form/prefetch states). Starts
        // true ("assume online") so the notice never flashes before the first callback fires.
        val isOnline: StateFlow<Boolean> =
            connectivityObserver.isOnline.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), true)

        var identifier by mutableStateOf("")
            private set

        var password by mutableStateOf("")
            private set

        var isPasswordVisible by mutableStateOf(false)
            private set

        // On by default: field crews often work with no network, so keeping the session is the
        // safer choice. Not part of LoginUiState — it is a form field, like identifier/password.
        var rememberMe by mutableStateOf(true)
            private set

        init {
            viewModelScope.launch {
                // Phiên offline hợp lệ (F01, mục P9): đã đăng nhập trước đó, mở lại app vào
                // thẳng không cần nhập lại, kể cả khi offline hoàn toàn.
                if (authRepository.observeIsLoggedIn().first()) {
                    runPrefetchAndSucceed()
                }
            }
        }

        fun onIdentifierChange(value: String) {
            identifier = value
        }

        fun onPasswordChange(value: String) {
            password = value
        }

        fun onTogglePasswordVisibility() {
            isPasswordVisible = !isPasswordVisible
        }

        fun onRememberMeChange(value: Boolean) {
            rememberMe = value
        }

        fun onLoginClick() {
            viewModelScope.launch {
                _uiState.value = LoginUiState.LoggingIn
                authRepository
                    .login(identifier, password, rememberMe)
                    .onSuccess { runPrefetchAndSucceed() }
                    .onFailure { e ->
                        _uiState.value = LoginUiState.Error(e.message ?: "Đăng nhập thất bại")
                    }
            }
        }

        private suspend fun runPrefetchAndSucceed() {
            _uiState.value = LoginUiState.Prefetching
            delay(PREFETCH_DELAY_MS)
            _uiState.value = LoginUiState.Success
        }

        private companion object {
            const val PREFETCH_DELAY_MS = 800L
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
