package com.luxmap.feature.survey.ui.capture

import app.cash.turbine.test
import com.luxmap.core.ble.BleConnectionState
import com.luxmap.core.ble.LuxSensorBleClient
import com.luxmap.core.location.GpsSignalState
import com.luxmap.feature.survey.capture.PackageResult
import com.luxmap.feature.survey.capture.SurveyCaptureController
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CaptureViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `starts awaiting BLE connection and becomes Ready once connected`() =
        runTest {
            val connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected)
            val luxClient = mockk<LuxSensorBleClient>()
            every { luxClient.connectionState } returns connectionState
            // CaptureViewModel's init now calls connect() once (Critical #1 fix) - luxClient is a
            // strict (non-relaxed) mock, so this needs an explicit stub in every test.
            every { luxClient.connect(any()) } just Runs
            val controller = mockk<SurveyCaptureController>(relaxed = true)
            // Relaxed mockk cannot auto-answer a suspend collect() on a generic StateFlow (it
            // throws KotlinNothingValueException), so every test needs an explicit stub here, same
            // as luxClient.connectionState above.
            every { controller.gpsSignalState } returns MutableStateFlow(GpsSignalState.Ok)
            val viewModel = CaptureViewModel(luxClient, controller)

            viewModel.uiState.test {
                assertEquals(CaptureUiState.AwaitingBleConnection, awaitItem())
                connectionState.value = BleConnectionState.Connected
                assertEquals(CaptureUiState.Ready, awaitItem())
            }
        }

    @Test
    fun `starting a recording generates a session id and tells the controller to start`() =
        runTest {
            val connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Connected)
            val luxClient = mockk<LuxSensorBleClient>()
            every { luxClient.connectionState } returns connectionState
            // CaptureViewModel's init now calls connect() once (Critical #1 fix) - luxClient is a
            // strict (non-relaxed) mock, so this needs an explicit stub in every test.
            every { luxClient.connect(any()) } just Runs
            val controller = mockk<SurveyCaptureController>(relaxed = true)
            every { controller.gpsSignalState } returns MutableStateFlow(GpsSignalState.Ok)
            val viewModel = CaptureViewModel(luxClient, controller)

            viewModel.uiState.test {
                assertEquals(CaptureUiState.AwaitingBleConnection, awaitItem())
                assertEquals(CaptureUiState.Ready, awaitItem())
                viewModel.onStartRecording(surveySweepId = "SWEEP-1", luxDeviceAddress = "AA:BB:CC:DD:EE:FF")
                val recording = awaitItem() as CaptureUiState.Recording
                assertEquals(false, recording.gpsSignalLost)
            }
            verify { controller.startSession(any(), "SWEEP-1", "AA:BB:CC:DD:EE:FF") }
        }

    @Test
    fun `stopping a recording moves through Packaging to Packaged on success`() =
        runTest {
            val connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Connected)
            val luxClient = mockk<LuxSensorBleClient>()
            every { luxClient.connectionState } returns connectionState
            // CaptureViewModel's init now calls connect() once (Critical #1 fix) - luxClient is a
            // strict (non-relaxed) mock, so this needs an explicit stub in every test.
            every { luxClient.connect(any()) } just Runs
            val controller = mockk<SurveyCaptureController>(relaxed = true)
            every { controller.gpsSignalState } returns MutableStateFlow(GpsSignalState.Ok)
            every { controller.stopSession() } returns flowOf(PackageResult.Success("/data/manifest.json"))
            val viewModel = CaptureViewModel(luxClient, controller)

            viewModel.uiState.test {
                awaitItem() // AwaitingBleConnection
                awaitItem() // Ready
                viewModel.onStartRecording(surveySweepId = "SWEEP-1", luxDeviceAddress = "AA:BB:CC:DD:EE:FF")
                awaitItem() // Recording
                viewModel.onStopRecording()
                assertEquals(CaptureUiState.Packaging, awaitItem())
                val packaged = awaitItem() as CaptureUiState.Packaged
                assertTrue(packaged.sessionId.isNotBlank())
            }
        }

    @Test
    fun `stopping a recording surfaces PackagingFailed on failure`() =
        runTest {
            val connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Connected)
            val luxClient = mockk<LuxSensorBleClient>()
            every { luxClient.connectionState } returns connectionState
            // CaptureViewModel's init now calls connect() once (Critical #1 fix) - luxClient is a
            // strict (non-relaxed) mock, so this needs an explicit stub in every test.
            every { luxClient.connect(any()) } just Runs
            val controller = mockk<SurveyCaptureController>(relaxed = true)
            every { controller.gpsSignalState } returns MutableStateFlow(GpsSignalState.Ok)
            every { controller.stopSession() } returns flowOf(PackageResult.Failure("disk full"))
            val viewModel = CaptureViewModel(luxClient, controller)

            viewModel.uiState.test {
                awaitItem() // AwaitingBleConnection
                awaitItem() // Ready
                viewModel.onStartRecording(surveySweepId = "SWEEP-1", luxDeviceAddress = "AA:BB:CC:DD:EE:FF")
                awaitItem() // Recording
                viewModel.onStopRecording()
                assertEquals(CaptureUiState.Packaging, awaitItem())
                val failed = awaitItem() as CaptureUiState.PackagingFailed
                assertEquals("disk full", failed.reason)
            }
        }

    @Test
    fun `GPS signal lost while recording raises the warning flag without leaving Recording`() =
        runTest {
            val connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Connected)
            val luxClient = mockk<LuxSensorBleClient>()
            every { luxClient.connectionState } returns connectionState
            // CaptureViewModel's init now calls connect() once (Critical #1 fix) - luxClient is a
            // strict (non-relaxed) mock, so this needs an explicit stub in every test.
            every { luxClient.connect(any()) } just Runs
            val gpsSignalState = MutableStateFlow<GpsSignalState>(GpsSignalState.Ok)
            val controller = mockk<SurveyCaptureController>(relaxed = true)
            every { controller.gpsSignalState } returns gpsSignalState
            val viewModel = CaptureViewModel(luxClient, controller)

            viewModel.uiState.test {
                awaitItem() // AwaitingBleConnection
                awaitItem() // Ready
                viewModel.onStartRecording(surveySweepId = "SWEEP-1", luxDeviceAddress = "AA:BB:CC:DD:EE:FF")
                val recording = awaitItem() as CaptureUiState.Recording
                assertEquals(false, recording.gpsSignalLost)

                gpsSignalState.value = GpsSignalState.Lost

                val warned = awaitItem() as CaptureUiState.Recording
                assertEquals(true, warned.gpsSignalLost)
            }
        }
}
