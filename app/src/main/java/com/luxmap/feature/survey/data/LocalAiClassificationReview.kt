package com.luxmap.feature.survey.data

import com.luxmap.core.theme.AssetCondition
import java.time.Instant

// Local-only placeholder model, not a real backend entity - the AI classification review
// contract has no confirmed sweep-status/field schema yet (see docs/contract-drift.md and the
// redesign roadmap's decision #2). Rewire to a real entity once WP2/WP5 confirm the schema.
data class LocalAiClassificationReview(
    val reviewId: String,
    val surveySweepId: String,
    val poleId: String,
    val distanceKm: Double,
    val frameRef: String,
    val aiClass: AssetCondition,
    val aiConfidencePercent: Int,
    val finalClass: AssetCondition,
    val overrideReason: String? = null,
    val overriddenAt: Instant? = null,
    val overriddenBy: String? = null,
)
