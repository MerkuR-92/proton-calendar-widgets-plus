package me.proton.android.calendar.domain.api

import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.api.UserApiResponse
import me.proton.core.domain.entity.UserId

interface UsersApi {
    suspend fun getUser(userId: UserId): ApiResponse<UserApiResponse>
}
