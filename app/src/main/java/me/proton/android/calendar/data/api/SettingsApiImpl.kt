package me.proton.android.calendar.data.api

import com.google.gson.Gson
import me.proton.android.calendar.data.entity.CalendarUserSettingsEntity
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
    val calendarUserSettings: CalendarUserSettingsEntity
) : BaseApiResponse()


