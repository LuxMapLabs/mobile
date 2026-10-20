package com.luxmap.feature.profile.data

interface ProfileRepository {
    suspend fun fetchCurrentUser(): Result<CurrentUserProfile>
}
