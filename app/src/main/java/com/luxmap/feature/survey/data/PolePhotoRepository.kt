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

    // Fetches the AI classification result for an already-uploaded photo and persists it
    // into Room, so it is still visible offline next time this photo is viewed.
    suspend fun fetchResult(
        workOrderId: String,
        photoId: String,
    ): Result<LocalPolePhotoEntity>
}
