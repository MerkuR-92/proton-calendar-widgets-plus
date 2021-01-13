package me.proton.android.calendar.domain.usecase

import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.CalendarUserSettingsEntity
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.api.SettingsApi
import me.proton.core.domain.entity.UserId
import me.proton.core.util.kotlin.toInt

class UpdateCalendarUserSettingsUseCase(
    private val logger: Logger,
    private val settingsApi: SettingsApi,
    private val calendarUserSettingsChangedUseCase: CalendarUserSettingsChangedUseCase
): UseCase {

    companion object {
        const val WORKER_ID_TZ = "WORKER_ID_TZ"
        const val WORKER_ID_AUTO_DETECT = "WORKER_ID_AUTO_DETECT"
    }

    suspend fun executePrimaryTimezone(userId: UserId, primaryTimezone: String): UseCase.Result {
        return when (val updateCalendarUserPrimaryTimezoneResponse =
            settingsApi.updateCalendarUserPrimaryTimezone(userId, primaryTimezone)
        ) {
            is ApiResponse.Success -> {
                calendarUserSettingsChangedUseCase.execute(userId.id, updateCalendarUserPrimaryTimezoneResponse.data.calendarUserSettings)
            }
            is ApiResponse.Error -> {
                logger.e("api error updating calendar user primary timezone: $updateCalendarUserPrimaryTimezoneResponse")
                UseCase.Result.Error(updateCalendarUserPrimaryTimezoneResponse.error)
            }
            is ApiResponse.Exception -> {
                logger.e("api error updating calendar user primary timezone: $updateCalendarUserPrimaryTimezoneResponse")
                UseCase.Result.Error(updateCalendarUserPrimaryTimezoneResponse.exception.message ?: "(no exception message)")
            }
        }
    }

    suspend fun executeAutoDetectPrimaryTimezone(userId: UserId, autoDetectPrimaryTimezone: Boolean): UseCase.Result {
        return when (
            val updateCalendarUserAutoDetectTimezoneResponse =
            settingsApi.updateCalendarUserAutoDetectTimezone(userId, autoDetectPrimaryTimezone.toInt())
        ) {
            is ApiResponse.Success -> {
                calendarUserSettingsChangedUseCase.execute(userId.id, updateCalendarUserAutoDetectTimezoneResponse.data.calendarUserSettings)
            }
            is ApiResponse.Error -> {
                logger.e("api error updating calendar user auto detect primary timezone: $updateCalendarUserAutoDetectTimezoneResponse")
                UseCase.Result.Error(updateCalendarUserAutoDetectTimezoneResponse.error)
            }
            is ApiResponse.Exception -> {
                logger.e("api error updating calendar user auto detect primary timezone: $updateCalendarUserAutoDetectTimezoneResponse")
                UseCase.Result.Error(updateCalendarUserAutoDetectTimezoneResponse.exception.message ?: "(no exception message)")
            }
        }
    }
}
