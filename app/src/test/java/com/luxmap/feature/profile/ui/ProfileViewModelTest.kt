package com.luxmap.feature.profile.ui

import com.luxmap.core.ble.BleConnectionState
import com.luxmap.core.ble.LuxSensorBleClient
import com.luxmap.core.network.ConnectivityObserver
import com.luxmap.core.sync.SyncQueueManager
import com.luxmap.feature.auth.data.AuthRepository
import com.luxmap.feature.profile.data.CurrentUserProfile
import com.luxmap.feature.profile.data.ProfileRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
import java.time.Instant

private val SAMPLE_PROFILE =
    CurrentUserProfile(
        fullName = "Nguyễn Văn Bình",
        email = "binh.nv@luxmap.vn",
        role = "field_engineer",
        communeIds = listOf("COM-001", "COM-002"),
    )

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {
    private fun viewModel(
        pendingCount: Int = 0,
        errorCount: Int = 0,
        lastSyncedAt: Instant? = null,
        isOnline: Boolean = true,
        bleState: BleConnectionState = BleConnectionState.Disconnected,
        profileResult: Result<CurrentUserProfile> = Result.success(SAMPLE_PROFILE),
    ): ProfileViewModel {
        val authRepository = mockk<AuthRepository>()
        every { authRepository.observeUsername() } returns flowOf("fe_thinh")
        val profileRepository = mockk<ProfileRepository>()
        coEvery { profileRepository.fetchCurrentUser() } returns profileResult
        val syncQueueManager = mockk<SyncQueueManager>()
        every { syncQueueManager.pendingCount } returns flowOf(pendingCount)
        every { syncQueueManager.errorCount } returns flowOf(errorCount)
        every { syncQueueManager.lastSyncedAt } returns flowOf(lastSyncedAt)
        val connectivityObserver = mockk<ConnectivityObserver>()
        every { connectivityObserver.isOnline } returns flowOf(isOnline)
        val bleClient = mockk<LuxSensorBleClient>()
        every { bleClient.connectionState } returns MutableStateFlow(bleState)
        return ProfileViewModel(
            authRepository,
            profileRepository,
            syncQueueManager,
            connectivityObserver,
            bleClient,
        )
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
    fun `pendingSyncCount and errorSyncCount reflect the sync queue manager`() =
        runTest {
            val vm = viewModel(pendingCount = 3, errorCount = 1)
            advanceUntilIdle()

            assertEquals(3, vm.pendingSyncCount.value)
            assertEquals(1, vm.errorSyncCount.value)
        }

    @Test
    fun `lastSyncedAt reflects the sync queue manager`() =
        runTest {
            val instant = Instant.parse("2026-10-06T12:00:00Z")
            val vm = viewModel(lastSyncedAt = instant)
            advanceUntilIdle()

            assertEquals(instant, vm.lastSyncedAt.value)
        }

    @Test
    fun `isOnline reflects the connectivity observer`() =
        runTest {
            val vm = viewModel(isOnline = false)
            advanceUntilIdle()

            assertEquals(false, vm.isOnline.value)
        }

    @Test
    fun `bleConnectionState reflects the ble client`() =
        runTest {
            val vm = viewModel(bleState = BleConnectionState.Connected)

            assertEquals(BleConnectionState.Connected, vm.bleConnectionState.value)
        }

    @Test
    fun `profileState loads the current user on init`() =
        runTest {
            val vm = viewModel(profileResult = Result.success(SAMPLE_PROFILE))
            advanceUntilIdle()

            val state = vm.profileState.value
            assertTrue(state is ProfileUiState.Success)
            assertEquals(SAMPLE_PROFILE, (state as ProfileUiState.Success).profile)
        }

    @Test
    fun `profileState becomes Error when fetchCurrentUser fails`() =
        runTest {
            val vm = viewModel(profileResult = Result.failure(IllegalStateException("network down")))
            advanceUntilIdle()

            val state = vm.profileState.value
            assertTrue(state is ProfileUiState.Error)
            assertEquals("network down", (state as ProfileUiState.Error).message)
        }
}
