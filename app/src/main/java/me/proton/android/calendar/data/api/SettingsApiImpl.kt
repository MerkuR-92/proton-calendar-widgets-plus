package me.proton.android.calendar.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.proton.android.calendar.data.entity.CalendarUserSettingsEntity
import me.proton.android.calendar.data.entity.UserSettingsEntity
import me.proton.android.calendar.domain.api.SettingsApi
import me.proton.core.domain.entity.UserId
import me.proton.core.network.data.ApiProvider
import me.proton.core.network.data.protonApi.BaseRetrofitApi
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT

interface SettingsApiService : BaseRetrofitApi {
    @GET("settings/calendar")
    suspend fun getCalendarUserSettings(): CalendarUserSettingsApiResponse

    @PUT("settings/calendar")
    suspend fun updateUserPrimaryTimezone(@Body body: UpdateUserPrimaryTimezoneApiRequest): CalendarUserSettingsApiResponse

    @GET("settings")
    suspend fun getUserSettings(): UserSettingsApiResponse
}

class SettingsApiImpl(private val apiProvider: ApiProvider) : SettingsApi {

    override suspend fun getCalendarUserSettings(userId: UserId): ApiResponse<CalendarUserSettingsApiResponse> =
        apiProvider.get<SettingsApiService>(userId).invoke {
            getCalendarUserSettings()
        }.toApiResponse()

    override suspend fun getUserSettings(userId: UserId): ApiResponse<UserSettingsApiResponse> =
        apiProvider.get<SettingsApiService>(userId).invoke {
            getUserSettings()
        }.toApiResponse()

    override suspend fun updateUserPrimaryTimezone(userId: UserId, primaryTimezone: String): ApiResponse<CalendarUserSettingsApiResponse> =
        apiProvider.get<SettingsApiService>(userId).invoke {
            updateUserPrimaryTimezone(
                UpdateUserPrimaryTimezoneApiRequest(primaryTimezone)
            )
        }.toApiResponse()

}

@Serializable
data class CalendarUserSettingsApiResponse(
    @SerialName("CalendarUserSettings")
    val calendarUserSettings: CalendarUserSettingsEntity
)

@Serializable
data class UserSettingsApiResponse(
    @SerialName("UserSettings")
    val userSettings: UserSettingsEntity
)

@Serializable
data class UpdateUserPrimaryTimezoneApiRequest(
    @SerialName("PrimaryTimezone")
    val primaryTimezone: String
)
