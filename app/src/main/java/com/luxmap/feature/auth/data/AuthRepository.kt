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

    // Called when the app is launched fresh: ends the session if the user did not choose
    // "Duy trì đăng nhập trên thiết bị này". Local only, no API call (the app may be offline).
    suspend fun endSessionIfNotRemembered()

    fun observeIsLoggedIn(): Flow<Boolean>

    // The name used to sign in (null if the session was saved before the app kept it).
    fun observeUsername(): Flow<String?>
}
