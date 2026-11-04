package com.luxmap.feature.map.ui

import app.cash.turbine.test
import com.luxmap.core.network.ConnectivityObserver
import com.luxmap.core.theme.AssetCondition
import com.luxmap.feature.map.data.GisMapDataset
import com.luxmap.feature.map.data.MapRepository
import com.luxmap.feature.map.data.PoleMarker
import io.mockk.every
import io.mockk.mockk
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

private fun fakeConnectivityObserver(isOnline: Boolean = true): ConnectivityObserver {
    val observer = mockk<ConnectivityObserver>()
    every { observer.isOnline } returns flowOf(isOnline)
    return observer
}

private fun pole(poleId: String) =
    PoleMarker(
        poleId = poleId,
        segmentId = null,
        lat = 10.55,
        lng = 105.85,
        fixtureStatus = AssetCondition.NORMAL,
        powerSource = null,
        hasIotNode = false,
        nearSensitivePoi = false,
    )

@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModelTest {
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
    fun `emits Empty when the dataset has no poles`() =
        runTest {
            val repository = mockk<MapRepository>()
            every { repository.observeGisMapDataset() } returns
                flowOf(GisMapDataset(poles = emptyList(), segments = emptyList()))
            val viewModel = MapViewModel(repository, fakeConnectivityObserver())

            viewModel.uiState.test {
                assertEquals(MapUiState.Loading, awaitItem())
                assertEquals(MapUiState.Empty, awaitItem())
            }
        }

    @Test
    fun `emits Success with the dataset when there is at least one pole`() =
        runTest {
            val dataset = GisMapDataset(poles = listOf(pole("POLE-1")), segments = emptyList())
            val repository = mockk<MapRepository>()
            every { repository.observeGisMapDataset() } returns flowOf(dataset)
            val viewModel = MapViewModel(repository, fakeConnectivityObserver())

            viewModel.uiState.test {
                assertEquals(MapUiState.Loading, awaitItem())
                val success = awaitItem() as MapUiState.Success
                assertEquals(dataset, success.dataset)
            }
        }

    @Test
    fun `emits Error when the repository flow fails`() =
        runTest {
            val repository = mockk<MapRepository>()
            every { repository.observeGisMapDataset() } returns flow { throw IllegalStateException("network down") }
            val viewModel = MapViewModel(repository, fakeConnectivityObserver())

            viewModel.uiState.test {
                assertEquals(MapUiState.Loading, awaitItem())
                val error = awaitItem() as MapUiState.Error
                assertEquals("network down", error.message)
            }
        }

    @Test
    fun `retry re-fetches and can recover from a previous error`() =
        runTest {
            val dataset = GisMapDataset(poles = listOf(pole("POLE-1")), segments = emptyList())
            val repository = mockk<MapRepository>()
            every { repository.observeGisMapDataset() } returnsMany
                listOf(
                    flow { throw IllegalStateException("network down") },
                    flowOf(dataset),
                )
            val viewModel = MapViewModel(repository, fakeConnectivityObserver())

            viewModel.uiState.test {
                assertEquals(MapUiState.Loading, awaitItem())
                awaitItem() as MapUiState.Error

                viewModel.retry()

                assertEquals(MapUiState.Loading, awaitItem())
                val success = awaitItem() as MapUiState.Success
                assertEquals(dataset, success.dataset)
            }
        }
}
