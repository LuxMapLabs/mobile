package com.luxmap.feature.survey.data

import com.luxmap.core.theme.AssetCondition
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeAiClassificationRepositoryTest {
    @Test
    fun `observeReviews only returns rows for the requested survey sweep`() =
        runTest {
            val repository = FakeAiClassificationRepository()

            val reviews = repository.observeReviews("SWEEP-2029").first()

            assertTrue(reviews.isNotEmpty())
            assertTrue(reviews.all { it.surveySweepId == "SWEEP-2029" })
        }

    @Test
    fun `observeReviews returns an empty list for an unknown survey sweep`() =
        runTest {
            val repository = FakeAiClassificationRepository()

            val reviews = repository.observeReviews("SWEEP-UNKNOWN").first()

            assertTrue(reviews.isEmpty())
        }

    @Test
    fun `setFinalClass with a different class than ai_class records an override`() =
        runTest {
            val repository = FakeAiClassificationRepository()
            val before = repository.observeReviews("SWEEP-2029").first().first { it.aiClass == AssetCondition.DIM }

            repository.setFinalClass(before.reviewId, AssetCondition.OUT, "Nhìn kỹ lại thấy tắt hẳn", "fe_thinh")

            val after = repository.observeReviews("SWEEP-2029").first().first { it.reviewId == before.reviewId }
            assertEquals(AssetCondition.OUT, after.finalClass)
            assertEquals("Nhìn kỹ lại thấy tắt hẳn", after.overrideReason)
            assertEquals("fe_thinh", after.overriddenBy)
            assertTrue(after.overriddenAt != null)
        }

    @Test
    fun `setFinalClass back to the review's own ai_class clears the override`() =
        runTest {
            val repository = FakeAiClassificationRepository()
            val target = repository.observeReviews("SWEEP-2029").first().first { it.aiClass == AssetCondition.DIM }
            repository.setFinalClass(target.reviewId, AssetCondition.OUT, "lý do", "fe_thinh")

            repository.setFinalClass(target.reviewId, target.aiClass, null, "fe_thinh")

            val after = repository.observeReviews("SWEEP-2029").first().first { it.reviewId == target.reviewId }
            assertEquals(target.aiClass, after.finalClass)
            assertNull(after.overrideReason)
            assertNull(after.overriddenAt)
            assertNull(after.overriddenBy)
        }

    @Test
    fun `submit records the survey sweep id`() =
        runTest {
            val repository = FakeAiClassificationRepository()

            repository.submit("SWEEP-2029")

            assertTrue(repository.submittedSweepIds.contains("SWEEP-2029"))
        }
}
