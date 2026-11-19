package com.luxmap.feature.workorder.polegps.ui.measure

import androidx.lifecycle.SavedStateHandle
import com.luxmap.core.camera.PhotoGpsExif
import com.luxmap.feature.workorder.polegps.data.GpsClock
import com.luxmap.feature.workorder.polegps.data.GpsFix
import com.luxmap.feature.workorder.polegps.data.GpsFixSource
import com.luxmap.feature.workorder.polegps.data.PoleGpsRepository
import com.luxmap.feature.workorder.polegps.data.PoleReference
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class PoleGpsMeasureViewModelTest {
    private val repository = mockk<PoleGpsRepository>(relaxed = true)
    private val exif = mockk<PhotoGpsExif>(relaxed = true)
    private var fixesFlow: Flow<GpsFix> = flowOf(GpsFix(10.1, 106.1, 4.0, Instant.ofEpochSecond(1), 1_000_000_000L))
    private var nowNanos = 1_000_000_000L
    private val clock =
        object : GpsClock() {
            override fun elapsedRealtimeNanos(): Long = nowNanos
        }
    private val source =
        object : GpsFixSource {
            override fun fixes(): Flow<GpsFix> = fixesFlow
        }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        coEvery { repository.poleReference("WO-1", "P-1") } returns PoleReference("P-1", 3, 10.0, 106.0)
        every { exif.read(any()) } returns null
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() =
        PoleGpsMeasureViewModel(
            SavedStateHandle(mapOf("workOrderId" to "WO-1", "poleId" to "P-1")),
            repository,
            source,
            exif,
            clock,
        )

    @Test
    fun `starts in Loading then shows the pole label and previous position once permission is granted`() =
        runTest {
            val vm = viewModel()
            vm.onPermissionGranted()
            val state = vm.state.value as PoleGpsMeasureUiState.Success
            assertEquals("Cột 3 (P-1)", state.poleLabel)
            assertEquals(3, state.previous?.position)
        }

    @Test
    fun `a pole outside the order is Empty`() =
        runTest {
            coEvery { repository.poleReference("WO-1", "P-1") } returns null
            val vm = viewModel()
            vm.onPermissionGranted()
            assertEquals(PoleGpsMeasureUiState.Empty, vm.state.value)
        }

    @Test
    fun `denied permission is an Error that explains the cause`() =
        runTest {
            val vm = viewModel()
            vm.onPermissionDenied()
            assertTrue((vm.state.value as PoleGpsMeasureUiState.Error).message.contains("quyền vị trí"))
        }

    @Test
    fun `onPhotoCaptured with location permission saves the fix from the gps source`() =
        runTest {
            fixesFlow = flowOf(GpsFix(10.5, 106.5, 7.0, Instant.ofEpochSecond(42), 1_000_000_000L))
            val vm = viewModel()
            val file = File("p1.jpg")
            var done = false
            vm.onPhotoCaptured(file, hasLocationPermission = true) { done = true }
            coVerify {
                repository.saveCapturedPhoto(
                    "WO-1",
                    "P-1",
                    file.absolutePath,
                    10.5,
                    106.5,
                    7.0,
                    Instant.ofEpochSecond(42),
                )
            }
            assertTrue(done)
        }

    @Test
    fun `onPhotoCaptured without location permission saves nothing and flags the missing location`() =
        runTest {
            val vm = viewModel()
            var done = false
            vm.onPhotoCaptured(File("p1.jpg"), hasLocationPermission = false) { done = true }
            coVerify(exactly = 0) { repository.saveCapturedPhoto(any(), any(), any(), any(), any(), any(), any()) }
            assertTrue(vm.locationMissing.value)
            assertFalse(done)
        }

    @Test
    fun `onPhotoCaptured saves nothing and flags the missing location when no gps fix ever arrives`() =
        runTest {
            fixesFlow = flow { awaitCancellation() }
            val vm = viewModel()
            var done = false
            vm.onPhotoCaptured(File("p1.jpg"), hasLocationPermission = true) { done = true }
            advanceTimeBy(PoleGpsMeasureViewModel.FIX_TIMEOUT_MILLIS + 1)
            coVerify(exactly = 0) { repository.saveCapturedPhoto(any(), any(), any(), any(), any(), any(), any()) }
            assertTrue(vm.locationMissing.value)
            assertFalse(done)
        }

    @Test
    fun `retrying with location permission after a missing fix saves the real fix and clears the flag`() =
        runTest {
            fixesFlow = flowOf(GpsFix(10.5, 106.5, 7.0, Instant.ofEpochSecond(42), 1_000_000_000L))
            val vm = viewModel()
            val file = File("p1.jpg")
            var done = false
            vm.onPhotoCaptured(file, hasLocationPermission = false) {}
            vm.onPhotoCaptured(file, hasLocationPermission = true) { done = true }
            coVerify {
                repository.saveCapturedPhoto(
                    "WO-1",
                    "P-1",
                    file.absolutePath,
                    10.5,
                    106.5,
                    7.0,
                    Instant.ofEpochSecond(42),
                )
            }
            assertFalse(vm.locationMissing.value)
            assertTrue(done)
        }

    @Test
    fun `startWatchingLocation keeps currentFix updated with the latest gps fix`() =
        runTest {
            fixesFlow = flowOf(GpsFix(11.0, 107.0, 5.0, Instant.ofEpochSecond(99), 1_000_000_000L))
            val vm = viewModel()
            vm.startWatchingLocation()
            assertEquals(GpsFix(11.0, 107.0, 5.0, Instant.ofEpochSecond(99), 1_000_000_000L), vm.currentFix.value)
        }

    @Test
    fun `onPhotoCaptured reuses an already-known fix without subscribing to the gps source again`() =
        runTest {
            var subscriptions = 0
            val trackedSource =
                object : GpsFixSource {
                    override fun fixes(): Flow<GpsFix> =
                        flow {
                            subscriptions++
                            emit(GpsFix(12.0, 108.0, 6.0, Instant.ofEpochSecond(7), 1_000_000_000L))
                            awaitCancellation()
                        }
                }
            val vm =
                PoleGpsMeasureViewModel(
                    SavedStateHandle(mapOf("workOrderId" to "WO-1", "poleId" to "P-1")),
                    repository,
                    trackedSource,
                    exif,
                    clock,
                )
            vm.startWatchingLocation()
            assertEquals(GpsFix(12.0, 108.0, 6.0, Instant.ofEpochSecond(7), 1_000_000_000L), vm.currentFix.value)

            val file = File("p1.jpg")
            var done = false
            vm.onPhotoCaptured(file, hasLocationPermission = true) { done = true }

            assertEquals(1, subscriptions)
            coVerify {
                repository.saveCapturedPhoto(
                    "WO-1",
                    "P-1",
                    file.absolutePath,
                    12.0,
                    108.0,
                    6.0,
                    Instant.ofEpochSecond(7),
                )
            }
            assertTrue(done)
        }

    @Test
    fun `onPhotoCaptured writes the fix into EXIF then saves the value read back from EXIF`() =
        runTest {
            fixesFlow = flowOf(GpsFix(10.5, 106.5, 7.0, Instant.ofEpochSecond(42), 1_000_000_000L))
            val vm = viewModel()
            val file = File("p1.jpg")
            every { exif.read(file) } returns (11.1 to 107.1)

            vm.onPhotoCaptured(file, hasLocationPermission = true) {}

            verify { exif.write(file, 10.5, 106.5) }
            coVerify {
                repository.saveCapturedPhoto(
                    "WO-1",
                    "P-1",
                    file.absolutePath,
                    11.1,
                    107.1,
                    7.0,
                    Instant.ofEpochSecond(42),
                )
            }
        }

    @Test
    fun `without a gps fix, EXIF is never touched`() =
        runTest {
            val vm = viewModel()
            val file = File("p1.jpg")

            vm.onPhotoCaptured(file, hasLocationPermission = false) {}

            verify(exactly = 0) { exif.write(any(), any(), any()) }
        }

    @Test
    fun `low accuracy requires confirmation and saves exact candidate once`() =
        runTest {
            fixesFlow = flowOf(GpsFix(10.5, 106.5, 35.0, Instant.ofEpochSecond(42), 1_000_000_000L))
            val vm = viewModel()
            val file = File("poor.jpg")
            vm.onPhotoCaptured(file, true) {}
            assertEquals(35.0, vm.pendingConfirmation.value?.accuracyM)
            verify(exactly = 0) { exif.write(any(), any(), any()) }
            vm.confirmLowAccuracy()
            vm.confirmLowAccuracy()
            coVerify(exactly = 1) {
                repository.saveCapturedPhoto(
                    "WO-1",
                    "P-1",
                    file.absolutePath,
                    10.5,
                    106.5,
                    35.0,
                    Instant.ofEpochSecond(42),
                )
            }
        }

    @Test
    fun `expired confirmation never writes stale GPS`() =
        runTest {
            fixesFlow = flowOf(GpsFix(10.5, 106.5, 35.0, Instant.ofEpochSecond(42), 1_000_000_000L))
            val vm = viewModel()
            vm.onPhotoCaptured(File("poor.jpg"), true) {}
            nowNanos = 11_000_000_001L
            vm.confirmLowAccuracy()
            verify(exactly = 0) { exif.write(any(), any(), any()) }
            coVerify(exactly = 0) { repository.saveCapturedPhoto(any(), any(), any(), any(), any(), any(), any()) }
            assertTrue(vm.locationMissing.value)
        }

    @Test
    fun `stop cancels observer and repeated start has one subscription`() =
        runTest {
            var active = 0
            fixesFlow =
                flow {
                    active++
                    try {
                        awaitCancellation()
                    } finally {
                        active--
                    }
                }
            val vm = viewModel()
            vm.startWatchingLocation()
            vm.startWatchingLocation()
            assertEquals(1, active)
            vm.stopWatchingLocation()
            assertEquals(0, active)
        }

    @Test
    fun `stale sample times out without touching EXIF or repository`() =
        runTest {
            nowNanos = 11_000_000_001L
            val vm = viewModel()
            vm.onPhotoCaptured(File("stale.jpg"), true) {}
            advanceTimeBy(PoleGpsMeasureViewModel.FIX_TIMEOUT_MILLIS + 1)
            assertTrue(vm.locationMissing.value)
            verify(exactly = 0) { exif.write(any(), any(), any()) }
            coVerify(exactly = 0) { repository.saveCapturedPhoto(any(), any(), any(), any(), any(), any(), any()) }
            vm.stopWatchingLocation()
        }

    @Test
    fun `subscription failure is recoverable without fake coordinates`() =
        runTest {
            fixesFlow = flow { throw SecurityException("revoked") }
            val vm = viewModel()
            vm.startWatchingLocation()
            assertTrue(vm.locationMissing.value)
            vm.onPhotoCaptured(File("denied.jpg"), false) {}
            verify(exactly = 0) { exif.write(any(), any(), any()) }
        }

    @Test
    fun `confirmation keeps its candidate even when observer receives a newer fix`() =
        runTest {
            val updates = MutableSharedFlow<GpsFix>(replay = 1)
            updates.emit(GpsFix(10.5, 106.5, 35.0, Instant.ofEpochSecond(42), 1_000_000_000L))
            fixesFlow = updates
            val vm = viewModel()
            val file = File("original.jpg")
            vm.onPhotoCaptured(file, true) {}
            updates.emit(GpsFix(11.5, 107.5, 5.0, Instant.ofEpochSecond(43), 1_000_000_000L))
            vm.confirmLowAccuracy()
            verify { exif.write(file, 10.5, 106.5) }
            coVerify(exactly = 1) {
                repository.saveCapturedPhoto(
                    "WO-1",
                    "P-1",
                    file.absolutePath,
                    10.5,
                    106.5,
                    35.0,
                    Instant.ofEpochSecond(42),
                )
            }
            vm.stopWatchingLocation()
        }

    @Test
    fun `stale sample is ignored while waiting for a fresh real timestamp`() =
        runTest {
            nowNanos = 12_000_000_000L
            val updates = MutableSharedFlow<GpsFix>(replay = 1)
            updates.emit(GpsFix(10.5, 106.5, 7.0, Instant.ofEpochSecond(42), 1_000_000_000L))
            fixesFlow = updates
            val vm = viewModel()
            val file = File("fresh.jpg")
            vm.onPhotoCaptured(file, true) {}
            verify(exactly = 0) { exif.write(any(), any(), any()) }
            updates.emit(GpsFix(11.5, 107.5, 5.0, Instant.ofEpochSecond(2), 12_000_000_000L))
            coVerify(exactly = 1) {
                repository.saveCapturedPhoto(
                    "WO-1",
                    "P-1",
                    file.absolutePath,
                    11.5,
                    107.5,
                    5.0,
                    Instant.ofEpochSecond(2),
                )
            }
            vm.stopWatchingLocation()
        }

    @Test
    fun `invalid sample cannot write EXIF or Room`() =
        runTest {
            fixesFlow = flowOf(GpsFix(Double.NaN, 106.5, -1.0, Instant.ofEpochSecond(42), 1_000_000_000L))
            val vm = viewModel()
            vm.onPhotoCaptured(File("invalid.jpg"), true) {}
            advanceTimeBy(PoleGpsMeasureViewModel.FIX_TIMEOUT_MILLIS + 1)
            verify(exactly = 0) { exif.write(any(), any(), any()) }
            coVerify(exactly = 0) { repository.saveCapturedPhoto(any(), any(), any(), any(), any(), any(), any()) }
            assertTrue(vm.locationMissing.value)
            vm.stopWatchingLocation()
        }

    @Test
    fun `saving failure releases loading so same photo can be retried`() =
        runTest {
            coEvery { repository.saveCapturedPhoto(any(), any(), any(), any(), any(), any(), any()) } throws
                IllegalStateException("disk unavailable")
            val vm = viewModel()
            val file = File("retry.jpg")
            var done = false
            vm.onPhotoCaptured(file, true) { done = true }
            assertFalse(vm.isSaving.value)
            assertTrue(vm.locationMissing.value)
            assertFalse(done)
            coEvery { repository.saveCapturedPhoto(any(), any(), any(), any(), any(), any(), any()) } returns Unit
            vm.onPhotoCaptured(file, true) { done = true }
            assertTrue(done)
        }
}
