package com.luxmap.feature.survey.data

import com.luxmap.feature.survey.data.entity.LocalPolePhotoEntity
import kotlinx.coroutines.flow.Flow

interface PolePhotoRepository {
    fun observePhotos(workOrderId: String): Flow<List<LocalPolePhotoEntity>>

    fun observeUploadStatuses(photoIds: List<String>): Flow<Map<String, String>>

    suspend fun capture(
        workOrderId: String,
        poleId: String,
        filePath: String,
        lat: Double,
        lng: Double,
    ): LocalPolePhotoEntity
}
