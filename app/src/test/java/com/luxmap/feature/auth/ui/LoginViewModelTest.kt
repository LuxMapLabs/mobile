package com.luxmap.feature.auth.ui

import android.content.Context
import com.luxmap.core.network.ConnectivityObserver
import com.luxmap.feature.auth.data.AuthRepository
import com.luxmap.feature.auth.data.LoginFailedException
import com.luxmap.feature.auth.data.LoginFailureReason
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {
    private val context = mockk<Context>()

    private fun viewModel(
        isOnline: Boolean = true,
        isLoggedIn: Boolean = false,
    ): Triple<LoginViewModel, AuthRepository, GoogleSignInHelper> {
        val authRepository = mockk<AuthRepository>()
        coEvery { authRepository.observeIsLoggedIn() } returns flowOf(isLoggedIn)
        val googleSignInHelper = mockk<GoogleSignInHelper>()
        val connectivityObserver = mockk<ConnectivityObserver>()
        every { connectivityObserver.isOnline } returns flowOf(isOnline)
        val vm = LoginViewModel(authRepository, googleSignInHelper, connectivityObserver)
        return Triple(vm, authRepository, googleSignInHelper)
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `onGoogleSignInClick while offline fails fast without touching the helper`() =
        runTest {
            val (vm, _, googleSignInHelper) = viewModel(isOnline = false)
            advanceUntilIdle()

            vm.onGoogleSignInClick(context)
            advanceUntilIdle()

            val state = vm.uiState.value as LoginUiState.Error
            assertEquals(LoginFailureReason.Network, state.reason)
            coVerify(exactly = 0) { googleSignInHelper.signIn(any()) }
        }

    @Test
    fun `onGoogleSignInClick success logs in and ends on Success`() =
        runTest {
            val (vm, authRepository, googleSignInHelper) = viewModel()
            advanceUntilIdle()
            coEvery { googleSignInHelper.signIn(context) } returns
                GoogleSignInOutcome.Success(idToken = "id-token-1", email = "fe1@luxmap.vn")
            coEvery { authRepository.loginWithGoogle("id-token-1", true, "fe1@luxmap.vn") } returns
                Result.success(Unit)

            vm.onGoogleSignInClick(context)
            advanceUntilIdle()

            assertEquals(LoginUiState.Success, vm.uiState.value)
        }

    @Test
    fun `onGoogleSignInClick cancelled goes back to Idle, not Error`() =
        runTest {
            val (vm, _, googleSignInHelper) = viewModel()
            advanceUntilIdle()
            coEvery { googleSignInHelper.signIn(context) } returns GoogleSignInOutcome.Cancelled

            vm.onGoogleSignInClick(context)
            advanceUntilIdle()

            assertEquals(LoginUiState.Idle, vm.uiState.value)
        }

    @Test
    fun `onGoogleSignInClick helper failure shows its message under GoogleSignInFailed`() =
        runTest {
            val (vm, _, googleSignInHelper) = viewModel()
            advanceUntilIdle()
            coEvery { googleSignInHelper.signIn(context) } returns
                GoogleSignInOutcome.Failed("Không tìm thấy tài khoản Google trên thiết bị này.")

            vm.onGoogleSignInClick(context)
            advanceUntilIdle()

            val state = vm.uiState.value as LoginUiState.Error
            assertEquals(LoginFailureReason.GoogleSignInFailed, state.reason)
            assertEquals("Không tìm thấy tài khoản Google trên thiết bị này.", state.message)
        }

    @Test
    fun `onGoogleSignInClick backend rejection surfaces the repository's failure reason`() =
        runTest {
            val (vm, authRepository, googleSignInHelper) = viewModel()
            advanceUntilIdle()
            coEvery { googleSignInHelper.signIn(context) } returns
                GoogleSignInOutcome.Success(idToken = "id-token-1", email = "fe1@luxmap.vn")
            coEvery { authRepository.loginWithGoogle(any(), any(), any()) } returns
                Result.failure(LoginFailedException(LoginFailureReason.GoogleSignInFailed, "not eligible"))

            vm.onGoogleSignInClick(context)
            advanceUntilIdle()

            val state = vm.uiState.value as LoginUiState.Error
            assertEquals(LoginFailureReason.GoogleSignInFailed, state.reason)
            assertTrue(state.message.contains("not eligible"))
        }
}
