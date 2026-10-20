package com.luxmap.feature.profile.data

import com.luxmap.core.network.UserApi
import javax.inject.Inject
import javax.inject.Singleton

// Calls the real GET /auth/me (see UserApi) - same endpoint RealHomeRepository/RealSurveyRepository
// already use for the caller's user_id, read here instead for the display fields (BE-07 contract).
@Singleton
class RealProfileRepository
    @Inject
    constructor(
        private val userApi: UserApi,
    ) : ProfileRepository {
        override suspend fun fetchCurrentUser(): Result<CurrentUserProfile> =
            runCatching {
                val dto = userApi.me()
                CurrentUserProfile(
                    fullName = dto.fullName,
                    email = dto.email,
                    role = dto.role,
                    communeIds = dto.communeIds,
                )
            }
    }
