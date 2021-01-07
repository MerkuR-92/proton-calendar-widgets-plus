package me.proton.android.calendar.domain.usecase

import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.UsersRepository
import me.proton.android.calendar.domain.api.CalendarsApi
import me.proton.android.calendar.domain.api.SettingsApi
import me.proton.core.domain.entity.UserId
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import java.util.*

/**
 * Sets up all the user's calendars, call this only once after successful login.
 */
class BootstrapCalendarsUseCase( // TODO TEST
    private val logger: Logger,
    private val calendarsApi: CalendarsApi,
    private val settingsApi: SettingsApi,
    private val usersRepository: UsersRepository,
    private val calendarsRepository: CalendarsRepository,
    private val cacheCalendarPassphraseUseCase: CacheCalendarPassphraseUseCase,
    private val createCalendarUseCase: CreateCalendarUseCase,
    private val fetchEventsUseCase: FetchEventsUseCase,
    private val updateAlarmsUseCase: UpdateAlarmsUseCase,
    private val syncAlarmsUseCase: SyncAlarmsUseCase
): UseCase {

    suspend fun execute(userId: UserId, defaultCalendarName: String): UseCase.Result {

        logger.v("executing BootstrapCalendarsUseCase")

        var calendarsResponse = calendarsApi.getCalendars(userId)
        if (calendarsResponse !is ApiResponse.Success) {
            logger.e("error getting calendars from API in BootstrapCalendarsUseCase")
            return UseCase.Result.Error("error getting calendars from API: $calendarsResponse")
        } else if (calendarsResponse.data.calendars.isNotEmpty() &&
            calendarsResponse.data.calendars.firstOrNull { it.isActive || it.isDisabled } == null) {
            logger.e("error no active calendar in BootstrapCalendarsUseCase")
            return UseCase.Result.Error("error user has no active calendar")
        }

        if (calendarsResponse.data.calendars.isNullOrEmpty()) {

            // Only update user primary timezone if user has no calendar settings yet
            var updateCalendarUserPrimaryTimezone = false
            when (val calendarUserSettingsResponse = settingsApi.getCalendarUserSettings(userId)) {
                is ApiResponse.Error -> {
                    updateCalendarUserPrimaryTimezone = calendarUserSettingsResponse.httpCode == 404
                    if (calendarUserSettingsResponse.httpCode != 404) logger.e("api error getting calendar user settings: $calendarUserSettingsResponse")
                }
                is ApiResponse.Exception -> logger.e("api error getting calendar user settings: $calendarUserSettingsResponse")
            }

            val createDefaultCalendarResult = createCalendarUseCase.execute(userId, defaultCalendarName)

            createDefaultCalendarResult.ifSuccessAndLogErrors(logger) {
                if (updateCalendarUserPrimaryTimezone) {
                    when (val updateCalendarUserPrimaryTimezoneResponse =
                        settingsApi.updateCalendarUserPrimaryTimezone(userId, TimeZone.getDefault().id)) {
                        is ApiResponse.Error -> logger.e("api error updating user timezone: $updateCalendarUserPrimaryTimezoneResponse")
                        is ApiResponse.Exception -> logger.e("api error updating user timezone: $updateCalendarUserPrimaryTimezoneResponse")
                    }
                }
            }

            if (createDefaultCalendarResult !is UseCase.Result.Success) {
                return UseCase.Result.Error("error unable to create default calendar for user")
            }

            // GET the calendar list again after creating default one
            calendarsResponse = calendarsApi.getCalendars(userId)
            if (calendarsResponse !is ApiResponse.Success) {
                logger.e("error getting calendars from API after creating default calendar")
                return UseCase.Result.Error("error getting calendars from API: $calendarsResponse")
            } else if (calendarsResponse.data.calendars.isNullOrEmpty()) {
                logger.e("still no calendar after creating default calendar")
                return UseCase.Result.Error("error user has no calendar")
            } else if (calendarsResponse.data.calendars.firstOrNull { it.isActive || it.isDisabled } == null) {
                logger.e("still no active calendar after creating default calendar")
                return UseCase.Result.Error("error user has no active calendar")
            }
        }

        val calendarUserSettingsResponse = settingsApi.getCalendarUserSettings(userId) // TODO this will have a value if we have at least 1 calendar
        if (calendarUserSettingsResponse !is ApiResponse.Success) {
            return UseCase.Result.Error("error getting calendar user settings from API: $calendarUserSettingsResponse")
        }

        val userSettingsResponse = settingsApi.getUserSettings(userId)
        if (userSettingsResponse !is ApiResponse.Success) {
            return UseCase.Result.Error("error getting user settings from API: $userSettingsResponse")
        }

        val failedCalendarIds = mutableListOf<String>()

        calendarsResponse.data.calendars.forEach { calendarEntity ->
            when (val bootstrapResponse = calendarsApi.getBootstrap(userId, calendarEntity.id)) {
                is ApiResponse.Success -> {
                    logger.v("got successful bootstrap response for calendar ${calendarEntity.id}")
                    calendarsRepository.apply {
                        persistCalendar(userId.id, calendarEntity)
                        persistCalendarSettings(bootstrapResponse.data.calendarSettings)
                        persistPassphrase(bootstrapResponse.data.passphrase)
                        bootstrapResponse.data.keys.forEach { persistCalendarKey(it) }
                        bootstrapResponse.data.members.forEach { persistMember(it) }
                    }

                    // TODO cached calendar passphrase will be invalidated when I reset my password and I was the only member of this calendar
                    // when calendar is shared it might get invalidated while I'm still logged in -- you can have only 1 ACTIVE calendar passphrase
                    // for this calendar at the same time, this will be sent in the event loop automatically

                    // extract passphrase for just saved Calendar
                    val cachePassphraseResult = cacheCalendarPassphraseUseCase.execute(userId, calendarEntity.id)
                    when (cachePassphraseResult) {
                        UseCase.Result.Success -> {
                            // TODO this is related to User and not Calendars, in theory we could save it some other time
                            //  but this should not cause any troubles

                            // if at least one calendar bootstrap succeeded, we save user and calendar settings
                            calendarsRepository.persistCalendarUserSettings(
                                userId.id,
                                calendarUserSettingsResponse.data.calendarUserSettings
                            )
                            usersRepository.persistUserSettings(userId.id, userSettingsResponse.data.userSettings)

                            // fetch events
                            val displayTimeZoneId =
                                calendarUserSettingsResponse.data.calendarUserSettings.primaryTimezone
                            val now = ZonedDateTime.now(ZoneId.of(displayTimeZoneId))
                            val fetchEventsResult = fetchEventsUseCase.execute(
                                userId,
                                listOf(calendarEntity.id),
                                now.with(TemporalAdjusters.firstDayOfMonth()).toLocalDate(),
                                now.with(TemporalAdjusters.lastDayOfMonth()).toLocalDate(),
                                displayTimeZoneId
                            )

                            fetchEventsResult.first.ifSuccessAndLogErrors(logger) {
                                if (fetchEventsResult.second == null) {
                                    logger.e("fetchEventsResult: null event list when Sucess")
                                } else {
                                    fetchEventsResult.second?.let {
                                        logger.v("persisting events in bootstrap: ${it.size}")
                                        calendarsRepository.persistEvents(*it.toTypedArray())
                                        updateAlarmsUseCase.execute(userId.id, it.map { it.id })
                                    }
                                }
                            }

                        }
                        is UseCase.Result.InvalidParams -> {
                            failedCalendarIds.add(calendarEntity.id)
                            logger.e("cachePassphraseResult invalid params: ${cachePassphraseResult.message}")
                        }
                        is UseCase.Result.Error -> {
                            failedCalendarIds.add(calendarEntity.id)
                            logger.e("cachePassphraseResult error: ${cachePassphraseResult.message}")
                        }
                    }
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

        failedCalendarIds.forEach {
            calendarsRepository.deleteCalendarById(it)
            // foreign keys on Calendar ID will also delete:
            //  - Calendar Settings
            //  - Passphrase
            //  - CalendarKeys
            //  - Members

            // CalendarUserSettings and UserSettings will not be deleted
        }

        return if (failedCalendarIds.isNotEmpty()) {
            UseCase.Result.Error("calendar bootstrap failed for: ${failedCalendarIds.joinToString(separator = ", ")}")
        } else {

            // sync alarms right after downloading calendars and events
            syncAlarmsUseCase.execute(userId).ifSuccessAndLogErrors(logger) {
                logger.v("syncAlarmsUseCase in bootstrap success")
            }

            UseCase.Result.Success
        }
    }

}
