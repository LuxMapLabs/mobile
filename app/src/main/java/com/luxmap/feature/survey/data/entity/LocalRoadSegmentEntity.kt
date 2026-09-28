package com.luxmap.feature.survey.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "local_road_segment")
data class LocalRoadSegmentEntity(
    @PrimaryKey val roadSegmentId: String,
    val surveySweepId: String,
    val name: String,
    val lengthMeters: Double?,
)
