package com.luxmap.feature.profile.data

data class CurrentUserProfile(
    val fullName: String,
    val email: String,
    val role: String,
    val communeIds: List<String>,
)
