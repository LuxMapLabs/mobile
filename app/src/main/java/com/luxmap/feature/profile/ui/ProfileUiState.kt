package com.luxmap.feature.profile.ui

import com.luxmap.feature.profile.data.CurrentUserProfile

// No Empty variant - "my own profile" has no empty case, only loading/success/error.
sealed interface ProfileUiState {
    data object Loading : ProfileUiState

    data class Success(val profile: CurrentUserProfile) : ProfileUiState

    data class Error(val message: String) : ProfileUiState
}
