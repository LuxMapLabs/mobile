package com.luxmap.feature.map.ui

import app.cash.turbine.test
import com.luxmap.core.network.ConnectivityObserver
import com.luxmap.core.theme.AssetCondition
import com.luxmap.feature.map.data.CabinetMarker
import com.luxmap.feature.map.data.CabinetTopology
import com.luxmap.feature.map.data.CabinetTopologyException
import com.luxmap.feature.map.data.CommuneOutline
import com.luxmap.feature.map.data.GisMapDataset
import com.luxmap.feature.map.data.MapRepository
import com.luxmap.feature.map.data.PoleMarker
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
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
import org.junit.Assert.assertNull
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

private fun cabinet(id: String) =
    CabinetMarker(
        cabinetId = id,
        name = "Tủ $id",
        communeId = "COM-1",
        lat = 10.55,
        lng = 105.85,
        feeders = emptyList(),
        iotNodeCount = 0,
    )

private fun topology(unlocated: Int) =
    CabinetTopology(
        edges = emptyList(),
        unlocatedPoleCount = unlocated,
        unroutedPoleCount = 0,
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
    fun `emits Empty when all map layers are empty`() =
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
    fun `cabinet search still works in a dataset without poles`() =
        runTest {
            val cabinet = cabinet("CAB-A")
            val dataset = GisMapDataset(poles = emptyList(), segments = emptyList(), cabinets = listOf(cabinet))
            val repository = mockk<MapRepository>()
            every { repository.observeGisMapDataset() } returns flowOf(dataset)
            val viewModel = MapViewModel(repository, fakeConnectivityObserver())

            viewModel.searchResults.test {
                assertEquals(MapSearchResults(), awaitItem())
                viewModel.setSearchQuery("CAB-A")
                assertEquals(MapSearchResults(cabinets = listOf(cabinet)), awaitItem())
            }
        }

    @Test
    fun `emits Success when the commune layer is the only layer with data`() =
        runTest {
            val dataset =
                GisMapDataset(
                    poles = emptyList(),
                    segments = emptyList(),
                    communes = listOf(CommuneOutline("COM-1", "Xã 1", emptyList())),
                )
            val repository = mockk<MapRepository>()
            every { repository.observeGisMapDataset() } returns flowOf(dataset)
            val viewModel = MapViewModel(repository, fakeConnectivityObserver())

            viewModel.uiState.test {
                assertEquals(MapUiState.Loading, awaitItem())
                assertEquals(MapUiState.Success(dataset = dataset, filteredDataset = dataset), awaitItem())
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
                assertEquals(false, error.isNetworkError)
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

    @Test
    fun `an IOException is a network error`() =
        runTest {
            val repository = mockk<MapRepository>()
            every { repository.observeGisMapDataset() } returns flow { throw java.io.IOException("offline") }
            val viewModel = MapViewModel(repository, fakeConnectivityObserver())

            viewModel.uiState.test {
                assertEquals(MapUiState.Loading, awaitItem())
                assertEquals(true, (awaitItem() as MapUiState.Error).isNetworkError)
            }
        }

    private fun repositoryWithCabinets(): MapRepository {
        val repository = mockk<MapRepository>()
        every { repository.observeGisMapDataset() } returns
            flowOf(
                GisMapDataset(
                    poles = listOf(pole("P-1")),
                    segments = emptyList(),
                    cabinets = listOf(cabinet("CAB-A"), cabinet("CAB-B")),
                ),
            )
        return repository
    }

    @Test
    fun `selecting a cabinet loads its diagram`() =
        runTest {
            val repository = repositoryWithCabinets()
            coEvery { repository.cabinetTopology("CAB-A") } returns topology(2)
            val viewModel = MapViewModel(repository, fakeConnectivityObserver())
            dispatcher.scheduler.advanceUntilIdle()
            viewModel.selectCabinet("CAB-A")
            assertEquals(TopologyState.Loading, viewModel.cabinetSelection.value?.topology)
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals("CAB-A", viewModel.cabinetSelection.value?.cabinet?.cabinetId)
            assertEquals(TopologyState.Loaded(topology(2)), viewModel.cabinetSelection.value?.topology)
        }

    @Test
    fun `a newer selection wins over a slower older diagram`() =
        runTest {
            val repository = repositoryWithCabinets()
            val slowA = CompletableDeferred<CabinetTopology>()
            coEvery { repository.cabinetTopology("CAB-A") } coAnswers { slowA.await() }
            coEvery { repository.cabinetTopology("CAB-B") } returns topology(5)
            val viewModel = MapViewModel(repository, fakeConnectivityObserver())
            dispatcher.scheduler.advanceUntilIdle()
            viewModel.selectCabinet("CAB-A")
            dispatcher.scheduler.advanceUntilIdle()
            viewModel.selectCabinet("CAB-B")
            dispatcher.scheduler.advanceUntilIdle()
            slowA.complete(topology(1))
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals("CAB-B", viewModel.cabinetSelection.value?.cabinet?.cabinetId)
            assertEquals(TopologyState.Loaded(topology(5)), viewModel.cabinetSelection.value?.topology)
        }

    @Test
    fun `clearing the cabinet removes the selection`() =
        runTest {
            val repository = repositoryWithCabinets()
            coEvery { repository.cabinetTopology(any()) } returns topology(0)
            val viewModel = MapViewModel(repository, fakeConnectivityObserver())
            dispatcher.scheduler.advanceUntilIdle()
            viewModel.selectCabinet("CAB-A")
            dispatcher.scheduler.advanceUntilIdle()
            viewModel.clearCabinet()
            assertNull(viewModel.cabinetSelection.value)
        }

    @Test
    fun `diagram errors use the screen copy`() =
        runTest {
            val repository = repositoryWithCabinets()
            coEvery { repository.cabinetTopology("CAB-A") } throws CabinetTopologyException.NotInScope()
            coEvery { repository.cabinetTopology("CAB-B") } throws CabinetTopologyException.PolesWithoutSegment()
            val viewModel = MapViewModel(repository, fakeConnectivityObserver())
            dispatcher.scheduler.advanceUntilIdle()
            viewModel.selectCabinet("CAB-A")
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals(
                TopologyState.Error("Tủ này không thuộc địa bàn của bạn"),
                viewModel.cabinetSelection.value?.topology,
            )
            viewModel.selectCabinet("CAB-B")
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals(
                TopologyState.Error("Có cột chưa gắn tuyến, chưa vẽ được sơ đồ"),
                viewModel.cabinetSelection.value?.topology,
            )
        }

    @Test
    fun `search finds cabinets by name or id`() =
        runTest {
            val viewModel = MapViewModel(repositoryWithCabinets(), fakeConnectivityObserver())
            viewModel.searchResults.test {
                awaitItem()
                viewModel.setSearchQuery("cab-b")
                var results = awaitItem()
                while (results.cabinets.isEmpty()) results = awaitItem()
                assertEquals(listOf("CAB-B"), results.cabinets.map { it.cabinetId })
            }
        }
}
