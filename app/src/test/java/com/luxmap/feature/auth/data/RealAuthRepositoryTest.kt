package com.luxmap.feature.auth.data

import com.luxmap.core.network.AuthApi
import com.luxmap.core.security.TokenStore
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

private fun httpException(
    code: Int,
    errorCode: String? = null,
) = HttpException(
    Response.error<Any>(
        code,
        (if (errorCode == null) "" else """{"error":{"code":"$errorCode","message":"x"}}""")
            .toResponseBody("application/json".toMediaType()),
    ),
)

private val TOKENS =
    AuthTokenResponseDto(
        access_token = "access-1",
        refresh_token = "refresh-1",
        token_type = "Bearer",
        expires_in = 3600,
    )

class RealAuthRepositoryTest {
    @Test
    fun `new login clears previous identity and history cache but no pending work tables`() =
        runTest {
            val auth = mockk<AuthApi>()
            val tokens = mockk<TokenStore>(relaxed = true)
            val history = mockk<com.luxmap.feature.history.data.dao.HistoryCacheDao>(relaxed = true)
            coEvery { auth.login(any()) } returns TOKENS
            val result = RealAuthRepository(auth, tokens, history, mockk(relaxed = true)).login("B", "password", true)
            assertTrue(result.isSuccess)
            coVerify(exactly = 1) { tokens.clearCachedUserId() }
            coVerify(exactly = 1) { history.clear() }
        }

    @Test
    fun `offline logout clears identity and history cache`() =
        runTest {
            val auth = mockk<AuthApi>()
            val tokens = mockk<TokenStore>(relaxed = true)
            val history = mockk<com.luxmap.feature.history.data.dao.HistoryCacheDao>(relaxed = true)
            coEvery { tokens.currentTokens() } returns com.luxmap.core.security.SessionTokens("a", "r", 1)
            coEvery { auth.logout(any()) } throws java.io.IOException("offline")
            RealAuthRepository(auth, tokens, history, mockk(relaxed = true)).logout()
            coVerify(exactly = 1) { tokens.clear() }
            coVerify(exactly = 1) { history.clear() }
        }

    @Test
    fun `loginWithGoogle success saves session with the Google account's display name`() =
        runTest {
            val authApi = mockk<AuthApi>()
            val tokenStore = mockk<TokenStore>()
            coEvery { authApi.google(GoogleLoginRequestDto("id-token-1")) } returns TOKENS
            coJustRun { tokenStore.clearCachedUserId() }
            coJustRun { tokenStore.saveSession(any(), any(), any(), any(), any()) }
            val repository = RealAuthRepository(authApi, tokenStore, mockk(relaxed = true), mockk(relaxed = true))

            val result = repository.loginWithGoogle("id-token-1", rememberMe = true, displayName = "fe1@luxmap.vn")

            assertTrue(result.isSuccess)
            coVerify {
                tokenStore.saveSession("access-1", "refresh-1", 3600, true, "fe1@luxmap.vn")
            }
        }

    @Test
    fun `loginWithGoogle maps INVALID_CREDENTIALS to GoogleSignInFailed, not InvalidCredentials`() =
        runTest {
            val authApi = mockk<AuthApi>()
            val tokenStore = mockk<TokenStore>()
            coEvery { authApi.google(any()) } throws httpException(401, "INVALID_CREDENTIALS")
            val repository = RealAuthRepository(authApi, tokenStore, mockk(relaxed = true), mockk(relaxed = true))

            val result = repository.loginWithGoogle("id-token-1", rememberMe = true, displayName = "fe1@luxmap.vn")

            val failure = result.exceptionOrNull() as? LoginFailedException
            assertEquals(LoginFailureReason.GoogleSignInFailed, failure?.reason)
        }

    @Test
    fun `loginWithGoogle maps ACCOUNT_LOCKED the same way password login does`() =
        runTest {
            val authApi = mockk<AuthApi>()
            val tokenStore = mockk<TokenStore>()
            coEvery { authApi.google(any()) } throws httpException(403, "ACCOUNT_LOCKED")
            val repository = RealAuthRepository(authApi, tokenStore, mockk(relaxed = true), mockk(relaxed = true))

            val result = repository.loginWithGoogle("id-token-1", rememberMe = true, displayName = "fe1@luxmap.vn")

            val failure = result.exceptionOrNull() as? LoginFailedException
            assertEquals(LoginFailureReason.AccountLocked, failure?.reason)
        }

    @Test
    fun `password login still maps INVALID_CREDENTIALS to InvalidCredentials, unaffected by the Google change`() =
        runTest {
            val authApi = mockk<AuthApi>()
            val tokenStore = mockk<TokenStore>()
            coEvery { authApi.login(any()) } throws httpException(401, "INVALID_CREDENTIALS")
            val repository = RealAuthRepository(authApi, tokenStore, mockk(relaxed = true), mockk(relaxed = true))

            val result = repository.login("fe1", "wrong-password", rememberMe = true)

            val failure = result.exceptionOrNull() as? LoginFailedException
            assertEquals(LoginFailureReason.InvalidCredentials, failure?.reason)
        }

    @Test
    fun `a blocked login says how long to wait from Retry-After`() =
        runTest {
            val authApi = mockk<AuthApi>()
            val blocked =
                HttpException(
                    Response.error<Any>(
                        "".toResponseBody("application/json".toMediaType()),
                        okhttp3.Response.Builder()
                            .code(429)
                            .message("Too Many Requests")
                            .protocol(okhttp3.Protocol.HTTP_1_1)
                            .header("Retry-After", "610")
                            .request(okhttp3.Request.Builder().url("http://localhost/").build())
                            .build(),
                    ),
                )
            coEvery { authApi.login(any()) } throws blocked
            val repository = RealAuthRepository(authApi, mockk(), mockk(relaxed = true), mockk(relaxed = true))

            val failure = repository.login("crew", "x", rememberMe = true).exceptionOrNull() as? LoginFailedException

            assertEquals(LoginFailureReason.RateLimited, failure?.reason)
            assertEquals(
                "Đăng nhập sai quá nhiều lần nên tạm bị khoá. Đợi khoảng 11 phút rồi thử lại.",
                failure?.message,
            )
        }

    @Test
    fun `a blocked login without Retry-After still says it is a temporary block`() =
        runTest {
            val authApi = mockk<AuthApi>()
            coEvery { authApi.login(any()) } throws httpException(429, "RATE_LIMITED")
            val repository = RealAuthRepository(authApi, mockk(), mockk(relaxed = true), mockk(relaxed = true))

            val failure = repository.login("crew", "x", rememberMe = true).exceptionOrNull() as? LoginFailedException

            assertEquals(LoginFailureReason.RateLimited, failure?.reason)
            assertEquals("Đăng nhập sai quá nhiều lần nên tạm bị khoá. Đợi ít phút rồi thử lại.", failure?.message)
        }
}
