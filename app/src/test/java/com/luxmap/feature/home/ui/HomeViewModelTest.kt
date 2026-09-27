package com.luxmap.feature.home.ui

import app.cash.turbine.test
import com.luxmap.feature.home.data.HomeData
import com.luxmap.feature.home.data.HomeMetrics
import com.luxmap.feature.home.data.HomeRepository
import com.luxmap.feature.home.data.WorkOrderCluster
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
    fun `emits Empty when repository has no work orders and no planned sweeps`() =
        runTest {
            val repository = mockk<HomeRepository>()
            every { repository.observeHomeData() } returns
                flowOf(
                    HomeData(
                        metrics =
                            HomeMetrics(
                                assignedCount = 0,
                                inProgressCount = 0,
                                overdueCount = 0,
                                plannedSweepCount = 0,
                            ),
                        clusters = emptyList(),
                    ),
                )
            val viewModel = HomeViewModel(repository)

            viewModel.uiState.test {
                assertEquals(HomeUiState.Loading, awaitItem())
                assertEquals(HomeUiState.Empty, awaitItem())
            }
        }

    @Test
    fun `emits Success with the repository data when there is at least one work order`() =
        runTest {
            val data =
                HomeData(
                    metrics =
                        HomeMetrics(
                            assignedCount = 1,
                            inProgressCount = 0,
                            overdueCount = 0,
                            plannedSweepCount = 0,
                        ),
                    clusters = listOf(WorkOrderCluster(clusterLabel = "Xã Đông Thịnh", items = emptyList())),
                )
            val repository = mockk<HomeRepository>()
            every { repository.observeHomeData() } returns flowOf(data)
            val viewModel = HomeViewModel(repository)

            viewModel.uiState.test {
                assertEquals(HomeUiState.Loading, awaitItem())
                val success = awaitItem() as HomeUiState.Success
                assertEquals(data, success.data)
            }
        }

    @Test
    fun `emits Error when the repository flow fails`() =
        runTest {
            val repository = mockk<HomeRepository>()
            every { repository.observeHomeData() } returns flow { throw IllegalStateException("network down") }
            val viewModel = HomeViewModel(repository)

            viewModel.uiState.test {
                assertEquals(HomeUiState.Loading, awaitItem())
                val error = awaitItem() as HomeUiState.Error
                assertEquals("network down", error.message)
            }
        }
}
