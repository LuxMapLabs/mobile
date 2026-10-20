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
    // serverPhotoId is the real backend photo_id (LocalPolePhotoEntity.serverPhotoId), NOT the
    // local row's own photoId (that is just this device's client_op_id).
    suspend fun fetchResult(
        workOrderId: String,
        serverPhotoId: String,
    ): Result<LocalPolePhotoEntity>

    // Room-only read, no network - lets the caller show an already-fetched AI result
    // immediately (offline-first) before/instead of waiting on fetchResult.
    suspend fun cachedResult(serverPhotoId: String): LocalPolePhotoEntity?

    // Field Engineer corrects/confirms the AI's cvState before a Manager decides.
    // finalCvState must be "on" or "off". Server requires reviewNote whenever
    // finalCvState != cvState (including when cvState is null/inconclusive) - a 400
    // HttpException surfaces that, same as 409 POLE_PHOTO_ALREADY_DECIDED once a Manager
    // has already decided. See docs/contract-drift.md "PATCH .../review".
    suspend fun reviewPhoto(
        workOrderId: String,
        serverPhotoId: String,
        finalCvState: String,
        reviewNote: String?,
    ): Result<LocalPolePhotoEntity>
}
