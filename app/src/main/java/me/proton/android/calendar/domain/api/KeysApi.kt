package me.proton.android.calendar.domain.api

import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.api.PublicKeysApiResponse
import me.proton.core.domain.entity.UserId

interface KeysApi {
    suspend fun getPublicKeys(userId: UserId, email: String): ApiResponse<PublicKeysApiResponse>
}
