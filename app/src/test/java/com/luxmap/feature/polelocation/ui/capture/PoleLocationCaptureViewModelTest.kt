package com.luxmap.feature.polelocation.ui.capture

import androidx.lifecycle.SavedStateHandle
import com.luxmap.feature.polelocation.data.PoleLocationExif
import com.luxmap.feature.polelocation.data.PoleLocationRepository
import com.luxmap.feature.workorder.polegps.data.GpsFix
import com.luxmap.feature.workorder.polegps.data.GpsFixSource
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.File
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class PoleLocationCaptureViewModelTest {
    private val repository = mockk<PoleLocationRepository>(relaxed = true)
    private val exif = mockk<PoleLocationExif>(relaxed = true)
    private var fixesFlow: Flow<GpsFix> = flowOf(GpsFix(10.0, 106.0, 4.0, Instant.ofEpochSecond(1)))
    private val fixSource =
        object : GpsFixSource {
            override fun fixes(): Flow<GpsFix> = fixesFlow
        }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() =
        PoleLocationCaptureViewModel(SavedStateHandle(mapOf("poleId" to "POLE-1")), repository, fixSource, exif)

    @Test
    fun `startWatchingLocation keeps currentFix updated`() =
        runTest {
            val vm = viewModel()
            vm.startWatchingLocation()
            assertEquals(GpsFix(10.0, 106.0, 4.0, Instant.ofEpochSecond(1)), vm.currentFix.value)
        }

    @Test
    fun `onPhotoCaptured without location permission reports an error and never submits`() =
        runTest {
            val vm = viewModel()
            val file = File("p1.jpg")
            var done = false
            vm.onPhotoCaptured(file, hasLocationPermission = false) { done = true }

            assertEquals(false, done)
            assertEquals(true, vm.errorMessage.value != null)
            coVerify(exactly = 0) { repository.submit(any(), any(), any(), any(), any()) }
        }

    @Test
    fun `onPhotoCaptured gives up and reports an error when no gps fix ever arrives`() =
        runTest {
            fixesFlow = flow { awaitCancellation() }
            val vm = viewModel()
            val file = File("p1.jpg")
            var done = false
            vm.onPhotoCaptured(file, hasLocationPermission = true) { done = true }
            advanceTimeBy(PoleLocationCaptureViewModel.FIX_TIMEOUT_MILLIS + 1)

            assertEquals(false, done)
            assertEquals(true, vm.errorMessage.value != null)
            coVerify(exactly = 0) { repository.submit(any(), any(), any(), any(), any()) }
        }

    @Test
    fun `onPhotoCaptured writes the fix into EXIF then submits the value read back from EXIF`() =
        runTest {
            val vm = viewModel()
            val file = File("p1.jpg")
            every { exif.read(file) } returns (11.1 to 107.1)
            var done = false

            vm.onPhotoCaptured(file, hasLocationPermission = true) { done = true }

            verify { exif.write(file, 10.0, 106.0) }
            coVerify {
                repository.submit("POLE-1", file.absolutePath, 11.1, 107.1, Instant.ofEpochSecond(1))
            }
            assertEquals(true, done)
            assertNull(vm.errorMessage.value)
        }

    @Test
    fun `when EXIF cannot be read back, falls back to the measured fix`() =
        runTest {
            val vm = viewModel()
            val file = File("p1.jpg")
            every { exif.read(file) } returns null

            vm.onPhotoCaptured(file, hasLocationPermission = true) {}

            coVerify { repository.submit("POLE-1", file.absolutePath, 10.0, 106.0, Instant.ofEpochSecond(1)) }
        }
}
