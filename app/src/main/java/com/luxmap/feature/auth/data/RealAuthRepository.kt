package com.luxmap.feature.auth.data

import com.luxmap.core.network.AuthApi
import com.luxmap.core.security.TokenStore
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

// Calls the real BE-07 endpoints (api/v1/auth/login|logout) — see AuthApi.kt. Error codes are
// looked up from luxmap_backend/src/LuxMap.Shared/Contracts/Errors/ErrorCodes.cs (read-only,
// the backend is never modified).
@Singleton
class RealAuthRepository
    @Inject
    constructor(
        private val authApi: AuthApi,
        private val tokenStore: TokenStore,
    ) : AuthRepository {
        override suspend fun login(
            identifier: String,
            password: String,
            rememberMe: Boolean,
        ): Result<Unit> {
            if (identifier.isBlank() || password.isBlank()) {
                return Result.failure(IllegalArgumentException("Vui lòng nhập đầy đủ thông tin"))
            }
            return runCatching { authApi.login(LoginRequestDto(identifier, password)) }
                .fold(
                    onSuccess = { tokens ->
                        tokenStore.saveSession(
                            tokens.access_token,
                            tokens.refresh_token,
                            tokens.expires_in,
                            rememberMe,
                            identifier,
                        )
                        Result.success(Unit)
                    },
                    onFailure = { error -> Result.failure(IllegalStateException(messageFor(error))) },
                )
        }

        // Best-effort: the server can't revoke the refresh token while offline, but the local
        // session still has to be cleared so the user can log out in the field without network.
        override suspend fun logout(): Result<Unit> {
            val refreshToken = tokenStore.currentTokens()?.refreshToken
            if (refreshToken != null) {
                runCatching { authApi.logout(LogoutRequestDto(refreshToken)) }
            }
            tokenStore.clear()
            return Result.success(Unit)
        }

        override suspend fun endSessionIfNotRemembered() = tokenStore.clearIfNotRemembered()

        override fun observeIsLoggedIn(): Flow<Boolean> = tokenStore.observeIsLoggedIn()

        override fun observeUsername(): Flow<String?> = tokenStore.observeUsername()

        private fun messageFor(error: Throwable): String =
            when (error) {
                is HttpException -> messageForHttpError(error)
                is IOException -> NETWORK_ERROR_MESSAGE
                else -> GENERIC_ERROR_MESSAGE
            }

        private fun messageForHttpError(error: HttpException): String =
            when (errorCodeFrom(error)) {
                "INVALID_CREDENTIALS" -> "Sai tên đăng nhập hoặc mật khẩu."
                "ACCOUNT_LOCKED" -> "Tài khoản đang bị khoá. Liên hệ quản trị viên."
                else -> GENERIC_ERROR_MESSAGE
            }

        private fun errorCodeFrom(error: HttpException): String? =
            runCatching {
                val body = error.response()?.errorBody()?.string() ?: return null
                errorJson.decodeFromString<ApiErrorEnvelope>(body).error.code
            }.getOrNull()

        private companion object {
            val errorJson = Json { ignoreUnknownKeys = true }
            const val NETWORK_ERROR_MESSAGE = "Không thể kết nối máy chủ. Kiểm tra mạng."
            const val GENERIC_ERROR_MESSAGE = "Đăng nhập thất bại. Vui lòng thử lại."
        }
    }
