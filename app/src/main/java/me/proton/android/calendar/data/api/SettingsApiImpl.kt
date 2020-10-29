package me.proton.android.calendar.data.api

import com.google.gson.Gson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.proton.android.calendar.data.entity.CalendarUserSettingsEntity
import me.proton.android.calendar.data.entity.UserSettingsEntity
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.api.SettingsApi
import retrofit2.Response
import retrofit2.http.GET

interface SettingsApiService {
    @GET("settings/calendar")
    suspend fun getCalendarUserSettings(): Response<CalendarUserSettingsApiResponse>

    @GET("settings")
    suspend fun getUserSettings(): Response<UserSettingsApiResponse>
}

class SettingsApiImpl(private val service: SettingsApiService, gson: Gson, logger: Logger) : BaseApi(gson, logger),
    SettingsApi {

    override suspend fun getCalendarUserSettings(): ApiResponse<CalendarUserSettingsApiResponse> = safeApiCall { service.getCalendarUserSettings() }

    override suspend fun getUserSettings(): ApiResponse<UserSettingsApiResponse> = safeApiCall { service.getUserSettings() }

}

@Serializable
data class CalendarUserSettingsApiResponse(
    @SerialName("Code")
    override val code: Int,
    @SerialName("CalendarUserSettings")
    val calendarUserSettings: CalendarUserSettingsEntity
) : BaseApiResponse()

@Serializable
data class UserSettingsApiResponse(
    @SerialName("Code")
    override val code: Int,
    @SerialName("UserSettings")
    val userSettings: UserSettingsEntity
) : BaseApiResponse()


