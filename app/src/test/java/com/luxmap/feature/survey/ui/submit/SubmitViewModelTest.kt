package com.luxmap.feature.survey.ui.submit

import app.cash.turbine.test
import com.luxmap.feature.survey.data.UploadProgress
import com.luxmap.feature.survey.data.UploadRepository
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
import org.junit.Before
import org.junit.Test

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
    fun `moves from Idle to Uploading to Done as the repository flow progresses`() =
        runTest {
            val repository = mockk<UploadRepository>()
            every { repository.uploadSession("SESSION-1") } returns
                flowOf(UploadProgress.InProgress(10_000L, 50_000L), UploadProgress.Done)
            val viewModel = SubmitViewModel(repository)

            viewModel.uiState.test {
                assertEquals(SubmitUiState.Idle, awaitItem())
                viewModel.onSubmit("SESSION-1")
                assertEquals(SubmitUiState.Uploading(10_000L, 50_000L), awaitItem())
                assertEquals(SubmitUiState.Done, awaitItem())
            }
        }
}
