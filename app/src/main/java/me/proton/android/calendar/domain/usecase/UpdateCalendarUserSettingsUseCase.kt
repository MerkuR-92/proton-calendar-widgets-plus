package me.proton.android.calendar.domain.usecase

import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.CalendarUserSettingsEntity
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.api.SettingsApi
import me.proton.core.domain.entity.UserId

class UpdateCalendarUserSettingsUseCase(
    private val logger: Logger,
    private val settingsApi: SettingsApi,
    private val calendarUserSettingsChangedUseCase: CalendarUserSettingsChangedUseCase
): UseCase {

    companion object {
        const val WORKER_ID = "UPDATE_CALENDAR_USER_SETTINGS"
    }

    suspend fun execute(userId: UserId, primaryTimezone: String): UseCase.Result {
        return when (val updateCalendarUserPrimaryTimezoneResponse =
            settingsApi.updateCalendarUserPrimaryTimezone(userId, primaryTimezone)) {
            is ApiResponse.Success -> {
                calendarUserSettingsChangedUseCase.execute(userId.id, updateCalendarUserPrimaryTimezoneResponse.data.calendarUserSettings)
                UseCase.Result.Success
            }
            is ApiResponse.Error -> {
                logger.e("api error updating user timezone: $updateCalendarUserPrimaryTimezoneResponse")
                UseCase.Result.Error(updateCalendarUserPrimaryTimezoneResponse.error)
            }
            is ApiResponse.Exception -> {
                logger.e("api error updating user timezone: $updateCalendarUserPrimaryTimezoneResponse")
                UseCase.Result.Error(updateCalendarUserPrimaryTimezoneResponse.exception.message ?: "(no exception message)")
            }
        }
    }
}
