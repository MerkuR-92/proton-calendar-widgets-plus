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

    suspend fun execute(userId: UserId, primaryTimezone: String? = null, autoDetectPrimaryTimezone: Int? = null): UseCase.Result {
        return when (val updateCalendarUserSettingsResponse =
            when {
                primaryTimezone != null -> settingsApi.updateCalendarUserPrimaryTimezone(userId, primaryTimezone)
                autoDetectPrimaryTimezone != null -> settingsApi.updateCalendarUserAutoDetectTimezone(userId, autoDetectPrimaryTimezone)
                else -> return UseCase.Result.Error("api error updating calendar user settings: missing parameter")
            }
        ) {
            is ApiResponse.Success -> {
                calendarUserSettingsChangedUseCase.execute(userId.id, updateCalendarUserSettingsResponse.data.calendarUserSettings)
                UseCase.Result.Success
            }
            is ApiResponse.Error -> {
                logger.e("api error updating calendar user settings: $updateCalendarUserSettingsResponse")
                UseCase.Result.Error(updateCalendarUserSettingsResponse.error)
            }
            is ApiResponse.Exception -> {
                logger.e("api error updating calendar user settings: $updateCalendarUserSettingsResponse")
                UseCase.Result.Error(updateCalendarUserSettingsResponse.exception.message ?: "(no exception message)")
            }
        }
    }
}
