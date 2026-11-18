package com.luxmap.feature.workorder.ui.completion

import androidx.lifecycle.SavedStateHandle
import com.luxmap.core.location.LocationTracker
import com.luxmap.feature.workorder.data.EvidenceKind
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
            val file = vm.outputFile(kotlin.io.path.createTempDirectory().toFile()).apply { writeText("jpeg") }

            vm.onPhotoCaptured(file) { doneCalled = true }
            dispatcher.scheduler.advanceUntilIdle()

            coVerify {
                repository.captureEvidence(
                    workOrderId = WORK_ORDER_ID,
                    kind = EvidenceKind.AFTER,
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
    fun `onPhotoCaptured without a location holds the photo instead of saving fake coordinates`() =
        runTest {
            val repository = mockk<WorkOrderCompletionRepository>(relaxed = true)
            val locationTracker = mockk<LocationTracker>()
            every { locationTracker.hasLocationPermission() } returns false
            io.mockk.coEvery { locationTracker.getCurrentLocation() } returns null
            val handle = savedStateHandle().apply { set("kind", "observation") }
            val vm = EvidenceCaptureViewModel(handle, repository, locationTracker)
            var doneCalled = false
            val file = vm.outputFile(kotlin.io.path.createTempDirectory().toFile()).apply { writeText("jpeg") }

            vm.onPhotoCaptured(file) { doneCalled = true }
            dispatcher.scheduler.advanceUntilIdle()

            coVerify(exactly = 0) {
                repository.captureEvidence(any(), any(), any(), any(), any(), any(), any())
            }
            assertEquals(1, vm.state.value.awaitingLocation)
            org.junit.Assert.assertFalse(doneCalled)
            org.junit.Assert.assertTrue(file.exists())
        }

    @Test
    fun `a camera photo held for location is saved with the retried current location`() =
        runTest {
            val repository = mockk<WorkOrderCompletionRepository>(relaxed = true)
            val locationTracker = mockk<LocationTracker>()
            io.mockk.coEvery { locationTracker.getCurrentLocation() } returnsMany listOf(null, LatLng(10.97, 106.49))
            val vm = EvidenceCaptureViewModel(savedStateHandle(), repository, locationTracker)
            val file = vm.outputFile(kotlin.io.path.createTempDirectory().toFile()).apply { writeText("jpeg") }

            vm.onPhotoCaptured(file) {}
            dispatcher.scheduler.advanceUntilIdle()
            vm.useCurrentLocation()
            dispatcher.scheduler.advanceUntilIdle()

            coVerify {
                repository.captureEvidence(
                    workOrderId = WORK_ORDER_ID,
                    kind = EvidenceKind.AFTER,
                    clientOpId = vm.clientOpId,
                    filePath = file.absolutePath,
                    lat = 10.97,
                    lng = 106.49,
                    capturedAt = any(),
                )
            }
            assertEquals(0, vm.state.value.awaitingLocation)
            assertEquals(1, vm.state.value.added)
        }
}
