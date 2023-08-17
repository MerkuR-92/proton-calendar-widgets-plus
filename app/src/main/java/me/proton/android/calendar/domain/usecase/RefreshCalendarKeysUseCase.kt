package me.proton.android.calendar.domain.usecase

import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.api.CalendarsApi
import me.proton.android.calendar.domain.api.SettingsApi
import me.proton.core.domain.entity.UserId
import javax.inject.Inject

/**
 * Fetches from API and saves CalendarUserSettings in DB.
 */
class RefreshCalendarKeysUseCase @Inject constructor(
    private val logger: Logger,
    private val calendarsRepository: CalendarsRepository,
    private val calendarsApi: CalendarsApi
) {

    companion object {
        const val REFRESH_CALENDAR_KEYS = "REFRESH_CALENDAR_KEYS"
    }

    suspend operator fun invoke(
        userId: UserId,
        calendarId: String
    ): UseCase.Result {

        val calendarKeysResponse = calendarsApi.getKeys(userId, calendarId)
        if (calendarKeysResponse !is ApiResponse.Success) {
            return UseCase.Result.Error("RefreshCalendarKeysUseCase: error getting calendar keys from API: $calendarKeysResponse")
        }

        val calendarKeys = calendarKeysResponse.data.keys

        // Persist new keys in DB
        calendarKeys.forEach {
            calendarsRepository.persistCalendarKey(it)
        }

        return UseCase.Result.Success(calendarKeys)
    }


}
