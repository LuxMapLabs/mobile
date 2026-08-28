package com.luxmap.feature.auth.data

import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    suspend fun login(
        identifier: String,
        password: String,
    ): Result<Unit>

    fun observeIsLoggedIn(): Flow<Boolean>
}
