package com.luxmap.feature.survey.ui.polephoto

import androidx.lifecycle.SavedStateHandle
import com.luxmap.core.location.LocationTracker
import com.luxmap.feature.survey.data.PolePhotoRepository
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.maplibre.android.geometry.LatLng
import java.io.File

private const val WORK_ORDER_ID = "WO-1"
private const val POLE_ID = "POLE-1"

private fun savedStateHandle() =
    SavedStateHandle(
        mapOf(
            PoleVideoCaptureViewModel.WORK_ORDER_ID_ARG to WORK_ORDER_ID,
            PoleVideoCaptureViewModel.POLE_ID_ARG to POLE_ID,
        ),
    )

@OptIn(ExperimentalCoroutinesApi::class)
class PoleVideoCaptureViewModelTest {
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
    fun `onVideoCaptured sends the real GPS fix to the repository and calls onDone`() =
        runTest {
            val repository = mockk<PolePhotoRepository>(relaxed = true)
            val locationTracker = mockk<LocationTracker>()
            coEvery { locationTracker.getCurrentLocation() } returns LatLng(10.0, 106.0)
            val vm = PoleVideoCaptureViewModel(savedStateHandle(), repository, locationTracker)
            val file = File("x.mp4")
            var done = false

            vm.onVideoCaptured(file) { done = true }
            dispatcher.scheduler.advanceUntilIdle()

            coVerify {
                repository.captureVideo(
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
    fun `onVideoCaptured falls back to 0,0 when the GPS fix is unavailable`() =
        runTest {
            val repository = mockk<PolePhotoRepository>(relaxed = true)
            val locationTracker = mockk<LocationTracker>()
            coEvery { locationTracker.getCurrentLocation() } returns null
            val vm = PoleVideoCaptureViewModel(savedStateHandle(), repository, locationTracker)
            val file = File("x.mp4")

            vm.onVideoCaptured(file) {}
            dispatcher.scheduler.advanceUntilIdle()

            coVerify {
                repository.captureVideo(
                    workOrderId = WORK_ORDER_ID,
                    poleId = POLE_ID,
                    filePath = file.absolutePath,
                    lat = 0.0,
                    lng = 0.0,
                )
            }
        }

    @Test
    fun `onVideoCaptured still calls onDone when the repository write throws`() =
        runTest {
            val repository = mockk<PolePhotoRepository>()
            coEvery {
                repository.captureVideo(workOrderId = any(), poleId = any(), filePath = any(), lat = any(), lng = any())
            } throws RuntimeException("disk full")
            val locationTracker = mockk<LocationTracker>()
            coEvery { locationTracker.getCurrentLocation() } returns LatLng(10.0, 106.0)
            val vm = PoleVideoCaptureViewModel(savedStateHandle(), repository, locationTracker)
            var done = false

            vm.onVideoCaptured(File("x.mp4")) { done = true }
            dispatcher.scheduler.advanceUntilIdle()

            assertTrue(done)
        }
}
