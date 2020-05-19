package me.proton.android.calendar.domain.api

import me.proton.android.calendar.data.api.*

interface AuthenticationApi {
    suspend fun refreshAccessToken(refreshToken: String): ApiResponse<RefreshAccessTokenApiResponse>
    suspend fun fetchLoginInfo(username: String): ApiResponse<LoginInfoApiResponse>
    suspend fun login(
        username: String,
        srpSession: String,
        clientEphemeral: String,
        clientProof: String
    ): ApiResponse<LoginApiResponse>
}
