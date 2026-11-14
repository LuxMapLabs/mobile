package com.luxmap.feature.workorder.polegps.ui.measure

import androidx.lifecycle.SavedStateHandle
import com.luxmap.feature.workorder.polegps.data.GpsFix
import com.luxmap.feature.workorder.polegps.data.GpsFixSource
import com.luxmap.feature.workorder.polegps.data.PoleGpsRepository
import com.luxmap.feature.workorder.polegps.data.PoleReference
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class PoleGpsMeasureViewModelTest {
    private val repository = mockk<PoleGpsRepository>(relaxed = true)
    private var fixesFlow: Flow<GpsFix> = flowOf(GpsFix(10.1, 106.1, 4.0, Instant.ofEpochSecond(1)))
    private val source =
        object : GpsFixSource {
            override fun fixes(): Flow<GpsFix> = fixesFlow
        }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        coEvery { repository.poleReference("WO-1", "P-1") } returns PoleReference("P-1", 3, 10.0, 106.0)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() =
        PoleGpsMeasureViewModel(SavedStateHandle(mapOf("workOrderId" to "WO-1", "poleId" to "P-1")), repository, source)

    @Test
    fun `starts in Loading then shows the pole label and previous position once permission is granted`() =
        runTest {
            val vm = viewModel()
            vm.onPermissionGranted()
            val state = vm.state.value as PoleGpsMeasureUiState.Success
            assertEquals("Cột 3 · P-1", state.poleLabel)
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
            fixesFlow = flowOf(GpsFix(10.5, 106.5, 7.0, Instant.ofEpochSecond(42)))
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
    fun `onPhotoCaptured without location permission saves zeroed coordinates instead of crashing`() =
        runTest {
            val vm = viewModel()
            val file = File("p1.jpg")
            var done = false
            vm.onPhotoCaptured(file, hasLocationPermission = false) { done = true }
            coVerify { repository.saveCapturedPhoto("WO-1", "P-1", file.absolutePath, 0.0, 0.0, 0.0, any()) }
            assertTrue(done)
        }
}
