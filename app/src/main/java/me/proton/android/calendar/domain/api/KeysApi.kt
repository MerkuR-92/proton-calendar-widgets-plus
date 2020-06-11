package me.proton.android.calendar.domain.api

import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.api.KeySaltsApiResponse
import me.proton.android.calendar.data.api.PublicKeysApiResponse

interface KeysApi {
    suspend fun getKeySalts(): ApiResponse<KeySaltsApiResponse>
    suspend fun getPublicKeys(email: String): ApiResponse<PublicKeysApiResponse>
}
