package com.luxmap.feature.profile.data

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakeProfileRepository
    @Inject
    constructor() : ProfileRepository {
        override suspend fun fetchCurrentUser(): Result<CurrentUserProfile> =
            Result.success(
                CurrentUserProfile(
                    fullName = "Nguyễn Văn Bình",
                    email = "binh.nv@luxmap.vn",
                    role = "field_engineer",
                    communeIds = listOf("COM-001", "COM-002"),
                ),
            )
    }
