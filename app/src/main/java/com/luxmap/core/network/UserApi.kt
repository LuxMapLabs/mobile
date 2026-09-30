package com.luxmap.core.network

import com.luxmap.feature.auth.data.CurrentUserResponseDto
import retrofit2.http.GET

// Separate from AuthApi on purpose: AuthApi (login/refresh/logout) is bound to the PLAIN
// Retrofit client (no Authorization header, no Authenticator - see ApiClient.kt). GET
// /auth/me needs a Bearer token, so it is bound to the normal authenticated client instead,
// the same one every other feature API uses.
interface UserApi {
    // Contract section 4.7 - reads the signed-in user from the database, not from the JWT
    // claims (see CurrentUserResponseDto). Used to get the caller's own user_id for filters
    // like GET /work-orders?assigned_to=.
    @GET("api/v1/auth/me")
    suspend fun me(): CurrentUserResponseDto
}
