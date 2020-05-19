package me.proton.android.calendar.domain.api

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName
import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.api.CalendarUserSettingsApiResponse
import me.proton.android.calendar.data.api.UserApiResponse

interface SettingsApi {
    suspend fun getCalendarUserSettings(): ApiResponse<CalendarUserSettingsApiResponse>
}
