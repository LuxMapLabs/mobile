package com.luxmap.feature.survey.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakeUploadRepository
    @Inject
    constructor() : UploadRepository {
        private val resumePoints = mutableMapOf<String, Long>()
        private val totalBytes = 50_000_000L
        private val chunkBytes = 10_000_000L

        // Simulates an interrupted upload for tests/manual QA of the resume path.
        fun markInterruptedAt(
            sessionId: String,
            bytesSent: Long,
        ) {
            resumePoints[sessionId] = bytesSent
        }

        override fun uploadSession(sessionId: String): Flow<UploadProgress> =
            flow {
                var bytesSent = resumePoints[sessionId] ?: 0L
                while (bytesSent < totalBytes) {
                    delay(10)
                    bytesSent = (bytesSent + chunkBytes).coerceAtMost(totalBytes)
                    resumePoints[sessionId] = bytesSent
                    emit(UploadProgress.InProgress(bytesSent, totalBytes))
                }
                emit(UploadProgress.Done)
            }
    }
