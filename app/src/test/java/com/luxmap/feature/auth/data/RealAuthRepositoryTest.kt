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
    fun `new login clears previous identity and survey list but no pending work tables`() =
        runTest {
            val auth = mockk<AuthApi>()
            val tokens = mockk<TokenStore>(relaxed = true)
            val surveys = mockk<com.luxmap.feature.survey.data.dao.SurveyRouteCacheDao>(relaxed = true)
            coEvery { auth.login(any()) } returns TOKENS
            val result = RealAuthRepository(auth, tokens, surveys).login("B", "password", true)
            assertTrue(result.isSuccess)
            coVerify(exactly = 1) { tokens.clearCachedUserId() }
            coVerify(exactly = 1) { surveys.deleteAll() }
        }

    @Test
    fun `offline logout clears identity and survey list`() =
        runTest {
            val auth = mockk<AuthApi>()
            val tokens = mockk<TokenStore>(relaxed = true)
            val surveys = mockk<com.luxmap.feature.survey.data.dao.SurveyRouteCacheDao>(relaxed = true)
            coEvery { tokens.currentTokens() } returns com.luxmap.core.security.SessionTokens("a", "r", 1)
            coEvery { auth.logout(any()) } throws java.io.IOException("offline")
            RealAuthRepository(auth, tokens, surveys).logout()
            coVerify(exactly = 1) { tokens.clear() }
            coVerify(exactly = 1) { surveys.deleteAll() }
        }

    @Test
    fun `loginWithGoogle success saves session with the Google account's display name`() =
        runTest {
            val authApi = mockk<AuthApi>()
            val tokenStore = mockk<TokenStore>()
            coEvery { authApi.google(GoogleLoginRequestDto("id-token-1")) } returns TOKENS
            coJustRun { tokenStore.clearCachedUserId() }
            coJustRun { tokenStore.saveSession(any(), any(), any(), any(), any()) }
            val repository = RealAuthRepository(authApi, tokenStore, mockk(relaxed = true))

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
            val repository = RealAuthRepository(authApi, tokenStore, mockk(relaxed = true))

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
            val repository = RealAuthRepository(authApi, tokenStore, mockk(relaxed = true))

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
            val repository = RealAuthRepository(authApi, tokenStore, mockk(relaxed = true))

            val result = repository.login("fe1", "wrong-password", rememberMe = true)

            val failure = result.exceptionOrNull() as? LoginFailedException
            assertEquals(LoginFailureReason.InvalidCredentials, failure?.reason)
        }
}
