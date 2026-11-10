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

    // Same shape as capture(), for a recorded video clip instead of a still photo. Writes
    // mediaType = "video" and enqueues "upload_pole_video" instead of "upload_pole_photo".
    suspend fun captureVideo(
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

    // Saves the Field Engineer's correction/agreement on cvState locally only — nothing is sent
    // to the server here. See RealPolePhotoRepository.enqueuePendingReviews for when it is sent.
    suspend fun saveDraftReview(
        serverPhotoId: String,
        finalCvState: String,
        reviewNote: String?,
    )

    // Scans this work order's photos for an unsent draft review and enqueues one
    // "submit_pole_photo_review" sync op per draft found. Returns how many were enqueued, so the
    // caller (PoleListViewModel.onComplete) can report it. Called exactly once, when "Hoàn tất"
    // is tapped — never at saveDraftReview time, so a draft is never sent before that.
    suspend fun enqueuePendingReviews(workOrderId: String): Int
}
