package com.luxmap.feature.auth.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luxmap.core.network.ConnectivityObserver
import com.luxmap.feature.auth.data.AuthRepository
import com.luxmap.feature.auth.data.LoginFailedException
import com.luxmap.feature.auth.data.LoginFailureReason
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

        // Used by onLoginClick to skip the request when the device is offline (separate from
        // LoginUiState — connectivity is not one of the form/prefetch states). Starts true ("assume
        // online"). Eagerly, not WhileSubscribed: no screen reads this flow, only onLoginClick reads
        // .value, so with WhileSubscribed it would never update and stay true.
        private val isOnline: StateFlow<Boolean> =
            connectivityObserver.isOnline.stateIn(viewModelScope, SharingStarted.Eagerly, true)

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
            clearError()
        }

        fun onPasswordChange(value: String) {
            password = value
            clearError()
        }

        // Once the user starts to fix the form the old error is out of date, so remove it.
        private fun clearError() {
            if (_uiState.value is LoginUiState.Error) _uiState.value = LoginUiState.Idle
        }

        fun onTogglePasswordVisibility() {
            isPasswordVisible = !isPasswordVisible
        }

        fun onRememberMeChange(value: Boolean) {
            rememberMe = value
        }

        fun onLoginClick() {
            // Empty fields are checked here, before any request, so no network call is made.
            val formError = validateForm()
            if (formError != null) {
                _uiState.value = formError
                return
            }
            // isOnline starts as true, so false here means the device really reported no network.
            // Answer at once instead of waiting for the request to fail. If the state is wrong and
            // the network is back, the user can tap the button again.
            if (!isOnline.value) {
                _uiState.value = LoginUiState.Error(LoginFailureReason.Network, OFFLINE_MESSAGE)
                return
            }
            viewModelScope.launch {
                _uiState.value = LoginUiState.LoggingIn
                authRepository
                    .login(identifier, password, rememberMe)
                    .onSuccess { runPrefetchAndSucceed() }
                    .onFailure { e ->
                        _uiState.value =
                            LoginUiState.Error(
                                reason = (e as? LoginFailedException)?.reason ?: LoginFailureReason.Unknown,
                                message = e.message ?: LOGIN_FAILED_MESSAGE,
                            )
                    }
            }
        }

        private fun validateForm(): LoginUiState.Error? =
            when {
                identifier.isBlank() ->
                    LoginUiState.Error(
                        LoginFailureReason.MissingIdentifier,
                        "Vui lòng nhập mã nhân viên hoặc số điện thoại.",
                    )
                password.isBlank() ->
                    LoginUiState.Error(LoginFailureReason.MissingPassword, "Vui lòng nhập mật khẩu.")
                else -> null
            }

        private suspend fun runPrefetchAndSucceed() {
            _uiState.value = LoginUiState.Prefetching
            delay(PREFETCH_DELAY_MS)
            _uiState.value = LoginUiState.Success
        }

        private companion object {
            const val PREFETCH_DELAY_MS = 800L
            const val LOGIN_FAILED_MESSAGE = "Đăng nhập thất bại. Vui lòng thử lại."
            const val OFFLINE_MESSAGE = "Kết nối thất bại. Vui lòng kiểm tra lại."
        }
    }
