package me.proton.android.calendar.domain.api

import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.api.KeySaltsApiResponse
import me.proton.android.calendar.data.api.PublicKeysApiResponse
import me.proton.core.domain.entity.UserId

interface KeysApi {
    suspend fun getKeySalts(userId: UserId): ApiResponse<KeySaltsApiResponse>
    suspend fun getPublicKeys(userId: UserId, email: String): ApiResponse<PublicKeysApiResponse>
}
