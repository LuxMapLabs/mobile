package com.luxmap.feature.survey.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

// Cache of a route assigned to this Field Engineer by "Kỹ sư bảo trì" (F03) — read from
// GET /api/v1/survey-sweeps/planned, no create/edit on mobile (spec §10).
@Entity(tableName = "local_survey_plan")
data class LocalSurveyPlanEntity(
    @PrimaryKey val surveySweepId: String,
    val assignedByName: String,
    val plannedDate: String,
    // planned / in_progress / pending_upload / submitted / processed — per CLAUDE.md C4 survey_sweep.status
    val status: String,
    val cachedAt: String,
)
