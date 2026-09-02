package com.luxmap.core.network

import com.luxmap.core.security.TokenStore
import com.luxmap.feature.auth.data.RefreshRequestDto
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject

// Tự làm mới access token khi một request nhận 401 — đúng tiêu chí "Không bị đăng xuất giữa
// chừng" của FM-05. Đồng bộ hoá bằng synchronized: OkHttp có thể gọi authenticate() từ nhiều
// luồng cùng lúc khi nhiều request rớt 401 song song — chỉ luồng đầu tiên gọi /auth/refresh
// thật, các luồng còn lại (chờ lock) phát hiện token đã đổi và dùng luôn token mới.
class TokenAuthenticator
    @Inject
    constructor(
        private val authApi: AuthApi,
        private val tokenStore: TokenStore,
    ) : Authenticator {
        override fun authenticate(
            route: Route?,
            response: Response,
        ): Request? {
            if (responseCount(response) >= MAX_RETRIES) {
                forceLogout()
                return null
            }

            val failedAccessToken = accessTokenFrom(response.request)

            synchronized(this) {
                val current = tokenStore.currentTokensBlocking() ?: return forceLogoutAndFail()

                if (failedAccessToken != null && current.accessToken != failedAccessToken) {
                    return response.request.newBuilder()
                        .header("Authorization", "Bearer ${current.accessToken}")
                        .build()
                }

                val refreshed =
                    runCatching {
                        runBlocking { authApi.refresh(RefreshRequestDto(current.refreshToken)) }
                    }.getOrNull() ?: return forceLogoutAndFail()

                runBlocking {
                    tokenStore.saveSession(
                        refreshed.access_token,
                        refreshed.refresh_token,
                        refreshed.expires_in,
                    )
                }

                return response.request.newBuilder()
                    .header("Authorization", "Bearer ${refreshed.access_token}")
                    .build()
            }
        }

        private fun forceLogoutAndFail(): Request? {
            forceLogout()
            return null
        }

        private fun forceLogout() {
            runBlocking { tokenStore.clear() }
        }

        private fun accessTokenFrom(request: Request): String? =
            request.header("Authorization")?.removePrefix("Bearer ")?.takeIf { it.isNotBlank() }

        private fun responseCount(response: Response): Int {
            var result = 1
            var prior = response.priorResponse
            while (prior != null) {
                result++
                prior = prior.priorResponse
            }
            return result
        }

        private companion object {
            const val MAX_RETRIES = 2
        }
    }
