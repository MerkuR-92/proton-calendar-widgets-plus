package me.proton.android.calendar.domain.usecase

import me.proton.android.calendar.common.GsonCommon
import me.proton.android.calendar.common.TimberLogger
import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.api.CalendarsApi
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.ValueKey.USER_CALENDAR_SETTINGS
import me.proton.android.calendar.domain.ValueStoreProvider
import me.proton.android.calendar.domain.api.SettingsApi

/**
 * Sets up all the user's calendars, call this only once after successful login.
 */
// TODO Valentin go over this entire use case
class BootstrapCalendarsUseCase( // TODO TEST
    private val logger: Logger,
    private val calendarsApi: CalendarsApi,
    private val settingsApi: SettingsApi,
    private val valueStoreProvider: ValueStoreProvider,
    private val calendarsRepository: CalendarsRepository,
    private val cacheCalendarPassphraseUseCase: CacheCalendarPassphraseUseCase): UseCase {

    suspend fun execute(userId: String) : UseCase.Result {

        logger.v("executing BootstrapCalendarsUseCase")

        val calendarsResponse = calendarsApi.getCalendars()
        if (calendarsResponse !is ApiResponse.Success) {
            return UseCase.Result.Error("error getting calendars from API: $calendarsResponse")
        }

        val calendarUserSettingsResponse = settingsApi.getCalendarUserSettings()
        if (calendarUserSettingsResponse !is ApiResponse.Success) {
            return UseCase.Result.Error("error getting calendar user settings from API: $calendarUserSettingsResponse")
        }

        val failedCalendarIds = mutableListOf<String>()

        calendarsResponse.data.calendars.forEach { calendarEntity ->
            when (val bootstrapResponse = calendarsApi.getBootstrap(calendarEntity.id)) {
                is ApiResponse.Success -> {
                    logger.v("got successful bootstrap response for calendar ${calendarEntity.id}")
                    calendarsRepository.apply {
                        persistCalendar(userId, calendarEntity)
                        persistSettings(bootstrapResponse.data.calendarSettings)
                        persistPassphrase(bootstrapResponse.data.passphrase)
                        bootstrapResponse.data.keys.forEach { persistCalendarKey(it) }
                        bootstrapResponse.data.members.forEach { persistMember(it) }
                    }

                    // extract passphrase for just saved Calendar
                    cacheCalendarPassphraseUseCase.execute(userId, calendarEntity.id)

                    TimberLogger.e("calendar settings when bootstrapping: ${calendarUserSettingsResponse.data.calendarUserSettings}")

                    // save User CalendarSettings
                    // TODO deal with nullable DefaultCalendarID!!!!!!!!!!!!!!!!!!!!!!!!!
                    val todoUserCalendarSettings = if (calendarUserSettingsResponse.data.calendarUserSettings.defaultCalendarId == null) {
                        calendarUserSettingsResponse.data.calendarUserSettings.copy(defaultCalendarId = calendarEntity.id)
                    } else calendarUserSettingsResponse.data.calendarUserSettings
                    valueStoreProvider.provideValueStore(userId).putString(USER_CALENDAR_SETTINGS, GsonCommon.gson.toJson(todoUserCalendarSettings))

                    // TODO ONLY FOR LOGIN MOCK, REMOVE THIS
                    valueStoreProvider.provideValueStore("TODO LOGIN").putString("DEFAULT CALENDAR ID", todoUserCalendarSettings.defaultCalendarId!!)

                }
                is ApiResponse.Error -> {
                    logger.e("api error getting calendar bootstrap: $bootstrapResponse")
                    failedCalendarIds.add(calendarEntity.id)
                }
                is ApiResponse.Exception -> {
                    logger.e("api exception getting calendar bootstrap: $bootstrapResponse")
                    failedCalendarIds.add(calendarEntity.id)
                }
            }
        }

        return if (failedCalendarIds.isNotEmpty()) {
            UseCase.Result.Error("calendar bootstrap failed for: ${failedCalendarIds.joinToString(separator = ", ")}")
        } else {
            UseCase.Result.Success
        }
    }

}
