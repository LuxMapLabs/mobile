package com.luxmap.feature.survey.ui.capture

import android.view.Surface
import app.cash.turbine.test
import com.luxmap.core.ble.BleConnectionState
import com.luxmap.core.ble.LuxDevice
import com.luxmap.core.ble.LuxDevicePreferences
import com.luxmap.core.ble.LuxDeviceScanner
import com.luxmap.core.ble.LuxSample
import com.luxmap.core.ble.LuxSensorBleClient
import com.luxmap.core.common.StorageMonitor
import com.luxmap.core.location.GpsSignalState
import com.luxmap.core.location.TrackPoint
import com.luxmap.feature.survey.capture.PackageResult
import com.luxmap.feature.survey.capture.PreRecordGpsAccuracyTracker
import com.luxmap.feature.survey.capture.RecordingStartResult
import com.luxmap.feature.survey.capture.SurveyCaptureController
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private val SENSOR_1 = LuxDevice(name = "LUX-001", address = "AA:AA:AA:AA:AA:AA")
private val SENSOR_2 = LuxDevice(name = "LUX-002", address = "BB:BB:BB:BB:BB:BB")

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

    // Builds a ViewModel whose luxClient/scanner/preferences/controller behavior is fully
    // controlled by the caller - every test wires only the mocks its scenario needs.
    private fun viewModel(
        connectionState: MutableStateFlow<BleConnectionState> = MutableStateFlow(BleConnectionState.Disconnected),
        rememberedDevice: LuxDevice? = null,
        scanResults: List<LuxDevice> = emptyList(),
        gpsSignalState: MutableStateFlow<GpsSignalState> = MutableStateFlow(GpsSignalState.Ok),
        recordingStartResult: MutableStateFlow<RecordingStartResult?> = MutableStateFlow(null),
        storageMonitor: StorageMonitor = mockk<StorageMonitor>().also { every { it.freeBytes() } returns 0L },
        // Defaults to already-ready: most tests here are about other behavior and just need
        // onStartRecording() to work from Ready, same as before this gate existed. Tests about the
        // gate itself pass their own MutableStateFlow(false) and flip it explicitly.
        gpsReadyToRecord: MutableStateFlow<Boolean> = MutableStateFlow(true),
    ): Triple<CaptureViewModel, LuxSensorBleClient, SurveyCaptureController> {
        val luxClient = mockk<LuxSensorBleClient>()
        every { luxClient.connectionState } returns connectionState
        every { luxClient.connect(any()) } just Runs
        every { luxClient.disconnect() } just Runs
        every { luxClient.samples } returns MutableSharedFlow(extraBufferCapacity = 1)

        val scanner = mockk<LuxDeviceScanner>()
        every { scanner.scan() } returns flowOf(*scanResults.toTypedArray())

        val preferences = mockk<LuxDevicePreferences>()
        coEvery { preferences.lastDevice() } returns rememberedDevice
        coEvery { preferences.saveLastDevice(any()) } just Runs

        val controller = mockk<SurveyCaptureController>(relaxed = true)
        every { controller.gpsSignalState } returns gpsSignalState
        every { controller.recordingStartResult } returns recordingStartResult
        every { controller.liveGpsPoint } returns MutableStateFlow(null)
        every { controller.distanceMeters } returns MutableStateFlow(0f)

        val preRecordGpsAccuracyTracker = mockk<PreRecordGpsAccuracyTracker>(relaxed = true)
        every { preRecordGpsAccuracyTracker.readyToRecord } returns gpsReadyToRecord

        val viewModel =
            CaptureViewModel(luxClient, scanner, preferences, controller, storageMonitor, preRecordGpsAccuracyTracker)
        return Triple(viewModel, luxClient, controller)
    }

    @Test
    fun `with no remembered device, starts scanning and connects once a device is selected`() =
        runTest {
            val connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected)
            val (viewModel, luxClient, _) =
                viewModel(
                    connectionState = connectionState,
                    rememberedDevice = null,
                    scanResults = listOf(SENSOR_1, SENSOR_2),
                )

            viewModel.uiState.test {
                assertEquals(CaptureUiState.Scanning(), awaitItem())
                assertEquals(CaptureUiState.Scanning(listOf(SENSOR_1)), awaitItem())
                assertEquals(CaptureUiState.Scanning(listOf(SENSOR_1, SENSOR_2)), awaitItem())

                viewModel.onDeviceSelected(SENSOR_2)
                assertEquals(CaptureUiState.Connecting(SENSOR_2.name), awaitItem())

                connectionState.value = BleConnectionState.Connected
                assertEquals(CaptureUiState.Ready(gpsReadyToRecord = true), awaitItem())
            }
            verify { luxClient.connect(SENSOR_2.address) }
        }

    @Test
    fun `a later scan result for the same address with a resolved name replaces the nameless entry`() =
        runTest {
            // Some BLE peripherals (e.g. ESP32 devices) put their name in the scan response
            // packet, not the primary advertisement - Android can call onScanResult once with
            // name = null before the scan response arrives, then again for the same address
            // once it does. The list must pick up that later name, not freeze on the first hit.
            val nameless = LuxDevice(name = null, address = SENSOR_1.address)
            val (viewModel, _, _) =
                viewModel(scanResults = listOf(nameless, SENSOR_1))

            viewModel.uiState.test {
                assertEquals(CaptureUiState.Scanning(), awaitItem())
                assertEquals(CaptureUiState.Scanning(listOf(nameless)), awaitItem())
                assertEquals(CaptureUiState.Scanning(listOf(SENSOR_1)), awaitItem())
            }
        }

    @Test
    fun `a remembered device connects automatically without scanning`() =
        runTest {
            val connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected)
            val (viewModel, luxClient, _) =
                viewModel(connectionState = connectionState, rememberedDevice = SENSOR_1)

            viewModel.uiState.test {
                // Scanning() is the StateFlow's field default - it is always the first value a
                // collector sees, before init's coroutine has had a chance to check for a
                // remembered device.
                assertEquals(CaptureUiState.Scanning(), awaitItem())
                assertEquals(CaptureUiState.Connecting(SENSOR_1.name), awaitItem())
                connectionState.value = BleConnectionState.Connected
                assertEquals(CaptureUiState.Ready(gpsReadyToRecord = true), awaitItem())
            }
            verify { luxClient.connect(SENSOR_1.address) }
        }

    @Test
    fun `onStartRecording is a hard no-op while the GPS accuracy gate is not ready`() =
        runTest {
            val connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected)
            val (viewModel, _, _) =
                viewModel(
                    connectionState = connectionState,
                    rememberedDevice = SENSOR_1,
                    gpsReadyToRecord = MutableStateFlow(false),
                )

            viewModel.uiState.test {
                assertEquals(CaptureUiState.Scanning(), awaitItem())
                assertEquals(CaptureUiState.Connecting(SENSOR_1.name), awaitItem())
                connectionState.value = BleConnectionState.Connected
                assertEquals(CaptureUiState.Ready(gpsReadyToRecord = false), awaitItem())

                viewModel.onStartRecording(surveySweepId = "SWEEP-1")
                expectNoEvents()
            }
        }

    @Test
    fun `connecting to a device saves it so it is remembered next time`() =
        runTest {
            val connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected)
            val preferences = mockk<LuxDevicePreferences>()
            coEvery { preferences.lastDevice() } returns null
            coEvery { preferences.saveLastDevice(any()) } just Runs
            val luxClient = mockk<LuxSensorBleClient>()
            every { luxClient.connectionState } returns connectionState
            every { luxClient.connect(any()) } just Runs
            val scanner = mockk<LuxDeviceScanner>()
            every { scanner.scan() } returns flowOf(SENSOR_1)
            val controller = mockk<SurveyCaptureController>(relaxed = true)
            every { controller.gpsSignalState } returns MutableStateFlow(GpsSignalState.Ok)
            every { controller.recordingStartResult } returns MutableStateFlow(null)
            val storageMonitor = mockk<StorageMonitor>()
            every { storageMonitor.freeBytes() } returns 0L
            val preRecordGpsAccuracyTracker = mockk<PreRecordGpsAccuracyTracker>(relaxed = true)
            every { preRecordGpsAccuracyTracker.readyToRecord } returns MutableStateFlow(true)
            val viewModel =
                CaptureViewModel(
                    luxClient,
                    scanner,
                    preferences,
                    controller,
                    storageMonitor,
                    preRecordGpsAccuracyTracker,
                )

            viewModel.uiState.test {
                awaitItem() // Scanning()
                awaitItem() // Scanning([SENSOR_1])
                viewModel.onDeviceSelected(SENSOR_1)
                awaitItem() // Connecting
                connectionState.value = BleConnectionState.Connected
                awaitItem() // Ready
            }
            coVerify { preferences.saveLastDevice(SENSOR_1) }
        }

    @Test
    fun `an empty scan times out, and retrying scans again`() =
        runTest {
            val (viewModel, _, _) = viewModel(rememberedDevice = null, scanResults = emptyList())

            viewModel.uiState.test {
                assertEquals(CaptureUiState.Scanning(), awaitItem())
                assertEquals(CaptureUiState.ScanTimedOut, awaitItem())

                viewModel.onRetryScan()
                assertEquals(CaptureUiState.Scanning(), awaitItem())
            }
        }

    @Test
    fun `changing device disconnects the current one and starts a fresh scan`() =
        runTest {
            val connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Connected)
            val (viewModel, luxClient, _) =
                viewModel(
                    connectionState = connectionState,
                    rememberedDevice = SENSOR_1,
                    scanResults = listOf(SENSOR_2),
                )

            viewModel.uiState.test {
                awaitItem() // Scanning() field default, before init checks for a remembered device
                awaitItem() // Connecting to the remembered device
                assertEquals(CaptureUiState.Ready(gpsReadyToRecord = true), awaitItem())

                viewModel.onChangeDevice()
                assertEquals(CaptureUiState.Scanning(), awaitItem())
                assertEquals(CaptureUiState.Scanning(listOf(SENSOR_2)), awaitItem())
            }
            verify { luxClient.disconnect() }
        }

    @Test
    fun `pressing start moves to StartingRecording, and the preview becoming ready starts the session`() =
        runTest {
            val connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Connected)
            val recordingStartResult = MutableStateFlow<RecordingStartResult?>(null)
            val (viewModel, _, controller) =
                viewModel(
                    connectionState = connectionState,
                    rememberedDevice = SENSOR_1,
                    recordingStartResult = recordingStartResult,
                )
            val surface = mockk<Surface>()

            viewModel.uiState.test {
                awaitItem() // Scanning() field default
                awaitItem() // Connecting
                assertEquals(CaptureUiState.Ready(gpsReadyToRecord = true), awaitItem())

                viewModel.onStartRecording(surveySweepId = "SWEEP-1")
                assertEquals(CaptureUiState.StartingRecording, awaitItem())

                viewModel.onPreviewSurfaceReady(surface)
                recordingStartResult.value = RecordingStartResult.Ready
                val recording = awaitItem() as CaptureUiState.Recording
                assertEquals(false, recording.gpsSignalLost)
            }
            verify { controller.startSession(any(), "SWEEP-1", SENSOR_1.address, surface) }
        }

    @Test
    fun `a failed camera start surfaces PackagingFailed`() =
        runTest {
            val connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Connected)
            val recordingStartResult = MutableStateFlow<RecordingStartResult?>(null)
            val (viewModel, _, _) =
                viewModel(
                    connectionState = connectionState,
                    rememberedDevice = SENSOR_1,
                    recordingStartResult = recordingStartResult,
                )
            val surface = mockk<Surface>()

            viewModel.uiState.test {
                awaitItem() // Scanning() field default
                awaitItem() // Connecting
                awaitItem() // Ready
                viewModel.onStartRecording(surveySweepId = "SWEEP-1")
                awaitItem() // StartingRecording
                viewModel.onPreviewSurfaceReady(surface)
                recordingStartResult.value = RecordingStartResult.Failed("camera busy")
                val failed = awaitItem() as CaptureUiState.PackagingFailed
                assertEquals("camera busy", failed.reason)
            }
        }

    @Test
    fun `stopping a recording moves through Packaging to Packaged on success`() =
        runTest {
            val connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Connected)
            val recordingStartResult = MutableStateFlow<RecordingStartResult?>(null)
            val (viewModel, _, controller) =
                viewModel(
                    connectionState = connectionState,
                    rememberedDevice = SENSOR_1,
                    recordingStartResult = recordingStartResult,
                )
            every { controller.stopSession() } returns flowOf(PackageResult.Success("/data/manifest.json"))

            viewModel.uiState.test {
                awaitItem() // Scanning() field default
                awaitItem() // Connecting
                awaitItem() // Ready
                viewModel.onStartRecording(surveySweepId = "SWEEP-1")
                awaitItem() // StartingRecording
                viewModel.onPreviewSurfaceReady(mockk())
                recordingStartResult.value = RecordingStartResult.Ready
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
            val recordingStartResult = MutableStateFlow<RecordingStartResult?>(null)
            val (viewModel, _, controller) =
                viewModel(
                    connectionState = connectionState,
                    rememberedDevice = SENSOR_1,
                    recordingStartResult = recordingStartResult,
                )
            every { controller.stopSession() } returns flowOf(PackageResult.Failure("disk full"))

            viewModel.uiState.test {
                awaitItem() // Scanning() field default
                awaitItem() // Connecting
                awaitItem() // Ready
                viewModel.onStartRecording(surveySweepId = "SWEEP-1")
                awaitItem() // StartingRecording
                viewModel.onPreviewSurfaceReady(mockk())
                recordingStartResult.value = RecordingStartResult.Ready
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
            val gpsSignalState = MutableStateFlow<GpsSignalState>(GpsSignalState.Ok)
            val recordingStartResult = MutableStateFlow<RecordingStartResult?>(null)
            val (viewModel, _, _) =
                viewModel(
                    connectionState = connectionState,
                    rememberedDevice = SENSOR_1,
                    gpsSignalState = gpsSignalState,
                    recordingStartResult = recordingStartResult,
                )

            viewModel.uiState.test {
                awaitItem() // Scanning() field default
                awaitItem() // Connecting
                awaitItem() // Ready
                viewModel.onStartRecording(surveySweepId = "SWEEP-1")
                awaitItem() // StartingRecording
                viewModel.onPreviewSurfaceReady(mockk())
                recordingStartResult.value = RecordingStartResult.Ready
                val recording = awaitItem() as CaptureUiState.Recording
                assertEquals(false, recording.gpsSignalLost)

                gpsSignalState.value = GpsSignalState.Lost

                val warned = awaitItem() as CaptureUiState.Recording
                assertEquals(true, warned.gpsSignalLost)
            }
        }

    // Drives the ViewModel from Ready through StartingRecording into Recording, the same
    // sequence the already-passing "pressing start..." test above uses - onStartRecording()
    // only works from Ready, and recordingStartResult must change value (not just already equal
    // Ready from the start) for its StateFlow collector to fire while the state is
    // StartingRecording. Returns once Recording is reached and its 4 new collectors are running.
    //
    // Deliberately uses runCurrent(), never advanceUntilIdle(), from this point on: Recording
    // starts a duration ticker that loops on delay(1_000) forever, so advanceUntilIdle() would
    // never find the scheduler idle and hang the test.
    private fun TestScope.startRecordingAndReachRecordingState(
        viewModel: CaptureViewModel,
        recordingStartResult: MutableStateFlow<RecordingStartResult?>,
    ) {
        dispatcher.scheduler.advanceUntilIdle() // let the remembered device connect, reaching Ready
        viewModel.onStartRecording("SWEEP-1")
        viewModel.onPreviewSurfaceReady(mockk())
        recordingStartResult.value = RecordingStartResult.Ready
        dispatcher.scheduler.runCurrent()
    }

    @Test
    fun `recording state picks up the latest lux sample`() =
        runTest {
            val samples = MutableSharedFlow<LuxSample>(extraBufferCapacity = 1)
            val connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Connected)
            val recordingStartResult = MutableStateFlow<RecordingStartResult?>(null)
            val (viewModel, luxClient, _) =
                viewModel(
                    connectionState = connectionState,
                    rememberedDevice = SENSOR_1,
                    recordingStartResult = recordingStartResult,
                )
            every { luxClient.samples } returns samples
            startRecordingAndReachRecordingState(viewModel, recordingStartResult)

            samples.emit(LuxSample(sampleNo = 0, moduleEpoch = 0, moduleMs = 0, phoneElapsedNs = 0, lux = 42.5f))
            dispatcher.scheduler.runCurrent()

            val state = viewModel.uiState.value
            assertTrue(state is CaptureUiState.Recording)
            assertEquals(42.5f, (state as CaptureUiState.Recording).latestLuxValue)
        }

    @Test
    fun `recording state picks up live gps accuracy and distance`() =
        runTest {
            val liveGpsPoint = MutableStateFlow<TrackPoint?>(null)
            val distanceMeters = MutableStateFlow(0f)
            val connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Connected)
            val recordingStartResult = MutableStateFlow<RecordingStartResult?>(null)
            val (viewModel, _, controller) =
                viewModel(
                    connectionState = connectionState,
                    rememberedDevice = SENSOR_1,
                    recordingStartResult = recordingStartResult,
                )
            every { controller.liveGpsPoint } returns liveGpsPoint
            every { controller.distanceMeters } returns distanceMeters
            startRecordingAndReachRecordingState(viewModel, recordingStartResult)

            liveGpsPoint.value = TrackPoint(0L, 10.0, 106.0, accuracyM = 7.5f, gpsBearingDeg = 90f, speedMps = null)
            distanceMeters.value = 123f
            dispatcher.scheduler.runCurrent()

            val state = viewModel.uiState.value as CaptureUiState.Recording
            assertEquals(7.5f, state.gpsAccuracyMeters)
            assertEquals(90f, state.headingDeg)
            assertEquals(123f, state.distanceMeters)
        }

    @Test
    fun `recording state duration advances with each tick`() =
        runTest {
            val connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Connected)
            val recordingStartResult = MutableStateFlow<RecordingStartResult?>(null)
            val (viewModel, _, _) =
                viewModel(
                    connectionState = connectionState,
                    rememberedDevice = SENSOR_1,
                    recordingStartResult = recordingStartResult,
                )
            startRecordingAndReachRecordingState(viewModel, recordingStartResult)

            dispatcher.scheduler.advanceTimeBy(3_000)
            dispatcher.scheduler.runCurrent()

            val state = viewModel.uiState.value as CaptureUiState.Recording
            assertEquals(3L, state.durationSeconds)
        }

    @Test
    fun `recording state reports free storage from the storage monitor`() =
        runTest {
            val storageMonitor = mockk<StorageMonitor>()
            every { storageMonitor.freeBytes() } returns 2_000_000_000L
            val connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Connected)
            val recordingStartResult = MutableStateFlow<RecordingStartResult?>(null)
            val (viewModel, _, _) =
                viewModel(
                    connectionState = connectionState,
                    rememberedDevice = SENSOR_1,
                    recordingStartResult = recordingStartResult,
                    storageMonitor = storageMonitor,
                )
            startRecordingAndReachRecordingState(viewModel, recordingStartResult)

            val state = viewModel.uiState.value as CaptureUiState.Recording
            assertEquals(2_000_000_000L, state.freeStorageBytes)
        }

    @Test
    fun `warning pulse fires once when gps signal is first lost`() =
        runTest {
            val gpsSignalState = MutableStateFlow<GpsSignalState>(GpsSignalState.Ok)
            val connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Connected)
            val recordingStartResult = MutableStateFlow<RecordingStartResult?>(null)
            val (viewModel, _, controller) =
                viewModel(
                    connectionState = connectionState,
                    rememberedDevice = SENSOR_1,
                    recordingStartResult = recordingStartResult,
                )
            every { controller.gpsSignalState } returns gpsSignalState
            startRecordingAndReachRecordingState(viewModel, recordingStartResult)

            viewModel.warningPulses.test {
                gpsSignalState.value = GpsSignalState.Lost
                dispatcher.scheduler.runCurrent()
                assertEquals(Unit, awaitItem())
            }
        }

    // Seeding gpsSignalState at Lost from the start (like the test above seeds Ok) would be wrong
    // here: a StateFlow's very first collected value arrives before Recording is reached, so the
    // if (current is Recording) guard skips it, and the Recording seed branch in Task 10's code
    // (RecordingStartResult.Ready) reads gpsSignalState.value directly - it does not go through
    // the collector this task changed, so it never pulses either. warningPulses would then never
    // emit anything at all, and awaitItem() would hang forever instead of actually checking
    // anything. So this test must start at Ok and drive one real Ok -> Lost transition itself,
    // the same way the test above does, before checking that a second write of the same value
    // does not add a second pulse.
    @Test
    fun `warning pulse does not repeat while gps signal stays lost`() =
        runTest {
            val gpsSignalState = MutableStateFlow<GpsSignalState>(GpsSignalState.Ok)
            val connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Connected)
            val recordingStartResult = MutableStateFlow<RecordingStartResult?>(null)
            val (viewModel, _, controller) =
                viewModel(
                    connectionState = connectionState,
                    rememberedDevice = SENSOR_1,
                    recordingStartResult = recordingStartResult,
                )
            every { controller.gpsSignalState } returns gpsSignalState
            startRecordingAndReachRecordingState(viewModel, recordingStartResult)

            viewModel.warningPulses.test {
                gpsSignalState.value = GpsSignalState.Lost // real transition - exactly one pulse expected
                dispatcher.scheduler.runCurrent()
                awaitItem()

                // Same value written again - a StateFlow conflates equal values and never
                // re-delivers them to collect, so this never even reaches the pulse guard in
                // CaptureViewModel. Checking it anyway matches what a caller of warningPulses
                // actually observes: no second pulse while the signal stays lost.
                gpsSignalState.value = GpsSignalState.Lost
                dispatcher.scheduler.runCurrent()
                expectNoEvents()
            }
        }
}
