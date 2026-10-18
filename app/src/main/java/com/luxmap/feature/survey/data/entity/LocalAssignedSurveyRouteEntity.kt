package com.luxmap.feature.survey.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

// Cache of the last successfully fetched "tuyến được giao" list - lets SurveyHubScreen (and
// SurveyPlanScreen, which reads the same list) still show something (marked isStale) when
// GET /work-orders?task_kind=survey fails offline.
@Entity(tableName = "local_assigned_survey_route")
data class LocalAssignedSurveyRouteEntity(
    @PrimaryKey val workOrderId: String,
    val title: String,
    val communeId: String,
    val woStatus: String,
    val dueDate: String?,
    val scheduledDate: String?,
)
