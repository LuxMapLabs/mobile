package com.luxmap.feature.auth.data

import com.luxmap.core.network.AuthApi
import com.luxmap.core.offline.OfflineSnapshotDao
import com.luxmap.core.security.TokenStore
import com.luxmap.feature.history.data.dao.HistoryCacheDao
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
        private val historyCacheDao: HistoryCacheDao,
        private val offlineSnapshotDao: OfflineSnapshotDao,
    ) : AuthRepository {
        override suspend fun login(
            identifier: String,
            password: String,
            rememberMe: Boolean,
        ): Result<Unit> {
            return runCatching { authApi.login(LoginRequestDto(identifier, password)) }
                .fold(
                    onSuccess = { tokens ->
                        tokenStore.clearCachedUserId()
                        historyCacheDao.clear()
                        offlineSnapshotDao.clear()
                        tokenStore.saveSession(
                            tokens.access_token,
                            tokens.refresh_token,
                            tokens.expires_in,
                            rememberMe,
                            identifier,
                        )
                        Result.success(Unit)
                    },
                    onFailure = { error -> Result.failure(failureFor(error, isGoogle = false)) },
                )
        }

        override suspend fun loginWithGoogle(
            idToken: String,
            rememberMe: Boolean,
            displayName: String,
        ): Result<Unit> {
            return runCatching { authApi.google(GoogleLoginRequestDto(idToken)) }
                .fold(
                    onSuccess = { tokens ->
                        tokenStore.clearCachedUserId()
                        historyCacheDao.clear()
                        offlineSnapshotDao.clear()
                        tokenStore.saveSession(
                            tokens.access_token,
                            tokens.refresh_token,
                            tokens.expires_in,
                            rememberMe,
                            displayName,
                        )
                        Result.success(Unit)
                    },
                    onFailure = { error -> Result.failure(failureFor(error, isGoogle = true)) },
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
            historyCacheDao.clear()
            offlineSnapshotDao.clear()
            return Result.success(Unit)
        }

        override suspend fun endSessionIfNotRemembered() = tokenStore.clearIfNotRemembered()

        override fun observeIsLoggedIn(): Flow<Boolean> = tokenStore.observeIsLoggedIn()

        override fun observeUsername(): Flow<String?> = tokenStore.observeUsername()

        private fun failureFor(
            error: Throwable,
            isGoogle: Boolean,
        ): LoginFailedException =
            when (error) {
                is HttpException -> failureForHttpError(error, isGoogle)
                is IOException -> LoginFailedException(LoginFailureReason.Network, NETWORK_ERROR_MESSAGE)
                else ->
                    LoginFailedException(
                        if (isGoogle) LoginFailureReason.GoogleSignInFailed else LoginFailureReason.Unknown,
                        if (isGoogle) GENERIC_GOOGLE_ERROR_MESSAGE else GENERIC_ERROR_MESSAGE,
                    )
            }

        // ACCT-4: the backend answers EVERY Google failure other than a locked account — bad
        // id_token, unverified/unmatched email, an invited account with no password yet, or the
        // feature itself disabled server-side — with the same INVALID_CREDENTIALS code it uses for
        // a wrong password. There is no field to blame on this screen for a Google sign-in, so that
        // code maps to GoogleSignInFailed (banner-only) here instead of InvalidCredentials.
        private fun failureForHttpError(
            error: HttpException,
            isGoogle: Boolean,
        ): LoginFailedException =
            if (error.code() == HTTP_TOO_MANY_REQUESTS) {
                LoginFailedException(LoginFailureReason.RateLimited, rateLimitedMessage(error))
            } else {
                failureForErrorCode(error, isGoogle)
            }

        private fun failureForErrorCode(
            error: HttpException,
            isGoogle: Boolean,
        ): LoginFailedException =
            when (errorCodeFrom(error)) {
                "INVALID_CREDENTIALS" ->
                    if (isGoogle) {
                        LoginFailedException(LoginFailureReason.GoogleSignInFailed, GOOGLE_NOT_ELIGIBLE_MESSAGE)
                    } else {
                        LoginFailedException(LoginFailureReason.InvalidCredentials, "Sai tên đăng nhập hoặc mật khẩu.")
                    }
                "ACCOUNT_LOCKED" ->
                    LoginFailedException(
                        LoginFailureReason.AccountLocked,
                        "Tài khoản đang bị khoá. Liên hệ quản trị viên.",
                    )
                else ->
                    LoginFailedException(
                        if (isGoogle) LoginFailureReason.GoogleSignInFailed else LoginFailureReason.Unknown,
                        if (isGoogle) GENERIC_GOOGLE_ERROR_MESSAGE else GENERIC_ERROR_MESSAGE,
                    )
            }

        // The server sends how long the block lasts in Retry-After (seconds). Saying so stops the
        // user from tapping again and again, which only keeps the block going.
        private fun rateLimitedMessage(error: HttpException): String {
            val seconds = error.response()?.headers()?.get("Retry-After")?.toLongOrNull()
            val wait = seconds?.let { "khoảng ${maxOf(1, (it + 59) / 60)} phút" } ?: "ít phút"
            return "Đăng nhập sai quá nhiều lần nên tạm bị khoá. Đợi $wait rồi thử lại."
        }

        private fun errorCodeFrom(error: HttpException): String? =
            runCatching {
                val body = error.response()?.errorBody()?.string() ?: return null
                errorJson.decodeFromString<ApiErrorEnvelope>(body).error.code
            }.getOrNull()

        private companion object {
            val errorJson = Json { ignoreUnknownKeys = true }
            const val HTTP_TOO_MANY_REQUESTS = 429
            const val NETWORK_ERROR_MESSAGE = "Kết nối thất bại. Vui lòng kiểm tra lại."
            const val GENERIC_ERROR_MESSAGE = "Đăng nhập thất bại. Vui lòng thử lại."
            const val GENERIC_GOOGLE_ERROR_MESSAGE = "Đăng nhập bằng Google thất bại. Vui lòng thử lại."
            const val GOOGLE_NOT_ELIGIBLE_MESSAGE =
                "Tài khoản Google này chưa được cấp quyền truy cập LuxMap. Liên hệ quản trị viên."
        }
    }
