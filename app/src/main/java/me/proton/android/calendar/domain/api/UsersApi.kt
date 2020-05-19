package me.proton.android.calendar.domain.api

import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.api.UserApiResponse

interface UsersApi {
    suspend fun getUser(): ApiResponse<UserApiResponse>
}
