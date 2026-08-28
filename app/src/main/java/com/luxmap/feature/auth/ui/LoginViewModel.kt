package com.luxmap.feature.auth.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luxmap.feature.auth.data.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel
    @Inject
    constructor(
        private val authRepository: AuthRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
        val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

        var identifier by mutableStateOf("")
            private set

        var password by mutableStateOf("")
            private set

        var isPasswordVisible by mutableStateOf(false)
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

        fun onLoginClick() {
            viewModelScope.launch {
                _uiState.value = LoginUiState.LoggingIn
                authRepository
                    .login(identifier, password)
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
        }
    }
