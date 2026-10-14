package com.luxmap.feature.survey.ui.plan

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.luxmap.feature.survey.data.AssignedSurveyRoute
import com.luxmap.feature.survey.data.SurveyRepository
import com.luxmap.feature.survey.domain.SurveyReadinessInputProvider
import com.luxmap.feature.survey.domain.usecase.CheckSurveyReadinessUseCase
import com.luxmap.feature.survey.domain.usecase.SurveyReadinessInput
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
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SurveyPlanViewModelTest {
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

    private fun savedStateHandle(workOrderId: String = "WO-1") = SavedStateHandle(mapOf("workOrderId" to workOrderId))

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `emits Empty when the work order is not among the assigned routes`() =
        runTest {
            val repository = mockk<SurveyRepository>()
            every { repository.observeAssignedRoutes() } returns flowOf(emptyList())
            val viewModel =
                SurveyPlanViewModel(savedStateHandle(), repository, mockk(relaxed = true), mockk(relaxed = true))

            viewModel.uiState.test {
                assertEquals(SurveyPlanUiState.Loading, awaitItem())
                assertEquals(SurveyPlanUiState.Empty, awaitItem())
            }
        }

    @Test
    fun `emits Success with the matching route when found`() =
        runTest {
            val repository = mockk<SurveyRepository>()
            every { repository.observeAssignedRoutes() } returns flowOf(listOf(route))
            val viewModel =
                SurveyPlanViewModel(savedStateHandle(), repository, mockk(relaxed = true), mockk(relaxed = true))

            viewModel.uiState.test {
                assertEquals(SurveyPlanUiState.Loading, awaitItem())
                val success = awaitItem() as SurveyPlanUiState.Success
                assertEquals(route, success.route)
            }
        }

    @Test
    fun `emits Error when the repository flow fails`() =
        runTest {
            val repository = mockk<SurveyRepository>()
            every { repository.observeAssignedRoutes() } returns flow { throw IllegalStateException("offline") }
            val viewModel =
                SurveyPlanViewModel(savedStateHandle(), repository, mockk(relaxed = true), mockk(relaxed = true))

            viewModel.uiState.test {
                assertEquals(SurveyPlanUiState.Loading, awaitItem())
                val error = awaitItem() as SurveyPlanUiState.Error
                assertEquals("offline", error.message)
            }
        }

    @Test
    fun `rejectionReason is always null while SHOW_RESURVEY_BANNER is off`() =
        runTest {
            val repository = mockk<SurveyRepository>()
            every { repository.observeAssignedRoutes() } returns flowOf(listOf(route))
            val viewModel =
                SurveyPlanViewModel(savedStateHandle(), repository, mockk(relaxed = true), mockk(relaxed = true))

            viewModel.uiState.test {
                awaitItem() // Loading
                val success = awaitItem() as SurveyPlanUiState.Success
                assertEquals(null, success.rejectionReason)
            }
        }

    @Test
    fun `loading a route with a non-REALTIME timestamp source surfaces a not-ready readiness result`() =
        runTest {
            val repository = mockk<SurveyRepository>()
            every { repository.observeAssignedRoutes() } returns flowOf(listOf(route))
            val readinessInputProvider = mockk<SurveyReadinessInputProvider>()
            coEvery { readinessInputProvider.gather() } returns
                SurveyReadinessInput(
                    cameraPermissionGranted = true,
                    exposureLockSupported = true,
                    timestampSourceRealtime = false,
                    gpsAvailable = true,
                    freeStorageBytes = 2_000_000_000L,
                    requiredStorageBytes = 1_000_000_000L,
                    batteryPercent = 80,
                )
            val viewModel =
                SurveyPlanViewModel(
                    savedStateHandle(),
                    repository,
                    readinessInputProvider,
                    CheckSurveyReadinessUseCase(),
                )

            viewModel.uiState.test {
                awaitItem() // Loading
                awaitItem() // Success, readiness null - checkReadiness() runs right after
                val withReadiness = awaitItem() as SurveyPlanUiState.Success
                assertFalse(requireNotNull(withReadiness.readiness).isReady)
                assertFalse(withReadiness.readiness!!.timestampSourceRealtime)
            }
        }
}
