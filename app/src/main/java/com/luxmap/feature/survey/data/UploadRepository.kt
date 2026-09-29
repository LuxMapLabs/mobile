package com.luxmap.feature.survey.data

import kotlinx.coroutines.flow.Flow

sealed interface UploadProgress {
    data class InProgress(val bytesSent: Long, val totalBytes: Long) : UploadProgress

    data object Done : UploadProgress

    data class Failed(val reason: String) : UploadProgress
}

// No real endpoint yet (spec §13/§14) — swapped for RealUploadRepository once Backend
// confirms one, without touching UI/ViewModel (RepositoryModule is the only wiring point).
interface UploadRepository {
    fun uploadSession(sessionId: String): Flow<UploadProgress>
}
