package com.luxmap.feature.auth.ui

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.luxmap.BuildConfig
import javax.inject.Inject

sealed interface GoogleSignInOutcome {
    data class Success(val idToken: String, val email: String) : GoogleSignInOutcome

    // The user dismissed the account picker — not an error, the screen just goes back to Idle.
    data object Cancelled : GoogleSignInOutcome

    data class Failed(val message: String) : GoogleSignInOutcome
}

// Thin wrapper around androidx.credentials (Credential Manager) — the only place in the app that
// touches that API. Takes an Activity Context from the call site (LoginScreen's LocalContext)
// rather than an injected ApplicationContext: Credential Manager needs an Activity to host the
// account-picker UI.
class GoogleSignInHelper
    @Inject
    constructor() {
        suspend fun signIn(context: Context): GoogleSignInOutcome {
            val option =
                GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
                    .build()
            val request = GetCredentialRequest.Builder().addCredentialOption(option).build()

            return try {
                val response = CredentialManager.create(context).getCredential(context, request)
                outcomeFor(response.credential)
            } catch (e: GetCredentialCancellationException) {
                GoogleSignInOutcome.Cancelled
            } catch (e: NoCredentialException) {
                GoogleSignInOutcome.Failed("Không tìm thấy tài khoản Google trên thiết bị này.")
            } catch (e: GetCredentialException) {
                GoogleSignInOutcome.Failed(GENERIC_FAILURE_MESSAGE)
            } catch (e: GoogleIdTokenParsingException) {
                GoogleSignInOutcome.Failed(GENERIC_FAILURE_MESSAGE)
            }
        }

        private fun outcomeFor(credential: androidx.credentials.Credential): GoogleSignInOutcome {
            if (credential !is CustomCredential ||
                credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                return GoogleSignInOutcome.Failed(GENERIC_FAILURE_MESSAGE)
            }
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
            return GoogleSignInOutcome.Success(
                idToken = googleIdTokenCredential.idToken,
                email = googleIdTokenCredential.id,
            )
        }

        private companion object {
            const val GENERIC_FAILURE_MESSAGE = "Không thể lấy thông tin đăng nhập Google. Vui lòng thử lại."
        }
    }
