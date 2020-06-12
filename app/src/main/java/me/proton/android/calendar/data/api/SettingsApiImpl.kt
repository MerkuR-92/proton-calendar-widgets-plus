package me.proton.android.calendar.data.api

import com.google.gson.Gson
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.api.SettingsApi
import retrofit2.Response
import retrofit2.http.GET

interface SettingsApiService {
    @GET("settings/calendar")
    suspend fun getCalendarUserSettings(): Response<CalendarUserSettingsApiResponse>
}

class SettingsApiImpl(private val service: SettingsApiService, gson: Gson, logger: Logger) : BaseApi(gson, logger),
    SettingsApi {

    override suspend fun getCalendarUserSettings(): ApiResponse<CalendarUserSettingsApiResponse> = safeApiCall { service.getCalendarUserSettings() }

}

data class CalendarUserSettingsApiResponse(
    override val code: Int,
    val calendarUserSettings: CalendarUserSettingsApiEntity
) : BaseApiResponse()

data class CalendarUserSettingsApiEntity(
    val weekStart: Int, // 1 - Monday, 7 - Sunday
    val weekLength: Int, // 0 - 7 days, 1 - 5 days
    val displayWeekNumber: Int, // 0 off, 1 on
    val dateFormat: Int, // 0 (number) - DD/MM/YYYY, 1 (number) - DD/MM/YYYY , 2 (number) - YYYY/MM/DD
    val timeFormat: Int, // 0 (number) - 24h, 1 (number) - 12h
    val autoDetectPrimaryTimezone: Int, // 0 off, 1 on
    val primaryTimezone: String, // "Europe/Budapest", NO LONGER NULL AFTER 12/06/2020: "Can be null if AutoDetectPrimaryTimezone is 0"
    val displaySecondaryTimezone: Int, // 0 off, 1 on
    val secondaryTimezone: String?, // Can be null if DisplaySecondaryTimezone is 0
    val viewPreference: Int, /* 0 - DAILY, 1 - WEEKLY, 2 - MONTHLY, 3 - YEARLY, 4 - PLANNING */
    val defaultCalendarId: String? // TODO we still get null for old accounts (even when web says there is default calendar)
)

