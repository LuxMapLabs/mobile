package com.luxmap.feature.survey.data

import com.luxmap.core.theme.AssetCondition
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakeAiClassificationRepository
    @Inject
    constructor() : AiClassificationRepository {
        private val reviews = MutableStateFlow(SAMPLE_REVIEWS)
        val submittedSweepIds = mutableListOf<String>()

        override fun observeReviews(surveySweepId: String): Flow<List<LocalAiClassificationReview>> =
            reviews.map { all -> all.filter { it.surveySweepId == surveySweepId } }

        override suspend fun setFinalClass(
            reviewId: String,
            finalClass: AssetCondition,
            overrideReason: String?,
            overriddenBy: String,
        ) {
            reviews.value =
                reviews.value.map { review ->
                    when {
                        review.reviewId != reviewId -> review
                        finalClass == review.aiClass ->
                            review.copy(
                                finalClass = finalClass,
                                overrideReason = null,
                                overriddenAt = null,
                                overriddenBy = null,
                            )
                        else ->
                            review.copy(
                                finalClass = finalClass,
                                overrideReason = overrideReason,
                                overriddenAt = Instant.now(),
                                overriddenBy = overriddenBy,
                            )
                    }
                }
        }

        override suspend fun submit(surveySweepId: String) {
            submittedSweepIds.add(surveySweepId)
        }

        companion object {
            private val SAMPLE_REVIEWS =
                listOf(
                    LocalAiClassificationReview(
                        reviewId = "REVIEW-1",
                        surveySweepId = "SWEEP-2029",
                        poleId = "POLE-0101",
                        distanceKm = 0.4,
                        frameRef = "Khung hình #012",
                        aiClass = AssetCondition.NORMAL,
                        aiConfidencePercent = 97,
                        finalClass = AssetCondition.NORMAL,
                    ),
                    LocalAiClassificationReview(
                        reviewId = "REVIEW-2",
                        surveySweepId = "SWEEP-2029",
                        poleId = "POLE-0102",
                        distanceKm = 0.8,
                        frameRef = "Khung hình #034",
                        aiClass = AssetCondition.DIM,
                        aiConfidencePercent = 78,
                        finalClass = AssetCondition.DIM,
                    ),
                    LocalAiClassificationReview(
                        reviewId = "REVIEW-3",
                        surveySweepId = "SWEEP-2029",
                        poleId = "POLE-0103",
                        distanceKm = 1.2,
                        frameRef = "Khung hình #061",
                        aiClass = AssetCondition.OUT,
                        aiConfidencePercent = 92,
                        finalClass = AssetCondition.OUT,
                    ),
                    LocalAiClassificationReview(
                        reviewId = "REVIEW-4",
                        surveySweepId = "SWEEP-2029",
                        poleId = "POLE-0104",
                        distanceKm = 1.5,
                        frameRef = "Khung hình #075",
                        aiClass = AssetCondition.DIM,
                        aiConfidencePercent = 61,
                        finalClass = AssetCondition.DIM,
                    ),
                    LocalAiClassificationReview(
                        reviewId = "REVIEW-5",
                        surveySweepId = "SWEEP-2029",
                        poleId = "POLE-0105",
                        distanceKm = 1.9,
                        frameRef = "Khung hình #098",
                        aiClass = AssetCondition.NORMAL,
                        aiConfidencePercent = 95,
                        finalClass = AssetCondition.NORMAL,
                    ),
                )
        }
    }
