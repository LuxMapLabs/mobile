package com.luxmap.feature.map.ui

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.luxmap.core.theme.AssetCondition
import com.luxmap.feature.map.data.PoleDetail
import com.luxmap.feature.map.data.PoleDetailRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.IOException

private val DETAIL =
    PoleDetail(
        poleId = "POLE-1",
        segmentId = null,
        segmentName = null,
        lat = null,
        lng = null,
        fixtureType = null,
        powerSource = null,
        lampWatt = null,
        installDate = null,
        warrantyExpiry = null,
        fixtureStatus = AssetCondition.NORMAL,
        statusConfidence = null,
        determinedAt = null,
        sourceChannel = null,
        hasIotNode = false,
        iotNodeStatus = null,
        iotLastReportAt = null,
        luminanceHistory = emptyList(),
        runtimeHistory = emptyList(),
        openFaults = emptyList(),
        recentFrames = emptyList(),
        note = null,
    )

@OptIn(ExperimentalCoroutinesApi::class)
class PoleDetailViewModelTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun handle() = SavedStateHandle(mapOf(PoleDetailViewModel.POLE_ID_ARG to "POLE-1"))

    @Test
    fun `an IOException is a network error and retry loads again`() =
        runTest {
            val repository = mockk<PoleDetailRepository>()
            every { repository.observePoleDetail("POLE-1") } returnsMany
                listOf(flow { throw IOException("offline") }, flowOf(DETAIL))
            val vm = PoleDetailViewModel(handle(), repository)
            vm.uiState.test {
                assertEquals(PoleDetailUiState.Loading, awaitItem())
                assertEquals(true, (awaitItem() as PoleDetailUiState.Error).isNetworkError)
                vm.retry()
                assertEquals(PoleDetailUiState.Loading, awaitItem())
                assertEquals(PoleDetailUiState.Success(DETAIL), awaitItem())
            }
            verify(exactly = 2) { repository.observePoleDetail("POLE-1") }
        }

    @Test
    fun `any other failure is a generic error`() =
        runTest {
            val repository = mockk<PoleDetailRepository>()
            every { repository.observePoleDetail("POLE-1") } returns flow { throw IllegalStateException("bad json") }
            val vm = PoleDetailViewModel(handle(), repository)
            vm.uiState.test {
                awaitItem()
                assertEquals(false, (awaitItem() as PoleDetailUiState.Error).isNetworkError)
            }
        }
}
