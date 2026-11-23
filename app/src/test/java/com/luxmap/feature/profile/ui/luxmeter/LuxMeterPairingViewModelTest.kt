package com.luxmap.feature.profile.ui.luxmeter

import com.luxmap.core.ble.LuxDevice
import com.luxmap.core.ble.LuxDevicePreferences
import com.luxmap.core.ble.LuxDeviceScanner
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LuxMeterPairingViewModelTest {
    private val meter = LuxDevice("ESP32C3_LUX", "AA:BB")
    private val phone = LuxDevice("Tai nghe", "CC:DD")
    private val paired = MutableStateFlow<LuxDevice?>(null)
    private val preferences = mockk<LuxDevicePreferences>(relaxed = true)
    private val scanner = mockk<LuxDeviceScanner>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { preferences.pairedDevice } returns paired
        coEvery { preferences.save(any()) } answers { paired.value = firstArg() }
        coEvery { preferences.clear() } answers { paired.value = null }
        every { scanner.isBluetoothEnabled() } returns true
        every { scanner.scan(any()) } returns flowOf(meter)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(): LuxMeterPairingViewModel =
        LuxMeterPairingViewModel(preferences, scanner).also { vm ->
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect { } }
        }

    @Test
    fun `shows the saved device`() =
        runTest {
            paired.value = meter
            val vm = viewModel()

            assertEquals(meter, vm.uiState.value.pairedDevice)
        }

    @Test
    fun `opening without permission does not touch bluetooth`() =
        runTest {
            val vm = viewModel()

            vm.openSheet(hasPermission = false)

            assertTrue(vm.uiState.value.isSheetOpen)
            assertFalse(vm.uiState.value.hasPermission)
            verify(exactly = 0) { scanner.scan(any()) }
        }

    @Test
    fun `opening with permission starts scanning right away`() =
        runTest {
            val vm = viewModel()

            vm.openSheet(hasPermission = true)

            assertEquals(listOf(meter), vm.uiState.value.foundDevices)
            assertTrue(vm.uiState.value.isBluetoothOn)
        }

    @Test
    fun `granting permission starts scanning`() =
        runTest {
            val vm = viewModel()
            vm.openSheet(hasPermission = false)

            vm.onPermissionResult(granted = true)

            assertEquals(listOf(meter), vm.uiState.value.foundDevices)
        }

    @Test
    fun `bluetooth off does not scan`() =
        runTest {
            every { scanner.isBluetoothEnabled() } returns false
            val vm = viewModel()

            vm.openSheet(hasPermission = true)

            assertFalse(vm.uiState.value.isBluetoothOn)
            verify(exactly = 0) { scanner.scan(any()) }
        }

    @Test
    fun `a security error is treated as bluetooth unavailable`() =
        runTest {
            every { scanner.isBluetoothEnabled() } throws SecurityException("denied")
            val vm = viewModel()

            vm.openSheet(hasPermission = true)

            assertFalse(vm.uiState.value.isBluetoothOn)
        }

    @Test
    fun `scan collects unique devices then stops`() =
        runTest {
            val found = MutableSharedFlow<LuxDevice>()
            every { scanner.scan(any()) } returns found
            val vm = viewModel()
            vm.openSheet(hasPermission = true)

            vm.scan()
            assertTrue(vm.uiState.value.isScanning)
            found.emit(phone)
            found.emit(phone)
            found.emit(meter)

            assertEquals(listOf(phone, meter), vm.uiState.value.foundDevices)
        }

    @Test
    fun `scan finishing clears the scanning flag`() =
        runTest {
            every { scanner.scan(any()) } returns flowOf(phone)
            val vm = viewModel()
            vm.openSheet(hasPermission = true)

            vm.scan()
            advanceUntilIdle()

            assertFalse(vm.uiState.value.isScanning)
            assertEquals(listOf(phone), vm.uiState.value.foundDevices)
        }

    @Test
    fun `selecting a device saves it and closes the sheet`() =
        runTest {
            val vm = viewModel()
            vm.openSheet(hasPermission = true)

            vm.select(meter)
            advanceUntilIdle()

            coVerify { preferences.save(meter) }
            assertEquals(meter, vm.uiState.value.pairedDevice)
            assertFalse(vm.uiState.value.isSheetOpen)
        }

    @Test
    fun `unpair clears the saved device`() =
        runTest {
            paired.value = meter
            val vm = viewModel()

            vm.unpair()
            advanceUntilIdle()

            assertNull(vm.uiState.value.pairedDevice)
        }

    @Test
    fun `dismiss closes the sheet and stops scanning`() =
        runTest {
            every { scanner.scan(any()) } returns MutableSharedFlow()
            val vm = viewModel()
            vm.openSheet(hasPermission = true)
            vm.scan()

            vm.dismissSheet()

            assertFalse(vm.uiState.value.isSheetOpen)
            assertFalse(vm.uiState.value.isScanning)
        }
}
