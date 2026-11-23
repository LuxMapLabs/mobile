package com.luxmap.feature.survey.ui.polephoto

import androidx.lifecycle.SavedStateHandle
import com.luxmap.core.location.LocationTracker
import com.luxmap.feature.survey.data.LuxReadingRepository
import com.luxmap.feature.survey.data.PolePhotoRepository
import com.luxmap.feature.survey.data.entity.LocalPolePhotoEntity
import com.luxmap.feature.survey.ui.polephoto.lux.LuxCardUiState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.maplibre.android.geometry.LatLng
import java.io.File
import java.time.Instant

private const val WORK_ORDER_ID = "WO-1"
private const val POLE_ID = "POLE-1"

private fun savedStateHandle() =
    SavedStateHandle(
        mapOf(
            PolePhotoCaptureViewModel.WORK_ORDER_ID_ARG to WORK_ORDER_ID,
            PolePhotoCaptureViewModel.POLE_ID_ARG to POLE_ID,
        ),
    )

@OptIn(ExperimentalCoroutinesApi::class)
class PolePhotoCaptureViewModelTest {
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
    fun `onPhotoCaptured sends the real GPS fix to the repository and calls onDone`() =
        runTest {
            val repository = mockk<PolePhotoRepository>(relaxed = true)
            val locationTracker = mockk<LocationTracker>()
            coEvery { locationTracker.getCurrentLocation() } returns LatLng(10.0, 106.0)
            val vm = PolePhotoCaptureViewModel(savedStateHandle(), repository, locationTracker, mockk(relaxed = true))
            val file = File("x.jpg")
            var done = false

            vm.onPhotoCaptured(file) { done = true }
            dispatcher.scheduler.advanceUntilIdle()

            coVerify {
                repository.capture(
                    workOrderId = WORK_ORDER_ID,
                    poleId = POLE_ID,
                    filePath = file.absolutePath,
                    lat = 10.0,
                    lng = 106.0,
                )
            }
            assertTrue(done)
        }

    @Test
    fun `onPhotoCaptured does not save fake coordinates when the GPS fix is unavailable`() =
        runTest {
            val repository = mockk<PolePhotoRepository>(relaxed = true)
            val locationTracker = mockk<LocationTracker>()
            coEvery { locationTracker.getCurrentLocation() } returns null
            val vm = PolePhotoCaptureViewModel(savedStateHandle(), repository, locationTracker, mockk(relaxed = true))
            var done = false

            vm.onPhotoCaptured(File("x.jpg")) { done = true }
            dispatcher.scheduler.advanceUntilIdle()

            coVerify(exactly = 0) {
                repository.capture(workOrderId = any(), poleId = any(), filePath = any(), lat = any(), lng = any())
            }
            assertTrue(vm.locationMissing.value)
            assertFalse(done)
        }

    @Test
    fun `retrying after a missing GPS fix saves the real location and clears the flag`() =
        runTest {
            val repository = mockk<PolePhotoRepository>(relaxed = true)
            val locationTracker = mockk<LocationTracker>()
            coEvery { locationTracker.getCurrentLocation() } returnsMany listOf(null, LatLng(10.0, 106.0))
            val vm = PolePhotoCaptureViewModel(savedStateHandle(), repository, locationTracker, mockk(relaxed = true))
            val file = File("x.jpg")
            var done = false

            vm.onPhotoCaptured(file) {}
            dispatcher.scheduler.advanceUntilIdle()
            vm.onPhotoCaptured(file) { done = true }
            dispatcher.scheduler.advanceUntilIdle()

            coVerify {
                repository.capture(
                    workOrderId = WORK_ORDER_ID,
                    poleId = POLE_ID,
                    filePath = file.absolutePath,
                    lat = 10.0,
                    lng = 106.0,
                )
            }
            assertFalse(vm.locationMissing.value)
            assertTrue(done)
        }

    @Test
    fun `onPhotoCaptured still calls onDone when the repository write throws`() =
        runTest {
            val repository = mockk<PolePhotoRepository>()
            coEvery {
                repository.capture(workOrderId = any(), poleId = any(), filePath = any(), lat = any(), lng = any())
            } throws RuntimeException("disk full")
            val locationTracker = mockk<LocationTracker>()
            coEvery { locationTracker.getCurrentLocation() } returns LatLng(10.0, 106.0)
            val vm = PolePhotoCaptureViewModel(savedStateHandle(), repository, locationTracker, mockk(relaxed = true))
            var done = false

            vm.onPhotoCaptured(File("x.jpg")) { done = true }
            dispatcher.scheduler.advanceUntilIdle()

            assertTrue(done)
        }

    private val luxReading = LuxCardUiState.Recorded(12.3, Instant.parse("2026-11-24T12:00:00Z"))

    private fun savedMedia() =
        LocalPolePhotoEntity(
            photoId = "PHOTO-9",
            workOrderId = WORK_ORDER_ID,
            poleId = POLE_ID,
            filePath = "x.jpg",
            capturedAt = Instant.EPOCH,
            lat = 10.0,
            lng = 106.0,
        )

    @Test
    fun `onPhotoCaptured records the lux reading against the saved media`() =
        runTest {
            val repository = mockk<PolePhotoRepository>()
            coEvery {
                repository.capture(workOrderId = any(), poleId = any(), filePath = any(), lat = any(), lng = any())
            } returns savedMedia()
            val luxRepository = mockk<LuxReadingRepository>(relaxed = true)
            val locationTracker = mockk<LocationTracker>()
            coEvery { locationTracker.getCurrentLocation() } returns LatLng(10.0, 106.0)
            val vm = PolePhotoCaptureViewModel(savedStateHandle(), repository, locationTracker, luxRepository)

            vm.onPhotoCaptured(File("x.jpg"), luxReading) {}
            dispatcher.scheduler.advanceUntilIdle()

            coVerify { luxRepository.record(WORK_ORDER_ID, POLE_ID, "PHOTO-9", 12.3, luxReading.measuredAt) }
        }

    @Test
    fun `onPhotoCaptured records no lux when none was measured`() =
        runTest {
            val repository = mockk<PolePhotoRepository>()
            coEvery {
                repository.capture(workOrderId = any(), poleId = any(), filePath = any(), lat = any(), lng = any())
            } returns savedMedia()
            val luxRepository = mockk<LuxReadingRepository>(relaxed = true)
            val locationTracker = mockk<LocationTracker>()
            coEvery { locationTracker.getCurrentLocation() } returns LatLng(10.0, 106.0)
            val vm = PolePhotoCaptureViewModel(savedStateHandle(), repository, locationTracker, luxRepository)

            vm.onPhotoCaptured(File("x.jpg")) {}
            dispatcher.scheduler.advanceUntilIdle()

            coVerify(exactly = 0) { luxRepository.record(any(), any(), any(), any(), any()) }
        }

    @Test
    fun `onPhotoCaptured records no lux when saving the media fails`() =
        runTest {
            val repository = mockk<PolePhotoRepository>()
            coEvery {
                repository.capture(workOrderId = any(), poleId = any(), filePath = any(), lat = any(), lng = any())
            } throws RuntimeException("disk full")
            val luxRepository = mockk<LuxReadingRepository>(relaxed = true)
            val locationTracker = mockk<LocationTracker>()
            coEvery { locationTracker.getCurrentLocation() } returns LatLng(10.0, 106.0)
            val vm = PolePhotoCaptureViewModel(savedStateHandle(), repository, locationTracker, luxRepository)
            var done = false

            vm.onPhotoCaptured(File("x.jpg"), luxReading) { done = true }
            dispatcher.scheduler.advanceUntilIdle()

            coVerify(exactly = 0) { luxRepository.record(any(), any(), any(), any(), any()) }
            assertTrue(done)
        }
}
