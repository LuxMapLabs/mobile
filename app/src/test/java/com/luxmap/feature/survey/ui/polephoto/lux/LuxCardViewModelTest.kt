package com.luxmap.feature.survey.ui.polephoto.lux

import com.luxmap.core.ble.BleConnectionState
import com.luxmap.core.ble.LuxDevice
import com.luxmap.core.ble.LuxDevicePreferences
import com.luxmap.core.ble.LuxDeviceScanner
import com.luxmap.core.ble.LuxSample
import com.luxmap.core.ble.LuxSensorBleClient
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LuxCardViewModelTest {
    private val meter = LuxDevice("LuxMap_ESP32", "AA:BB")
    private val paired = MutableStateFlow<LuxDevice?>(meter)
    private val connection = MutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected)
    private val samples = MutableSharedFlow<LuxSample>(extraBufferCapacity = 16)
    private val client = mockk<LuxSensorBleClient>(relaxed = true)
    private val preferences = mockk<LuxDevicePreferences>()
    private val scanner = mockk<LuxDeviceScanner>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { client.connectionState } returns connection
        every { client.samples } returns samples
        every { client.connect(any()) } answers { connection.value = BleConnectionState.Connecting }
        every { client.disconnect() } answers { connection.value = BleConnectionState.Disconnected }
        every { preferences.pairedDevice } returns paired
        every { scanner.isBluetoothEnabled() } returns true
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = LuxCardViewModel(client, preferences, scanner)

    private suspend fun LuxCardViewModel.connectAndFeed(vararg values: Double) {
        expand(hasPermission = true)
        connection.value = BleConnectionState.Connected
        values.forEach { samples.emit(LuxSample(it, 0L)) }
    }

    @Test
    fun `starts collapsed and does not connect`() =
        runTest {
            val vm = viewModel()

            assertEquals(LuxCardUiState.Collapsed, vm.uiState.value)
            verify(exactly = 0) { client.connect(any()) }
        }

    @Test
    fun `expanding without a paired meter asks to pair`() =
        runTest {
            paired.value = null
            val vm = viewModel()

            vm.expand(hasPermission = true)

            assertEquals(LuxCardUiState.NotPaired, vm.uiState.value)
            verify(exactly = 0) { client.connect(any()) }
        }

    @Test
    fun `expanding without permission asks for it`() =
        runTest {
            val vm = viewModel()

            vm.expand(hasPermission = false)

            assertEquals(LuxCardUiState.PermissionMissing, vm.uiState.value)
            verify(exactly = 0) { client.connect(any()) }
        }

    @Test
    fun `expanding with bluetooth off asks to turn it on`() =
        runTest {
            every { scanner.isBluetoothEnabled() } returns false
            val vm = viewModel()

            vm.expand(hasPermission = true)

            assertEquals(LuxCardUiState.BluetoothOff, vm.uiState.value)
            verify(exactly = 0) { client.connect(any()) }
        }

    @Test
    fun `expanding connects to the paired meter`() =
        runTest {
            val vm = viewModel()

            vm.expand(hasPermission = true)

            verify { client.connect("AA:BB") }
            assertEquals(LuxCardUiState.Connecting, vm.uiState.value)
        }

    @Test
    fun `pairing while expanded connects right away`() =
        runTest {
            paired.value = null
            val vm = viewModel()
            vm.expand(hasPermission = true)

            paired.value = meter

            verify { client.connect("AA:BB") }
        }

    @Test
    fun `live readings become stable after five close samples`() =
        runTest {
            val vm = viewModel()

            vm.connectAndFeed(12.1, 12.4, 12.3)
            val warming = vm.uiState.value as LuxCardUiState.Live
            assertEquals(3, warming.sampleCount)
            assertNull(warming.stableLux)

            samples.emit(LuxSample(12.2, 0L))
            samples.emit(LuxSample(12.5, 0L))
            val stable = vm.uiState.value as LuxCardUiState.Live
            assertEquals(12.3, stable.stableLux)
            assertEquals(12.5, stable.latestLux)
        }

    @Test
    fun `record keeps the stable value and disconnects`() =
        runTest {
            val vm = viewModel()
            vm.connectAndFeed(12.1, 12.4, 12.3, 12.2, 12.5)

            vm.record()

            val recorded = vm.uiState.value as LuxCardUiState.Recorded
            assertEquals(12.3, recorded.lux, 0.0)
            assertEquals(recorded, vm.recordedReading)
            verify { client.disconnect() }
        }

    @Test
    fun `record does nothing before the reading is stable`() =
        runTest {
            val vm = viewModel()
            vm.connectAndFeed(10.0, 15.0)

            vm.record()

            assertTrue(vm.uiState.value is LuxCardUiState.Live)
            assertNull(vm.recordedReading)
        }

    @Test
    fun `losing the connection clears the samples`() =
        runTest {
            val vm = viewModel()
            vm.connectAndFeed(12.1, 12.4, 12.3)

            connection.value = BleConnectionState.Disconnected
            connection.value = BleConnectionState.Connected

            assertEquals(0, (vm.uiState.value as LuxCardUiState.Live).sampleCount)
        }

    @Test
    fun `a dropped connection is shown as disconnected`() =
        runTest {
            val vm = viewModel()
            vm.connectAndFeed()

            connection.value = BleConnectionState.Disconnected

            assertEquals(LuxCardUiState.Disconnected, vm.uiState.value)
        }

    @Test
    fun `remeasure drops the recorded value and reconnects`() =
        runTest {
            val vm = viewModel()
            vm.connectAndFeed(12.1, 12.4, 12.3, 12.2, 12.5)
            vm.record()

            vm.remeasure()

            assertNull(vm.recordedReading)
            verify(exactly = 2) { client.connect("AA:BB") }
            assertEquals(LuxCardUiState.Connecting, vm.uiState.value)
        }

    @Test
    fun `collapse discards the reading and disconnects`() =
        runTest {
            val vm = viewModel()
            vm.connectAndFeed(12.1, 12.4, 12.3, 12.2, 12.5)
            vm.record()

            vm.collapse()

            assertEquals(LuxCardUiState.Collapsed, vm.uiState.value)
            assertNull(vm.recordedReading)
        }

    @Test
    fun `samples are ignored while collapsed`() =
        runTest {
            val vm = viewModel()
            connection.value = BleConnectionState.Connected
            samples.emit(LuxSample(12.0, 0L))

            vm.expand(hasPermission = true)
            connection.value = BleConnectionState.Connected

            assertEquals(0, (vm.uiState.value as LuxCardUiState.Live).sampleCount)
        }
}
