package com.luxmap.feature.survey.ui.aiconfirm

import app.cash.turbine.test
import com.luxmap.core.theme.AssetCondition
import com.luxmap.feature.auth.data.AuthRepository
import com.luxmap.feature.survey.data.AiClassificationRepository
import com.luxmap.feature.survey.data.LocalAiClassificationReview
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private fun review(
    reviewId: String,
    aiClass: AssetCondition,
    finalClass: AssetCondition = aiClass,
    aiConfidencePercent: Int = 90,
    overrideReason: String? = null,
) = LocalAiClassificationReview(
    reviewId = reviewId,
    surveySweepId = "SWEEP-1",
    poleId = "POLE-$reviewId",
    distanceKm = 1.0,
    frameRef = "Khung hình #$reviewId",
    aiClass = aiClass,
    aiConfidencePercent = aiConfidencePercent,
    finalClass = finalClass,
    overrideReason = overrideReason,
)

@OptIn(ExperimentalCoroutinesApi::class)
class AiConfirmationViewModelTest {
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
    fun `load emits Success with reviews and condition counts based on final class`() =
        runTest {
            val repository = mockk<AiClassificationRepository>()
            every { repository.observeReviews("SWEEP-1") } returns
                MutableStateFlow(listOf(review("1", AssetCondition.NORMAL), review("2", AssetCondition.DIM)))
            val viewModel = AiConfirmationViewModel(repository, mockk())

            viewModel.uiState.test {
                assertEquals(AiConfirmationUiState.Loading, awaitItem())
                viewModel.load("SWEEP-1")
                val success = awaitItem() as AiConfirmationUiState.Success
                assertEquals(2, success.reviews.size)
                assertEquals(1, success.normalCount)
                assertEquals(1, success.dimCount)
                assertEquals(0, success.outCount)
            }
        }

    @Test
    fun `an empty review list emits Empty, not an empty Success list`() =
        runTest {
            val repository = mockk<AiClassificationRepository>()
            every { repository.observeReviews("SWEEP-1") } returns MutableStateFlow(emptyList())
            val viewModel = AiConfirmationViewModel(repository, mockk())

            viewModel.uiState.test {
                assertEquals(AiConfirmationUiState.Loading, awaitItem())
                viewModel.load("SWEEP-1")
                assertEquals(AiConfirmationUiState.Empty, awaitItem())
            }
        }

    @Test
    fun `a repository failure emits Error`() =
        runTest {
            val repository = mockk<AiClassificationRepository>()
            every { repository.observeReviews("SWEEP-1") } returns flow { throw IllegalStateException("boom") }
            val viewModel = AiConfirmationViewModel(repository, mockk())

            viewModel.uiState.test {
                assertEquals(AiConfirmationUiState.Loading, awaitItem())
                viewModel.load("SWEEP-1")
                assertTrue(awaitItem() is AiConfirmationUiState.Error)
            }
        }

    @Test
    fun `NEEDS_REVIEW filter includes a NORMAL review that was overridden, not only non-NORMAL ai_class`() =
        runTest {
            val repository = mockk<AiClassificationRepository>()
            every { repository.observeReviews("SWEEP-1") } returns
                MutableStateFlow(
                    listOf(
                        review("1", aiClass = AssetCondition.NORMAL),
                        review(
                            "2",
                            aiClass = AssetCondition.NORMAL,
                            finalClass = AssetCondition.DIM,
                            overrideReason = "sửa",
                        ),
                        review("3", aiClass = AssetCondition.OUT),
                    ),
                )
            val viewModel = AiConfirmationViewModel(repository, mockk())

            viewModel.uiState.test {
                awaitItem()
                viewModel.load("SWEEP-1")
                awaitItem()
                viewModel.onFilterSelected(ReviewFilter.NEEDS_REVIEW)
                val success = awaitItem() as AiConfirmationUiState.Success
                assertEquals(setOf("2", "3"), success.reviews.map { it.reviewId }.toSet())
            }
        }

    @Test
    fun `OVERRIDDEN filter only includes reviews with a non-null override reason`() =
        runTest {
            val repository = mockk<AiClassificationRepository>()
            every { repository.observeReviews("SWEEP-1") } returns
                MutableStateFlow(
                    listOf(
                        review("1", aiClass = AssetCondition.OUT),
                        review(
                            "2",
                            aiClass = AssetCondition.NORMAL,
                            finalClass = AssetCondition.DIM,
                            overrideReason = "sửa",
                        ),
                    ),
                )
            val viewModel = AiConfirmationViewModel(repository, mockk())

            viewModel.uiState.test {
                awaitItem()
                viewModel.load("SWEEP-1")
                awaitItem()
                viewModel.onFilterSelected(ReviewFilter.OVERRIDDEN)
                val success = awaitItem() as AiConfirmationUiState.Success
                assertEquals(listOf("2"), success.reviews.map { it.reviewId })
            }
        }

    @Test
    fun `selecting a review opens the sheet seeded from its current final class and reason`() =
        runTest {
            val viewModel = AiConfirmationViewModel(mockk(), mockk())
            val target =
                review("1", aiClass = AssetCondition.NORMAL, finalClass = AssetCondition.DIM, overrideReason = "sửa")

            viewModel.sheet.test {
                assertNull(awaitItem())
                viewModel.onReviewSelected(target)
                val sheet = awaitItem()
                assertEquals(AssetCondition.DIM, sheet?.chosenClass)
                assertEquals("sửa", sheet?.reason)
            }
        }

    @Test
    fun `saving the same class as ai_class clears the override`() =
        runTest {
            val repository = mockk<AiClassificationRepository>(relaxed = true)
            val authRepository = mockk<AuthRepository>()
            every { authRepository.observeUsername() } returns flowOf("fe_thinh")
            val viewModel = AiConfirmationViewModel(repository, authRepository)
            val target =
                review("1", aiClass = AssetCondition.NORMAL, finalClass = AssetCondition.DIM, overrideReason = "sửa")
            viewModel.onReviewSelected(target)
            viewModel.onConditionChosen(AssetCondition.NORMAL)

            viewModel.onSaveSheet()
            advanceUntilIdle()

            coVerify {
                repository.setFinalClass(
                    reviewId = "1",
                    finalClass = AssetCondition.NORMAL,
                    overrideReason = null,
                    overriddenBy = "fe_thinh",
                )
            }
            assertNull(viewModel.sheet.value)
        }

    @Test
    fun `saving a different class without a reason is a no-op`() =
        runTest {
            val repository = mockk<AiClassificationRepository>(relaxed = true)
            val viewModel = AiConfirmationViewModel(repository, mockk())
            viewModel.onReviewSelected(review("1", aiClass = AssetCondition.NORMAL))
            viewModel.onConditionChosen(AssetCondition.OUT)
            viewModel.onReasonChanged("   ")

            viewModel.onSaveSheet()
            advanceUntilIdle()

            coVerify(exactly = 0) { repository.setFinalClass(any(), any(), any(), any()) }
            assertTrue(viewModel.sheet.value != null)
        }

    @Test
    fun `saving a different class with a reason persists the override`() =
        runTest {
            val repository = mockk<AiClassificationRepository>(relaxed = true)
            val authRepository = mockk<AuthRepository>()
            every { authRepository.observeUsername() } returns flowOf("fe_thinh")
            val viewModel = AiConfirmationViewModel(repository, authRepository)
            viewModel.onReviewSelected(review("1", aiClass = AssetCondition.NORMAL))
            viewModel.onConditionChosen(AssetCondition.OUT)
            viewModel.onReasonChanged("Nhìn kỹ lại thấy tắt hẳn")

            viewModel.onSaveSheet()
            advanceUntilIdle()

            coVerify {
                repository.setFinalClass(
                    reviewId = "1",
                    finalClass = AssetCondition.OUT,
                    overrideReason = "Nhìn kỹ lại thấy tắt hẳn",
                    overriddenBy = "fe_thinh",
                )
            }
        }

    @Test
    fun `onSubmit forwards the sweep id to the repository`() =
        runTest {
            val repository = mockk<AiClassificationRepository>(relaxed = true)
            val viewModel = AiConfirmationViewModel(repository, mockk())

            viewModel.onSubmit("SWEEP-1")
            advanceUntilIdle()

            coVerify { repository.submit("SWEEP-1") }
        }
}
