package me.proton.android.calendar.domain.api

import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.api.CalendarUserSettingsApiResponse

interface SettingsApi {
    suspend fun getCalendarUserSettings(): ApiResponse<CalendarUserSettingsApiResponse>
}
