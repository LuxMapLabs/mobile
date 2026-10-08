package com.luxmap.feature.survey.ui.submit

import app.cash.turbine.test
import com.luxmap.core.network.ConnectivityObserver
import com.luxmap.feature.survey.data.UploadProgress
import com.luxmap.feature.survey.data.UploadRepository
import com.luxmap.feature.survey.data.dao.SurveySessionDao
import com.luxmap.feature.survey.data.entity.LocalSurveySessionEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

private fun session(recordingState: String) =
    LocalSurveySessionEntity(
        sessionId = "SESSION-1",
        surveySweepId = "SWEEP-1",
        workOrderId = "WO-1",
        recordingState = recordingState,
        syncState = null,
        startedAtUtc = Instant.parse("2026-10-08T10:00:00Z"),
        startedAtElapsedNs = 0L,
        endedAtUtc = null,
        durationSeconds = null,
        distanceMeters = null,
        gpsTrackFilePath = null,
        luxLogFilePath = null,
        frameTimestampLogFilePath = null,
        captureConfigFilePath = null,
        manifestFilePath = null,
        packageSchemaVersion = "v1",
        timestampSourceRealtime = true,
        bleGapDetected = false,
        createdAt = Instant.parse("2026-10-08T10:00:00Z"),
        updatedAt = Instant.parse("2026-10-08T10:00:00Z"),
    )

@OptIn(ExperimentalCoroutinesApi::class)
class SubmitViewModelTest {
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
    fun `a session that is not packaged yet shows NotPackagedYet and onSubmit is a no-op`() =
        runTest {
            val repository = mockk<UploadRepository>()
            val sessionDao = mockk<SurveySessionDao>()
            coEvery { sessionDao.sessionById("SESSION-1") } returns session(recordingState = "recording")
            val connectivity = mockk<ConnectivityObserver>()
            every { connectivity.isOnline } returns flowOf(true)
            val viewModel = SubmitViewModel(repository, sessionDao, connectivity)

            viewModel.uiState.test {
                assertTrue(awaitItem() is SubmitUiState.Idle)
                viewModel.onSubmit("SESSION-1")
                assertTrue(awaitItem() is SubmitUiState.NotPackagedYet)
            }
            coVerify(exactly = 0) { repository.uploadSession(any()) }
        }

    @Test
    fun `a Conflict from the repository maps to SubmitUiState Conflict`() =
        runTest {
            val repository = mockk<UploadRepository>()
            val sessionDao = mockk<SurveySessionDao>()
            coEvery { sessionDao.sessionById("SESSION-1") } returns session(recordingState = "packaged")
            every { repository.uploadSession("SESSION-1") } returns flowOf(UploadProgress.Conflict("x"))
            val connectivity = mockk<ConnectivityObserver>()
            every { connectivity.isOnline } returns flowOf(true)
            val viewModel = SubmitViewModel(repository, sessionDao, connectivity)

            viewModel.uiState.test {
                assertTrue(awaitItem() is SubmitUiState.Idle)
                viewModel.onSubmit("SESSION-1")
                assertTrue(awaitItem() is SubmitUiState.Conflict)
            }
        }

    @Test
    fun `moves from Idle to Uploading to Done as the repository flow progresses`() =
        runTest {
            val repository = mockk<UploadRepository>()
            val sessionDao = mockk<SurveySessionDao>()
            coEvery { sessionDao.sessionById("SESSION-1") } returns session(recordingState = "packaged")
            every { repository.uploadSession("SESSION-1") } returns
                flowOf(UploadProgress.InProgress(10_000L, 50_000L), UploadProgress.Done)
            val connectivity = mockk<ConnectivityObserver>()
            every { connectivity.isOnline } returns flowOf(true)
            val viewModel = SubmitViewModel(repository, sessionDao, connectivity)

            viewModel.uiState.test {
                assertEquals(SubmitUiState.Idle, awaitItem())
                viewModel.onSubmit("SESSION-1")
                assertEquals(SubmitUiState.Uploading(10_000L, 50_000L, isOffline = false), awaitItem())
                assertEquals(SubmitUiState.Done, awaitItem())
            }
        }

    @Test
    fun `a Failed from the repository maps to SubmitUiState Failed`() =
        runTest {
            val repository = mockk<UploadRepository>()
            val sessionDao = mockk<SurveySessionDao>()
            coEvery { sessionDao.sessionById("SESSION-1") } returns session(recordingState = "packaged")
            every { repository.uploadSession("SESSION-1") } returns flowOf(UploadProgress.Failed("boom"))
            val connectivity = mockk<ConnectivityObserver>()
            every { connectivity.isOnline } returns flowOf(true)
            val viewModel = SubmitViewModel(repository, sessionDao, connectivity)

            viewModel.uiState.test {
                assertTrue(awaitItem() is SubmitUiState.Idle)
                viewModel.onSubmit("SESSION-1")
                assertEquals(SubmitUiState.Failed("boom"), awaitItem())
            }
        }
}
