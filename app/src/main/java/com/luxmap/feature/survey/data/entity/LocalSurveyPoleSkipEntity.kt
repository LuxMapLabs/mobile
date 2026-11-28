package com.luxmap.feature.survey.data.entity

import androidx.room.Entity
import java.time.Instant

// A pole the Field Engineer could not photograph on this survey route, with the reason they
// picked. Local only: the backend has no per-pole skip field yet, so the reasons travel in the
// completion report note. Capturing a photo for the pole later deletes its skip.
@Entity(tableName = "local_survey_pole_skip", primaryKeys = ["workOrderId", "poleId"])
data class LocalSurveyPoleSkipEntity(
    val workOrderId: String,
    val poleId: String,
    val reason: String,
    val skippedAt: Instant,
)
