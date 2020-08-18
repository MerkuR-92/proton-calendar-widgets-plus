package me.proton.android.calendar.data.api

import com.google.gson.Gson
import me.proton.android.calendar.data.entity.UserSettingsEntity
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.api.SettingsApi
import retrofit2.Response
import retrofit2.http.GET

interface SettingsApiService {
    @GET("settings/calendar")
    suspend fun getCalendarUserSettings(): Response<UserSettingsApiResponse>
}

class SettingsApiImpl(private val service: SettingsApiService, gson: Gson, logger: Logger) : BaseApi(gson, logger),
    SettingsApi {

    override suspend fun getUserSettings(): ApiResponse<UserSettingsApiResponse> = safeApiCall { service.getCalendarUserSettings() }

}

data class UserSettingsApiResponse(
    override val code: Int,
    val calendarUserSettings: UserSettingsEntity
) : BaseApiResponse()


