package me.proton.android.calendar.domain.api

import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.api.CalendarUserSettingsApiResponse
import me.proton.android.calendar.data.api.ServerEvent
import me.proton.android.calendar.data.api.UserSettingsApiResponse

interface SettingsApi {
    suspend fun getCalendarUserSettings(): ApiResponse<CalendarUserSettingsApiResponse>
    suspend fun getUserSettings(): ApiResponse<UserSettingsApiResponse>
}
