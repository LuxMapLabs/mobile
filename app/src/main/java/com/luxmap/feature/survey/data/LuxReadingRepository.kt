package com.luxmap.feature.survey.data

import kotlinx.coroutines.flow.Flow
import java.time.Instant

data class LuxReading(
    val clientOpId: String,
    val poleId: String,
    val photoId: String,
    val luxValue: Double,
    val measuredAt: Instant,
    val syncStatus: String?,
)

interface LuxReadingRepository {
    suspend fun record(
        workOrderId: String,
        poleId: String,
        photoId: String,
        luxValue: Double,
        measuredAt: Instant,
    ): LuxReading

    fun observeReadings(workOrderId: String): Flow<List<LuxReading>>
}

fun List<LuxReading>.latestByPole(): Map<String, LuxReading> =
    groupBy { it.poleId }.mapValues { (_, readings) -> readings.maxBy { it.measuredAt } }

fun List<LuxReading>.latestForPhoto(photoId: String): LuxReading? =
    filter { it.photoId == photoId }.maxByOrNull { it.measuredAt }
