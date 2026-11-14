package com.luxmap.feature.workorder.polegps.ui.measure

import androidx.lifecycle.SavedStateHandle
import com.luxmap.feature.workorder.polegps.data.GpsFix
import com.luxmap.feature.workorder.polegps.data.GpsFixSource
import com.luxmap.feature.workorder.polegps.data.PoleGpsRepository
import com.luxmap.feature.workorder.polegps.data.PoleReference
import com.luxmap.feature.workorder.polegps.domain.FixQuality
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class PoleGpsMeasureViewModelTest {
    private val repository = mockk<PoleGpsRepository>(relaxed = true)
    private val fixes = MutableSharedFlow<GpsFix>(extraBufferCapacity = 16)
    private val source =
        object : GpsFixSource {
            override fun fixes(): Flow<GpsFix> = fixes
        }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        coEvery { repository.poleReference("WO-1", "P-1") } returns PoleReference("P-1", 3, 10.0, 106.0)
        coEvery { repository.allMeasurements("WO-1") } returns emptyList()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() =
        PoleGpsMeasureViewModel(SavedStateHandle(mapOf("workOrderId" to "WO-1", "poleId" to "P-1")), repository, source)

    private fun fix(
        accuracy: Double,
        atMillis: Long,
    ) = GpsFix(10.1, 106.1, accuracy, Instant.ofEpochMilli(atMillis))

    @Test
    fun `starts in Loading then shows the pole once permission is granted`() =
        runTest {
            val vm = viewModel()
            vm.onPermissionGranted()
            val state = vm.state.value as PoleGpsMeasureUiState.Success
            assertEquals(FixQuality.WAITING, state.quality)
            assertEquals(3, state.previous?.position)
            assertFalse(state.canSave)
        }

    @Test
    fun `poleLabel names the actual pole, not a placeholder`() =
        runTest {
            val vm = viewModel()
            vm.onPermissionGranted()
            val state = vm.state.value as PoleGpsMeasureUiState.Success
            assertEquals("Cột 3 · P-1", state.poleLabel)
        }

    @Test
    fun `poleLabel falls back to just the pole id when there is no cached position`() =
        runTest {
            coEvery { repository.poleReference("WO-1", "P-1") } returns PoleReference("P-1", null, null, null)
            val vm = viewModel()
            vm.onPermissionGranted()
            val state = vm.state.value as PoleGpsMeasureUiState.Success
            assertEquals("P-1", state.poleLabel)
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
    fun `save is only enabled after the accuracy gate is ready`() =
        runTest {
            val vm = viewModel()
            vm.onPermissionGranted()
            fixes.emit(fix(5.0, 0))
            assertFalse((vm.state.value as PoleGpsMeasureUiState.Success).canSave)
            fixes.emit(fix(5.0, 3000))
            assertTrue((vm.state.value as PoleGpsMeasureUiState.Success).canSave)
        }

    @Test
    fun `a weak fix keeps save disabled and shows the accuracy`() =
        runTest {
            val vm = viewModel()
            vm.onPermissionGranted()
            fixes.emit(fix(25.0, 0))
            val state = vm.state.value as PoleGpsMeasureUiState.Success
            assertEquals(FixQuality.WEAK, state.quality)
            assertEquals(25.0, state.liveFix!!.accuracyM, 0.0)
            assertFalse(state.canSave)
        }

    @Test
    fun `saving stores the latest fix and reports it as saved`() =
        runTest {
            val vm = viewModel()
            vm.onPermissionGranted()
            fixes.emit(fix(4.0, 0))
            fixes.emit(fix(4.0, 3000))
            vm.onSave()
            coVerify { repository.saveCapturedPhoto("WO-1", "P-1", "", 10.1, 106.1, 4.0, Instant.ofEpochMilli(3000)) }
            assertTrue((vm.state.value as PoleGpsMeasureUiState.Success).saved)
        }

    @Test
    fun `saving without a ready gate does nothing`() =
        runTest {
            val vm = viewModel()
            vm.onPermissionGranted()
            fixes.emit(fix(30.0, 0))
            vm.onSave()
            coVerify(exactly = 0) { repository.saveCapturedPhoto(any(), any(), any(), any(), any(), any(), any()) }
        }

    @Test
    fun `measuring again clears the saved flag and restarts the gate`() =
        runTest {
            val vm = viewModel()
            vm.onPermissionGranted()
            fixes.emit(fix(4.0, 0))
            fixes.emit(fix(4.0, 3000))
            vm.onSave()
            vm.onMeasureAgain()
            val state = vm.state.value as PoleGpsMeasureUiState.Success
            assertFalse(state.saved)
            assertFalse(state.canSave)
        }

    @Test
    fun `a previously measured pole shows its stored measurement`() =
        runTest {
            coEvery { repository.allMeasurements("WO-1") } returns
                listOf(
                    com.luxmap.feature.workorder.polegps.data.PoleMeasurement(
                        "P-1",
                        10.2,
                        106.2,
                        3.0,
                        Instant.ofEpochSecond(1),
                        com.luxmap.feature.workorder.polegps.data.MeasurementSource.DEVICE,
                    ),
                )
            val vm = viewModel()
            vm.onPermissionGranted()
            assertEquals(10.2, (vm.state.value as PoleGpsMeasureUiState.Success).stored!!.lat, 0.0)
        }
}
