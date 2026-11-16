package com.luxmap.feature.survey.ui.hub

import app.cash.turbine.test
import com.luxmap.feature.survey.data.AssignedRoutesResult
import com.luxmap.feature.survey.data.AssignedSurveyRoute
import com.luxmap.feature.survey.data.SurveyRepository
import com.luxmap.feature.workorder.data.CompletionSyncState
import com.luxmap.feature.workorder.data.WorkOrderCompletionRepository
import io.mockk.coEvery
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class SurveyHubViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val route =
        AssignedSurveyRoute(
            workOrderId = "WO-1",
            title = "Khảo sát đường A",
            communeId = "COM-001",
            woStatus = "assigned",
            dueDate = null,
            scheduledDate = "2026-10-01",
        )

    private fun viewModel(
        routes: List<AssignedSurveyRoute> = emptyList(),
        isStale: Boolean = false,
        repositoryFlowThrows: Boolean = false,
        locallyCompletedWorkOrderIds: Set<String> = emptySet(),
        poleGpsOrders: List<AssignedSurveyRoute> = emptyList(),
        syncStates: Map<String, CompletionSyncState> = emptyMap(),
        poleGpsThrows: Boolean = false,
        routesThrowable: Throwable = IllegalStateException("offline"),
    ): SurveyHubViewModel {
        val repository = mockk<SurveyRepository>()
        every { repository.observeAssignedRoutes() } returns
            if (repositoryFlowThrows) {
                flow { throw routesThrowable }
            } else {
                flowOf(AssignedRoutesResult(routes, isStale))
            }
        every { repository.observeAssignedPoleGpsOrders() } returns
            if (poleGpsThrows) flow { throw IllegalStateException("x") } else flowOf(poleGpsOrders)
        val completionRepository = mockk<WorkOrderCompletionRepository>()
        every { completionRepository.observeCompletedWorkOrderIds() } returns flowOf(locallyCompletedWorkOrderIds)
        every { completionRepository.observeCompletionSyncStates() } returns flowOf(syncStates)
        return SurveyHubViewModel(repository, completionRepository)
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `emits Empty when there are no routes`() =
        runTest {
            viewModel().uiState.test {
                assertEquals(SurveyHubUiState.Loading, awaitItem())
                assertEquals(SurveyHubUiState.Empty, awaitItem())
            }
        }

    @Test
    fun `emits Success with assigned routes when there is at least one`() =
        runTest {
            viewModel(routes = listOf(route)).uiState.test {
                awaitItem()
                val success = awaitItem() as SurveyHubUiState.Success
                assertEquals(listOf(route), success.assignedRoutes)
            }
        }

    // Found on a real device 2026-10-29: a route's Hub card looked completely unchanged after
    // completing its survey at the pole list, since nothing locally tracked a pending completion
    // for it - same local-state gap WorkOrderDetailScreen already guards against for
    // repair/inspection.
    @Test
    fun `emits Success with locallyCompletedWorkOrderIds from the completion repository`() =
        runTest {
            viewModel(
                routes = listOf(route, route.copy(workOrderId = "WO-2")),
                locallyCompletedWorkOrderIds = setOf("WO-1"),
            ).uiState.test {
                awaitItem()
                val success = awaitItem() as SurveyHubUiState.Success
                assertEquals(setOf("WO-1"), success.locallyCompletedWorkOrderIds)
            }
        }

    @Test
    fun `emits Success with isStale when the repository falls back to its cache`() =
        runTest {
            viewModel(routes = listOf(route), isStale = true).uiState.test {
                awaitItem()
                val success = awaitItem() as SurveyHubUiState.Success
                assertTrue(success.isStale)
            }
        }

    @Test
    fun `emits Error when the assigned-routes flow fails`() =
        runTest {
            viewModel(repositoryFlowThrows = true).uiState.test {
                awaitItem()
                val error = awaitItem() as SurveyHubUiState.Error
                assertEquals("offline", error.message)
            }
        }

    @Test
    fun `onRetry re-loads after an error and can reach Success`() =
        runTest {
            val repository = mockk<SurveyRepository>()
            every { repository.observeAssignedRoutes() } returnsMany
                listOf(
                    flow { throw IllegalStateException("offline") },
                    flowOf(AssignedRoutesResult(listOf(route), isStale = false)),
                )
            every { repository.observeAssignedPoleGpsOrders() } returns flowOf(emptyList())
            val completionRepository = mockk<WorkOrderCompletionRepository>()
            every { completionRepository.observeCompletedWorkOrderIds() } returns flowOf(emptySet())
            every { completionRepository.observeCompletionSyncStates() } returns flowOf(emptyMap())
            val viewModel = SurveyHubViewModel(repository, completionRepository)

            viewModel.uiState.test {
                awaitItem() // Loading
                awaitItem() // Error
                viewModel.onRetry()
                assertEquals(SurveyHubUiState.Loading, awaitItem())
                val success = awaitItem() as SurveyHubUiState.Success
                assertEquals(listOf(route), success.assignedRoutes)
            }
        }

    @Test
    fun `refresh re-loads from Success, unlike onRetry which only works from Error`() =
        runTest {
            val startedRoute = route.copy(woStatus = "in_progress")
            val repository = mockk<SurveyRepository>()
            every { repository.observeAssignedRoutes() } returnsMany
                listOf(
                    flowOf(AssignedRoutesResult(listOf(route), isStale = false)),
                    flowOf(AssignedRoutesResult(listOf(startedRoute), isStale = false)),
                )
            every { repository.observeAssignedPoleGpsOrders() } returns flowOf(emptyList())
            val completionRepository = mockk<WorkOrderCompletionRepository>()
            every { completionRepository.observeCompletedWorkOrderIds() } returns flowOf(emptySet())
            every { completionRepository.observeCompletionSyncStates() } returns flowOf(emptyMap())
            val viewModel = SurveyHubViewModel(repository, completionRepository)

            viewModel.uiState.test {
                awaitItem() // Loading
                awaitItem() // Success (assigned)
                viewModel.refresh()
                val success = awaitItem() as SurveyHubUiState.Success
                assertEquals(listOf(startedRoute), success.assignedRoutes)
            }
        }

    @Test
    fun `onStartRoute calls the API then reloads to pick up the new status`() =
        runTest {
            val startedRoute = route.copy(woStatus = "in_progress")
            val repository = mockk<SurveyRepository>()
            every { repository.observeAssignedRoutes() } returnsMany
                listOf(
                    flowOf(AssignedRoutesResult(listOf(route), isStale = false)),
                    flowOf(AssignedRoutesResult(listOf(startedRoute), isStale = false)),
                )
            every { repository.observeAssignedPoleGpsOrders() } returns flowOf(emptyList())
            coEvery { repository.startRoute("WO-1") } returns Result.success(Unit)
            val completionRepository = mockk<WorkOrderCompletionRepository>()
            every { completionRepository.observeCompletedWorkOrderIds() } returns flowOf(emptySet())
            every { completionRepository.observeCompletionSyncStates() } returns flowOf(emptyMap())
            val viewModel = SurveyHubViewModel(repository, completionRepository)

            viewModel.uiState.test {
                awaitItem() // Loading
                awaitItem() // Success (assigned)
                viewModel.onStartRoute("WO-1")
                val starting = awaitItem() as SurveyHubUiState.Success
                assertEquals("WO-1", starting.startingWorkOrderId)
                val success = awaitItem() as SurveyHubUiState.Success
                assertEquals(listOf(startedRoute), success.assignedRoutes)
                assertEquals(null, success.startingWorkOrderId)
            }
        }

    @Test
    fun `a successful onStartRoute emits startedRoute with the work order id`() =
        runTest {
            val startedRoute = route.copy(woStatus = "in_progress")
            val repository = mockk<SurveyRepository>()
            every { repository.observeAssignedRoutes() } returnsMany
                listOf(
                    flowOf(AssignedRoutesResult(listOf(route), isStale = false)),
                    flowOf(AssignedRoutesResult(listOf(startedRoute), isStale = false)),
                )
            every { repository.observeAssignedPoleGpsOrders() } returns flowOf(emptyList())
            coEvery { repository.startRoute("WO-1") } returns Result.success(Unit)
            val completionRepository = mockk<WorkOrderCompletionRepository>()
            every { completionRepository.observeCompletedWorkOrderIds() } returns flowOf(emptySet())
            every { completionRepository.observeCompletionSyncStates() } returns flowOf(emptyMap())
            val viewModel = SurveyHubViewModel(repository, completionRepository)
            // Let init's load() reach Success first - otherwise onStartRoute's own
            // "current !is Success" guard returns early and never calls the repository, same
            // as why the uiState-based tests above drain Loading/Success via awaitItem() first.
            dispatcher.scheduler.advanceUntilIdle()

            viewModel.startedRoute.test {
                viewModel.onStartRoute("WO-1")
                assertEquals("WO-1", awaitItem())
            }
        }

    @Test
    fun `a failed onStartRoute clears startingWorkOrderId and sets startError`() =
        runTest {
            val repository = mockk<SurveyRepository>()
            every { repository.observeAssignedRoutes() } returns flowOf(AssignedRoutesResult(listOf(route), false))
            every { repository.observeAssignedPoleGpsOrders() } returns flowOf(emptyList())
            coEvery { repository.startRoute("WO-1") } returns Result.failure(IllegalStateException("conflict"))
            val completionRepository = mockk<WorkOrderCompletionRepository>()
            every { completionRepository.observeCompletedWorkOrderIds() } returns flowOf(emptySet())
            every { completionRepository.observeCompletionSyncStates() } returns flowOf(emptyMap())
            val viewModel = SurveyHubViewModel(repository, completionRepository)

            viewModel.uiState.test {
                awaitItem() // Loading
                awaitItem() // Success
                viewModel.onStartRoute("WO-1")
                awaitItem() // Success (starting)
                val failed = awaitItem() as SurveyHubUiState.Success
                assertEquals(null, failed.startingWorkOrderId)
                assertEquals("conflict", failed.startError)
            }
        }

    @Test
    fun `assigned pole_gps_update orders reach Success alongside the survey routes`() =
        runTest {
            val poleGpsOrder = route.copy(workOrderId = "WO-9", title = "Cập nhật GPS cột tuyến B")
            val viewModel = viewModel(poleGpsOrders = listOf(poleGpsOrder))

            viewModel.uiState.test {
                awaitItem() // Loading
                val success = awaitItem() as SurveyHubUiState.Success
                assertEquals(listOf(poleGpsOrder), success.assignedPoleGpsOrders)
            }
        }

    @Test
    fun `pole_gps_update orders alone are enough to avoid the Empty state`() =
        runTest {
            val poleGpsOrder = route.copy(workOrderId = "WO-9")
            val viewModel = viewModel(poleGpsOrders = listOf(poleGpsOrder))

            viewModel.uiState.test {
                awaitItem() // Loading
                assertTrue(awaitItem() is SurveyHubUiState.Success)
            }
        }

    @Test
    fun `carries completion sync states into Success`() =
        runTest {
            viewModel(
                routes = listOf(route.copy(woStatus = "in_progress")),
                syncStates = mapOf("WO-1" to CompletionSyncState.PENDING),
                locallyCompletedWorkOrderIds = setOf("WO-1"),
            ).uiState.test {
                assertEquals(SurveyHubUiState.Loading, awaitItem())
                val success = awaitItem() as SurveyHubUiState.Success
                assertEquals(mapOf("WO-1" to CompletionSyncState.PENDING), success.completionSyncStates)
            }
        }

    @Test
    fun `only synced work orders left means Empty`() =
        runTest {
            viewModel(routes = listOf(route), locallyCompletedWorkOrderIds = setOf("WO-1")).uiState.test {
                assertEquals(SurveyHubUiState.Loading, awaitItem())
                assertEquals(SurveyHubUiState.Empty, awaitItem())
            }
        }

    @Test
    fun `an IOException is a network error`() =
        runTest {
            viewModel(repositoryFlowThrows = true, routesThrowable = IOException("timeout")).uiState.test {
                assertEquals(SurveyHubUiState.Loading, awaitItem())
                assertEquals(true, (awaitItem() as SurveyHubUiState.Error).isNetworkError)
            }
        }

    @Test
    fun `a pole gps flow failure shows Error, not a crash`() =
        runTest {
            viewModel(routes = listOf(route), poleGpsThrows = true).uiState.test {
                assertEquals(SurveyHubUiState.Loading, awaitItem())
                assertEquals(false, (awaitItem() as SurveyHubUiState.Error).isNetworkError)
            }
        }

    @Test
    fun `onRetry reloads from Empty`() =
        runTest {
            val vm = viewModel()
            vm.uiState.test {
                assertEquals(SurveyHubUiState.Loading, awaitItem())
                assertEquals(SurveyHubUiState.Empty, awaitItem())
                vm.onRetry()
                assertEquals(SurveyHubUiState.Loading, awaitItem())
                assertEquals(SurveyHubUiState.Empty, awaitItem())
            }
        }
}
