package com.luxmap.feature.survey.ui.hub

import app.cash.turbine.test
import com.luxmap.core.network.ConnectivityObserver
import com.luxmap.core.theme.SyncStatus
import com.luxmap.feature.survey.data.AssignedRoutesResult
import com.luxmap.feature.survey.data.AssignedSurveyRoute
import com.luxmap.feature.survey.data.QueuedUploadSession
import com.luxmap.feature.survey.data.SurveyRepository
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
    private val queuedSession = QueuedUploadSession(sessionId = "SESSION-1", surveySweepId = "SWEEP-2")

    private fun viewModel(
        routes: List<AssignedSurveyRoute> = emptyList(),
        isStale: Boolean = false,
        queuedSessions: List<QueuedUploadSession> = emptyList(),
        isOnline: Boolean = true,
        repositoryFlowThrows: Boolean = false,
    ): SurveyHubViewModel {
        val repository = mockk<SurveyRepository>()
        every { repository.observeAssignedRoutes() } returns
            if (repositoryFlowThrows) {
                flow { throw IllegalStateException("offline") }
            } else {
                flowOf(AssignedRoutesResult(routes, isStale))
            }
        every { repository.observeUploadingSessions() } returns flowOf(queuedSessions)
        val connectivity = mockk<ConnectivityObserver>()
        every { connectivity.isOnline } returns flowOf(isOnline)
        return SurveyHubViewModel(repository, connectivity)
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
    fun `emits Empty when there are no routes and nothing uploading`() =
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

    @Test
    fun `emits Success with an uploading session even when there are no assigned routes`() =
        runTest {
            viewModel(queuedSessions = listOf(queuedSession)).uiState.test {
                awaitItem()
                val success = awaitItem() as SurveyHubUiState.Success
                assertTrue(success.assignedRoutes.isEmpty())
                assertEquals(1, success.uploadingSessions.size)
                assertEquals(SyncStatus.QUEUED_ONLINE, success.uploadingSessions.first().syncStatus)
            }
        }

    @Test
    fun `an uploading session is marked QUEUED_OFFLINE when the device is offline`() =
        runTest {
            viewModel(queuedSessions = listOf(queuedSession), isOnline = false).uiState.test {
                awaitItem()
                val success = awaitItem() as SurveyHubUiState.Success
                assertEquals(SyncStatus.QUEUED_OFFLINE, success.uploadingSessions.first().syncStatus)
            }
        }

    @Test
    fun `the unconfirmed cards are always empty while SHOW_UNCONFIRMED_HUB_CARDS is false`() =
        runTest {
            viewModel(routes = listOf(route)).uiState.test {
                awaitItem()
                val success = awaitItem() as SurveyHubUiState.Success
                assertTrue(success.rejectedSweeps.isEmpty())
                assertTrue(success.pendingAiConfirmation.isEmpty())
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
            every { repository.observeUploadingSessions() } returns flowOf(emptyList())
            val connectivity = mockk<ConnectivityObserver>()
            every { connectivity.isOnline } returns flowOf(true)
            val viewModel = SurveyHubViewModel(repository, connectivity)

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
            every { repository.observeUploadingSessions() } returns flowOf(emptyList())
            val connectivity = mockk<ConnectivityObserver>()
            every { connectivity.isOnline } returns flowOf(true)
            val viewModel = SurveyHubViewModel(repository, connectivity)

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
            every { repository.observeUploadingSessions() } returns flowOf(emptyList())
            coEvery { repository.startRoute("WO-1") } returns Result.success(Unit)
            val connectivity = mockk<ConnectivityObserver>()
            every { connectivity.isOnline } returns flowOf(true)
            val viewModel = SurveyHubViewModel(repository, connectivity)

            viewModel.uiState.test {
                awaitItem() // Loading
                awaitItem() // Success (assigned)
                viewModel.onStartRoute("WO-1", SurveyRouteStartDestination.POLE_CAPTURE)
                val starting = awaitItem() as SurveyHubUiState.Success
                assertEquals("WO-1", starting.startingWorkOrderId)
                val success = awaitItem() as SurveyHubUiState.Success
                assertEquals(listOf(startedRoute), success.assignedRoutes)
                assertEquals(null, success.startingWorkOrderId)
            }
        }

    @Test
    fun `a successful onStartRoute emits startedRoute with the given destination`() =
        runTest {
            val startedRoute = route.copy(woStatus = "in_progress")
            val repository = mockk<SurveyRepository>()
            every { repository.observeAssignedRoutes() } returnsMany
                listOf(
                    flowOf(AssignedRoutesResult(listOf(route), isStale = false)),
                    flowOf(AssignedRoutesResult(listOf(startedRoute), isStale = false)),
                )
            every { repository.observeUploadingSessions() } returns flowOf(emptyList())
            coEvery { repository.startRoute("WO-1") } returns Result.success(Unit)
            val connectivity = mockk<ConnectivityObserver>()
            every { connectivity.isOnline } returns flowOf(true)
            val viewModel = SurveyHubViewModel(repository, connectivity)
            // Let init's load() reach Success first - otherwise onStartRoute's own
            // "current !is Success" guard returns early and never calls the repository, same
            // as why the uiState-based tests above drain Loading/Success via awaitItem() first.
            dispatcher.scheduler.advanceUntilIdle()

            viewModel.startedRoute.test {
                viewModel.onStartRoute("WO-1", SurveyRouteStartDestination.READINESS_CHECK)
                val navigation = awaitItem()
                assertEquals("WO-1", navigation.workOrderId)
                assertEquals(SurveyRouteStartDestination.READINESS_CHECK, navigation.destination)
            }
        }

    @Test
    fun `a failed onStartRoute clears startingWorkOrderId and sets startError`() =
        runTest {
            val repository = mockk<SurveyRepository>()
            every { repository.observeAssignedRoutes() } returns flowOf(AssignedRoutesResult(listOf(route), false))
            every { repository.observeUploadingSessions() } returns flowOf(emptyList())
            coEvery { repository.startRoute("WO-1") } returns Result.failure(IllegalStateException("conflict"))
            val connectivity = mockk<ConnectivityObserver>()
            every { connectivity.isOnline } returns flowOf(true)
            val viewModel = SurveyHubViewModel(repository, connectivity)

            viewModel.uiState.test {
                awaitItem() // Loading
                awaitItem() // Success
                viewModel.onStartRoute("WO-1", SurveyRouteStartDestination.POLE_CAPTURE)
                awaitItem() // Success (starting)
                val failed = awaitItem() as SurveyHubUiState.Success
                assertEquals(null, failed.startingWorkOrderId)
                assertEquals("conflict", failed.startError)
            }
        }
}
