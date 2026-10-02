package com.luxmap.feature.survey.ui.coverage

import app.cash.turbine.test
import com.luxmap.feature.survey.data.SurveyRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CoverageViewModelTest {
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
    fun `loading segments with at least one file emits Success`() =
        runTest {
            val repository = mockk<SurveyRepository>()
            coEvery { repository.segmentFilePathsFor("S1") } returns listOf("/data/segment_0.mp4")
            val viewModel = CoverageViewModel(repository)

            viewModel.uiState.test {
                assertEquals(CoverageUiState.Loading, awaitItem())
                viewModel.loadSegments("S1")
                assertEquals(CoverageUiState.Success(listOf("/data/segment_0.mp4")), awaitItem())
            }
        }

    @Test
    fun `loading segments with an empty list emits Empty`() =
        runTest {
            val repository = mockk<SurveyRepository>()
            coEvery { repository.segmentFilePathsFor("S1") } returns emptyList()
            val viewModel = CoverageViewModel(repository)

            viewModel.uiState.test {
                assertEquals(CoverageUiState.Loading, awaitItem())
                viewModel.loadSegments("S1")
                assertEquals(CoverageUiState.Empty, awaitItem())
            }
        }

    @Test
    fun `a repository failure emits Error`() =
        runTest {
            val repository = mockk<SurveyRepository>()
            coEvery { repository.segmentFilePathsFor("S1") } throws IllegalStateException("no such session")
            val viewModel = CoverageViewModel(repository)

            viewModel.uiState.test {
                assertEquals(CoverageUiState.Loading, awaitItem())
                viewModel.loadSegments("S1")
                val error = awaitItem() as CoverageUiState.Error
                assertEquals("no such session", error.message)
            }
        }

    @Test
    fun `a player error sets the flag on the existing Success state instead of leaving it`() =
        runTest {
            val repository = mockk<SurveyRepository>()
            coEvery { repository.segmentFilePathsFor("S1") } returns listOf("/data/segment_0.mp4")
            val viewModel = CoverageViewModel(repository)

            viewModel.uiState.test {
                awaitItem() // Loading
                viewModel.loadSegments("S1")
                awaitItem() // Success, no error yet

                viewModel.onPlayerError("file missing")
                val withError = awaitItem() as CoverageUiState.Success
                assertEquals("file missing", withError.playerErrorMessage)
            }
        }

    @Test
    fun `confirming redo discards the session and emits the survey sweep id`() =
        runTest {
            val repository = mockk<SurveyRepository>()
            coEvery { repository.discardSession("S1") } returns "SWEEP-1"
            val viewModel = CoverageViewModel(repository)

            viewModel.redoCompleted.test {
                viewModel.onRedoConfirmed("S1")
                assertEquals("SWEEP-1", awaitItem())
            }
        }
}
