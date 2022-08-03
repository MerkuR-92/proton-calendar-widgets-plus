package me.proton.android.calendar.domain.usecase

import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.entity.CalendarUserSettingsEntity
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.api.SettingsApi
import me.proton.core.domain.entity.UserId
import javax.inject.Inject

/**
 * Fetches from API and saves CalendarUserSettings in DB.
 */
class RefreshCalendarUserSettingsUseCase @Inject constructor(
    private val logger: Logger,
    private val calendarsRepository: CalendarsRepository,
    private val settingsApi: SettingsApi,
) {

    suspend operator fun invoke(
        userId: UserId
    ): UseCase.Result {

        val calendarUserSettingsResponse = settingsApi.getCalendarUserSettings(userId)

        if (calendarUserSettingsResponse !is ApiResponse.Success) {
            return UseCase.Result.Error("RefreshCalendarUserSettingsUseCase: error getting calendar user settings from API: $calendarUserSettingsResponse")
        }

        calendarsRepository.persistCalendarUserSettings(
            userId.id,
            calendarUserSettingsResponse.data.calendarUserSettings
        )

        return UseCase.Result.Success<CalendarUserSettingsEntity>(calendarUserSettingsResponse.data.calendarUserSettings)
    }


}
