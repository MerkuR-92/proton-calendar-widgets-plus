package me.proton.android.calendar.domain.api

import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.api.CalendarUserSettingsApiResponse
import me.proton.android.calendar.data.api.UserSettingsApiResponse
import me.proton.core.domain.entity.UserId

interface SettingsApi {
    suspend fun getCalendarUserSettings(userId: UserId): ApiResponse<CalendarUserSettingsApiResponse>
    suspend fun getUserSettings(userId: UserId): ApiResponse<UserSettingsApiResponse>
}
