package com.luxmap.feature.workorder.ui.completion

import androidx.lifecycle.SavedStateHandle
import com.luxmap.core.location.LocationTracker
import com.luxmap.feature.workorder.data.WorkOrderCompletionRepository
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.maplibre.android.geometry.LatLng
import java.io.File

private const val WORK_ORDER_ID = "WO-1"

private fun savedStateHandle() = SavedStateHandle(mapOf(EvidenceCaptureViewModel.WORK_ORDER_ID_ARG to WORK_ORDER_ID))

@OptIn(ExperimentalCoroutinesApi::class)
class EvidenceCaptureViewModelTest {
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
    fun `outputFile is named after the viewModel's own clientOpId, under the work order's evidence folder`() {
        val repository = mockk<WorkOrderCompletionRepository>()
        val locationTracker = mockk<LocationTracker>()
        val vm = EvidenceCaptureViewModel(savedStateHandle(), repository, locationTracker)
        val baseDir =
            File.createTempFile("evidence-test", "").apply {
                delete()
                mkdirs()
            }

        val file = vm.outputFile(baseDir)

        assertEquals("evidence/$WORK_ORDER_ID/${vm.clientOpId}.jpg", file.relativeTo(baseDir).path.replace("\\", "/"))
    }

    @Test
    fun `onPhotoCaptured writes evidence through the repository using the current location, then calls onDone`() =
        runTest {
            val repository = mockk<WorkOrderCompletionRepository>(relaxed = true)
            val locationTracker = mockk<LocationTracker>()
            every { locationTracker.hasLocationPermission() } returns true
            io.mockk.coEvery { locationTracker.getCurrentLocation() } returns LatLng(10.97, 106.49)
            val vm = EvidenceCaptureViewModel(savedStateHandle(), repository, locationTracker)
            var doneCalled = false
            val file = File("/data/evidence/${vm.clientOpId}.jpg")

            vm.onPhotoCaptured(file) { doneCalled = true }
            dispatcher.scheduler.advanceUntilIdle()

            coVerify {
                repository.captureAfterEvidence(
                    workOrderId = WORK_ORDER_ID,
                    clientOpId = vm.clientOpId,
                    filePath = file.absolutePath,
                    lat = 10.97,
                    lng = 106.49,
                    capturedAt = any(),
                )
            }
            org.junit.Assert.assertTrue(doneCalled)
        }

    @Test
    fun `onPhotoCaptured falls back to 0,0 when no location is available, rather than failing the capture`() =
        runTest {
            val repository = mockk<WorkOrderCompletionRepository>(relaxed = true)
            val locationTracker = mockk<LocationTracker>()
            every { locationTracker.hasLocationPermission() } returns false
            io.mockk.coEvery { locationTracker.getCurrentLocation() } returns null
            val vm = EvidenceCaptureViewModel(savedStateHandle(), repository, locationTracker)
            val file = File("/data/evidence/${vm.clientOpId}.jpg")

            vm.onPhotoCaptured(file) {}
            dispatcher.scheduler.advanceUntilIdle()

            coVerify {
                repository.captureAfterEvidence(
                    workOrderId = WORK_ORDER_ID,
                    clientOpId = vm.clientOpId,
                    filePath = file.absolutePath,
                    lat = 0.0,
                    lng = 0.0,
                    capturedAt = any(),
                )
            }
        }
}
