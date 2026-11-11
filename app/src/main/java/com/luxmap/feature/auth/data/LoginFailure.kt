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

    // The backend answers an unrecognized/unverified/not-yet-eligible Google account with the
    // same INVALID_CREDENTIALS code as a wrong password (ACCT-4) — but there is no username/
    // password field to blame here, so this gets its own banner-only message instead of being
    // folded into InvalidCredentials.
    GoogleSignInFailed,
}

class LoginFailedException(
    val reason: LoginFailureReason,
    message: String,
) : Exception(message)
