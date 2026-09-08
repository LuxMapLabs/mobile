package com.luxmap.core.network

import com.luxmap.core.security.TokenStore
import com.luxmap.feature.auth.data.RefreshRequestDto
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject

// Refreshes the access token automatically when a request gets a 401 — this is FM-05's "never
// silently logged out" requirement. Uses synchronized because OkHttp can call authenticate()
// from several threads at once when multiple requests fail with 401 together — only the first
// thread makes the real /auth/refresh call; the others (waiting on the lock) see the token has
// already changed and reuse it.
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
