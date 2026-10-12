package com.luxmap.feature.survey.ui.hub

import app.cash.turbine.test
import com.luxmap.core.network.ConnectivityObserver
import com.luxmap.core.theme.SyncStatus
import com.luxmap.feature.survey.data.AssignedRoadSegment
import com.luxmap.feature.survey.data.AssignedSurveyRoute
import com.luxmap.feature.survey.data.SurveyRepository
import com.luxmap.feature.survey.data.SurveySweepStatus
import com.luxmap.feature.survey.data.dao.SurveySessionDao
import com.luxmap.feature.survey.data.entity.LocalSurveySessionEntity
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
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class SurveyHubViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val route =
        AssignedSurveyRoute(
            surveySweepId = "SWEEP-1",
            assignedByName = "Kỹ sư bảo trì A",
            plannedDate = "2026-10-01",
            status = SurveySweepStatus.PLANNED,
            roadSegments = listOf(AssignedRoadSegment("RS-1", "Đường A", 500.0)),
        )

    private fun queuedSession(sessionId: String) =
        LocalSurveySessionEntity(
            sessionId = sessionId,
            surveySweepId = "SWEEP-2",
            workOrderId = "WO-1",
            recordingState = "packaged",
            syncState = "queued",
            startedAtUtc = Instant.parse("2026-10-01T20:00:00Z"),
            startedAtElapsedNs = 0L,
            endedAtUtc = null,
            durationSeconds = null,
            distanceMeters = null,
            gpsTrackFilePath = null,
            luxLogFilePath = null,
            frameTimestampLogFilePath = null,
            captureConfigFilePath = null,
            manifestFilePath = null,
            packageSchemaVersion = "v0",
            timestampSourceRealtime = true,
            bleGapDetected = false,
            createdAt = Instant.parse("2026-10-01T20:00:00Z"),
            updatedAt = Instant.parse("2026-10-01T20:00:00Z"),
        )

    private fun viewModel(
        routes: List<AssignedSurveyRoute> = emptyList(),
        queuedSessions: List<LocalSurveySessionEntity> = emptyList(),
        isOnline: Boolean = true,
        repositoryFlowThrows: Boolean = false,
    ): SurveyHubViewModel {
        val repository = mockk<SurveyRepository>()
        every { repository.observeAssignedRoutes() } returns
            if (repositoryFlowThrows) {
                flow { throw IllegalStateException("offline") }
            } else {
                flowOf(routes)
            }
        val sessionDao = mockk<SurveySessionDao>()
        every { sessionDao.observeSessionsBySyncState("queued") } returns flowOf(queuedSessions)
        val connectivity = mockk<ConnectivityObserver>()
        every { connectivity.isOnline } returns flowOf(isOnline)
        return SurveyHubViewModel(repository, sessionDao, connectivity)
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
            viewModel(queuedSessions = listOf(queuedSession("SESSION-1"))).uiState.test {
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
            viewModel(queuedSessions = listOf(queuedSession("SESSION-1")), isOnline = false).uiState.test {
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
    fun `emits Error when the assigned-routes flow fails`() =
        runTest {
            viewModel(repositoryFlowThrows = true).uiState.test {
                awaitItem()
                val error = awaitItem() as SurveyHubUiState.Error
                assertEquals("offline", error.message)
            }
        }
}
