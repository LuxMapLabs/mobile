package com.luxmap.feature.auth.data

import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    suspend fun login(
        identifier: String,
        password: String,
        rememberMe: Boolean,
    ): Result<Unit>

    // Best-effort: always clears the local session even if the logout API call fails
    // (offline) — FM-26 will reuse this function for the Profile screen's logout button,
    // no repository changes needed.
    suspend fun logout(): Result<Unit>

    fun observeIsLoggedIn(): Flow<Boolean>
}
