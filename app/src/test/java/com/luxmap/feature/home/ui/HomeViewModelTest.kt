package com.luxmap.feature.home.ui

import app.cash.turbine.test
import com.luxmap.feature.auth.data.AuthRepository
import com.luxmap.feature.home.data.HomeData
import com.luxmap.feature.home.data.HomeMetrics
import com.luxmap.feature.home.data.HomeRepository
import com.luxmap.feature.home.data.WorkOrderCluster
import com.luxmap.feature.home.data.WorkOrderSummaryItem
import com.luxmap.feature.workorder.data.WorkOrderCompletionRepository
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

private fun fakeCompletionRepository(ids: Set<String> = emptySet()): WorkOrderCompletionRepository {
    val repository = mockk<WorkOrderCompletionRepository>()
    every { repository.observeCompletedWorkOrderIds() } returns flowOf(ids)
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
            val viewModel = HomeViewModel(repository, fakeCompletionRepository(), fakeAuthRepository())

            viewModel.uiState.test {
                assertEquals(HomeUiState.Loading, awaitItem())
                assertEquals(HomeUiState.Empty, awaitItem())
            }
        }

    @Test
    fun `emits Empty when the API only returns survey and pole gps work orders`() =
        runTest {
            val repository = mockk<HomeRepository>()
            every { repository.observeHomeData() } returns
                flowOf(homeData(summaryItem("S1", "survey"), summaryItem("G1", "pole_gps_update")))
            val viewModel = HomeViewModel(repository, fakeCompletionRepository(), fakeAuthRepository())

            viewModel.uiState.test {
                assertEquals(HomeUiState.Loading, awaitItem())
                assertEquals(HomeUiState.Empty, awaitItem())
            }
        }

    @Test
    fun `emits Success when only one tab has work orders`() =
        runTest {
            val data = homeData(summaryItem("R1", "repair"))
            val repository = mockk<HomeRepository>()
            every { repository.observeHomeData() } returns flowOf(data)
            val viewModel = HomeViewModel(repository, fakeCompletionRepository(), fakeAuthRepository())

            viewModel.uiState.test {
                assertEquals(HomeUiState.Loading, awaitItem())
                assertEquals(data, (awaitItem() as HomeUiState.Success).data)
            }
        }

    @Test
    fun `carries locally completed work order ids into the success state`() =
        runTest {
            val repository = mockk<HomeRepository>()
            every { repository.observeHomeData() } returns flowOf(homeData(summaryItem("WO-1", "repair")))
            val viewModel = HomeViewModel(repository, fakeCompletionRepository(setOf("WO-1")), fakeAuthRepository())

            viewModel.uiState.test {
                assertEquals(HomeUiState.Loading, awaitItem())
                val success = awaitItem() as HomeUiState.Success
                assertEquals(setOf("WO-1"), success.locallyCompletedWorkOrderIds)
            }
        }

    @Test
    fun `emits Error when the repository flow fails`() =
        runTest {
            val repository = mockk<HomeRepository>()
            every { repository.observeHomeData() } returns flow { throw IllegalStateException("network down") }
            val viewModel = HomeViewModel(repository, fakeCompletionRepository(), fakeAuthRepository())

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
            val viewModel = HomeViewModel(repository, fakeCompletionRepository(), fakeAuthRepository())

            viewModel.uiState.test {
                assertEquals(HomeUiState.Loading, awaitItem())
                awaitItem() as HomeUiState.Error

                viewModel.retry()

                assertEquals(HomeUiState.Loading, awaitItem())
                val success = awaitItem() as HomeUiState.Success
                assertEquals(data, success.data)
            }
        }
}
