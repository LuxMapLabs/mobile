package com.luxmap.feature.auth.data

// Why a login failed, so the screen can show the message in the right place: next to a field or
// in a banner. The backend has ONE code (INVALID_CREDENTIALS) for both a wrong username and a
// wrong password on purpose, so the app cannot tell which one was wrong.
enum class LoginFailureReason {
    MissingIdentifier,
    MissingPassword,
    InvalidCredentials,
    AccountLocked,
    Network,
    Unknown,
}

class LoginFailedException(
    val reason: LoginFailureReason,
    message: String,
) : Exception(message)
