package com.luxmap.feature.home.ui

import app.cash.turbine.test
import com.luxmap.core.network.ConnectivityObserver
import com.luxmap.feature.auth.data.AuthRepository
import com.luxmap.feature.home.data.HomeData
import com.luxmap.feature.home.data.HomeMetrics
import com.luxmap.feature.home.data.HomeRepository
import com.luxmap.feature.home.data.WorkOrderCluster
import com.luxmap.feature.home.data.WorkOrderSummaryItem
import com.luxmap.feature.workorder.data.CompletionSyncState
import com.luxmap.feature.workorder.data.WorkOrderCompletionRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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

private fun fakeCompletionRepository(
    states: Flow<Map<String, CompletionSyncState>> = flowOf(emptyMap()),
): WorkOrderCompletionRepository {
    val repository = mockk<WorkOrderCompletionRepository>()
    every { repository.observeCompletionSyncStates() } returns states
    return repository
}

private fun fakeAuthRepository(username: String? = "kysu1"): AuthRepository {
    val repository = mockk<AuthRepository>()
    every { repository.observeUsername() } returns flowOf(username)
    return repository
}

private fun summaryItem(
    id: String,
    kind: String,
) = WorkOrderSummaryItem(
    workOrderId = id,
    woStatus = "assigned",
    dueDate = null,
    priorityScore = null,
    taskKind = kind,
    communeId = "COM-001",
    title = "Lệnh $id",
    scheduledDate = null,
    faultCount = 0,
)

private fun homeData(vararg items: WorkOrderSummaryItem) =
    HomeData(
        metrics =
            HomeMetrics(
                assignedCount = items.size,
                inProgressCount = 0,
                overdueCount = 0,
                plannedSweepCount = null,
            ),
        clusters = listOf(WorkOrderCluster(clusterLabel = "COM-001", items = items.toList())),
    )

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val network = MutableStateFlow(true)
    private val online = mockk<ConnectivityObserver> { every { isOnline } returns network }

    @Test
    fun `retains the existing profile full name when tasks are empty and while refreshing`() =
        runTest {
            val repository = mockk<HomeRepository>()
            every { repository.observeHomeData() } returns flowOf(homeData().copy(fullName = "Nguyễn Văn Bình"))
            val viewModel = HomeViewModel(repository, fakeCompletionRepository(), fakeAuthRepository(), online)
            viewModel.uiState.test {
                assertEquals(HomeUiState.Loading, awaitItem())
                assertEquals(HomeUiState.Empty, awaitItem())
                assertEquals("Nguyễn Văn Bình", viewModel.fullName.value)
                viewModel.retry()
                assertEquals(HomeUiState.Loading, awaitItem())
                assertEquals("Nguyễn Văn Bình", viewModel.fullName.value)
                assertEquals(HomeUiState.Empty, awaitItem())
            }
        }

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
    fun `emits Empty when there are no work orders at all`() =
        runTest {
            val repository = mockk<HomeRepository>()
            every { repository.observeHomeData() } returns flowOf(homeData())
            val viewModel = HomeViewModel(repository, fakeCompletionRepository(), fakeAuthRepository(), online)

            viewModel.uiState.test {
                assertEquals(HomeUiState.Loading, awaitItem())
                assertEquals(HomeUiState.Empty, awaitItem())
            }
        }

    @Test
    fun `returning to Home fetches a repair started from a notification`() =
        runTest {
            val repair = summaryItem("R1", "repair").copy(woStatus = "in_progress")
            val repository = mockk<HomeRepository>()
            every { repository.observeHomeData() } returnsMany listOf(flowOf(homeData()), flowOf(homeData(repair)))
            val viewModel = HomeViewModel(repository, fakeCompletionRepository(), fakeAuthRepository(), online)

            viewModel.uiState.test {
                assertEquals(HomeUiState.Loading, awaitItem())
                assertEquals(HomeUiState.Empty, awaitItem())
                viewModel.onResume()
                expectNoEvents()

                viewModel.onResume()
                assertEquals(HomeUiState.Loading, awaitItem())
                val refreshed = awaitItem() as HomeUiState.Success
                assertEquals(listOf(repair), refreshed.data.visibleTasks())
            }
        }

    @Test
    fun `emits Success when the API only returns survey and pole gps work orders`() =
        runTest {
            val repository = mockk<HomeRepository>()
            every { repository.observeHomeData() } returns
                flowOf(homeData(summaryItem("S1", "survey"), summaryItem("G1", "pole_gps_update")))
            val viewModel = HomeViewModel(repository, fakeCompletionRepository(), fakeAuthRepository(), online)

            viewModel.uiState.test {
                assertEquals(HomeUiState.Loading, awaitItem())
                val success = awaitItem() as HomeUiState.Success
                assertEquals(listOf("S1", "G1"), success.data.visibleTasks().map { it.workOrderId })
            }
        }

    @Test
    fun `emits Success when only one tab has work orders`() =
        runTest {
            val data = homeData(summaryItem("R1", "repair"))
            val repository = mockk<HomeRepository>()
            every { repository.observeHomeData() } returns flowOf(data)
            val viewModel = HomeViewModel(repository, fakeCompletionRepository(), fakeAuthRepository(), online)

            viewModel.uiState.test {
                assertEquals(HomeUiState.Loading, awaitItem())
                assertEquals(data, (awaitItem() as HomeUiState.Success).data)
            }
        }

    @Test
    fun `carries sync states into Success and drops the badge once synced`() =
        runTest {
            val states = MutableStateFlow(mapOf("WO-1" to CompletionSyncState.PENDING))
            val repository = mockk<HomeRepository>()
            every { repository.observeHomeData() } returns flowOf(homeData(summaryItem("WO-1", "repair")))
            val viewModel = HomeViewModel(repository, fakeCompletionRepository(states), fakeAuthRepository(), online)

            viewModel.uiState.test {
                assertEquals(HomeUiState.Loading, awaitItem())
                assertEquals(
                    mapOf("WO-1" to CompletionSyncState.PENDING),
                    (awaitItem() as HomeUiState.Success).completionSyncStates,
                )
                states.value = emptyMap()
                assertEquals(
                    emptyMap<String, CompletionSyncState>(),
                    (awaitItem() as HomeUiState.Success).completionSyncStates,
                )
            }
        }

    @Test
    fun `an IOException is a network error, anything else is not`() =
        runTest {
            val repository = mockk<HomeRepository>()
            every { repository.observeHomeData() } returnsMany
                listOf(
                    flow { throw IOException("timeout") },
                    flow { throw IllegalStateException("boom") },
                )
            val viewModel = HomeViewModel(repository, fakeCompletionRepository(), fakeAuthRepository(), online)

            viewModel.uiState.test {
                assertEquals(HomeUiState.Loading, awaitItem())
                assertEquals(true, (awaitItem() as HomeUiState.Error).isNetworkError)
                viewModel.retry()
                assertEquals(HomeUiState.Loading, awaitItem())
                assertEquals(false, (awaitItem() as HomeUiState.Error).isNetworkError)
            }
        }

    @Test
    fun `retry cancels the previous collector so an old sync state cannot overwrite the new one`() =
        runTest {
            val oldStates = MutableStateFlow(mapOf("WO-1" to CompletionSyncState.PENDING))
            val newStates = MutableStateFlow(mapOf("WO-1" to CompletionSyncState.FAILED))
            val completion = mockk<WorkOrderCompletionRepository>()
            every { completion.observeCompletionSyncStates() } returnsMany listOf(oldStates, newStates)
            val repository = mockk<HomeRepository>()
            every { repository.observeHomeData() } returns flowOf(homeData(summaryItem("WO-1", "repair")))
            val viewModel = HomeViewModel(repository, completion, fakeAuthRepository(), online)

            viewModel.uiState.test {
                assertEquals(HomeUiState.Loading, awaitItem())
                awaitItem()
                viewModel.retry()
                assertEquals(HomeUiState.Loading, awaitItem())
                assertEquals(
                    mapOf("WO-1" to CompletionSyncState.FAILED),
                    (awaitItem() as HomeUiState.Success).completionSyncStates,
                )
                oldStates.value = mapOf("WO-1" to CompletionSyncState.CONFLICT)
                expectNoEvents()
            }
        }

    @Test
    fun `emits Error when the repository flow fails`() =
        runTest {
            val repository = mockk<HomeRepository>()
            every { repository.observeHomeData() } returns flow { throw IllegalStateException("network down") }
            val viewModel = HomeViewModel(repository, fakeCompletionRepository(), fakeAuthRepository(), online)

            viewModel.uiState.test {
                assertEquals(HomeUiState.Loading, awaitItem())
                val error = awaitItem() as HomeUiState.Error
                assertEquals("network down", error.message)
            }
        }

    @Test
    fun `retry re-fetches and can recover from a previous error`() =
        runTest {
            val data = homeData(summaryItem("WO-1", "repair"))
            val repository = mockk<HomeRepository>()
            every { repository.observeHomeData() } returnsMany
                listOf(
                    flow { throw IllegalStateException("network down") },
                    flowOf(data),
                )
            val viewModel = HomeViewModel(repository, fakeCompletionRepository(), fakeAuthRepository(), online)

            viewModel.uiState.test {
                assertEquals(HomeUiState.Loading, awaitItem())
                awaitItem() as HomeUiState.Error

                viewModel.retry()

                assertEquals(HomeUiState.Loading, awaitItem())
                val success = awaitItem() as HomeUiState.Success
                assertEquals(data, success.data)
            }
        }

    @Test
    fun `a failed Home reloads by itself when the network comes back`() =
        runTest {
            val repository = mockk<HomeRepository>()
            var calls = 0
            every { repository.observeHomeData() } returns
                flow {
                    calls++
                    if (calls == 1) throw IOException("offline")
                    emit(homeData().copy(fullName = "Bình"))
                }
            network.value = false
            val viewModel = HomeViewModel(repository, fakeCompletionRepository(), fakeAuthRepository(), online)
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals(true, viewModel.uiState.value is HomeUiState.Error)

            network.value = true
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(2, calls)
            assertEquals("Bình", viewModel.fullName.value)
        }
}
