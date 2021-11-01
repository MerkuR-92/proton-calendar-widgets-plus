package me.proton.android.calendar.domain.usecase

import me.proton.android.calendar.WidgetRefresher
import me.proton.android.calendar.common.DateTimeUtilsImpl.fallbackTimeZone
import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.entity.CalendarEntity
import me.proton.android.calendar.domain.*
import me.proton.android.calendar.domain.api.CalendarsApi
import me.proton.android.calendar.domain.api.ServerEventsApi
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
    private val userSettingsRepository: UserSettingsRepository,
    private val calendarsRepository: CalendarsRepository,
    private val cacheCalendarPassphraseUseCase: CacheCalendarPassphraseUseCase,
    private val createCalendarUseCase: CreateCalendarUseCase,
    private val fetchEventsUseCase: FetchEventsUseCase,
    private val updateAlarmsUseCase: UpdateAlarmsUseCase,
    private val syncAlarmsUseCase: SyncAlarmsUseCase,
    private val keySetupUseCase: KeySetupUseCase,
    private val reactivateCalendarKeyUseCase: ReactivateCalendarKeyUseCase,
    private val serverEventsApi: ServerEventsApi,
    private val valueStoreProvider: ValueStoreProvider,
    private val widgetRefresher: WidgetRefresher
): UseCase {

    suspend fun execute(userId: UserId, defaultCalendarName: String, showConfirmationDialog: Boolean): UseCase.Result {

        logger.v("executing BootstrapCalendarsUseCase")

        var calendarsResponse = calendarsApi.getCalendars(userId)
        if (calendarsResponse !is ApiResponse.Success) {
            logger.e("BootstrapCalendarsUseCase: error getting calendars from API")
            return UseCase.Result.Error("BootstrapCalendarsUseCase: error getting calendars from API: $calendarsResponse")
        }

        // Subscribed calendars do not count when checking if we have active calendars
        var userCalendars = calendarsResponse.data.calendars.filterNot { it.isSubscribed }
        if (calendarsResponse.data.calendars.isNotEmpty() && calendarsResponse.data.calendars.firstOrNull { it.isResetNeeded } != null) {
            // Always show confirmation dialog if a calendar has flag RESET_NEEDED
            return UseCase.Result.Error("BootstrapCalendarsUseCase: error reset needed for calendar", UseCase.Error.RESET_NEEDED)
        } else if (userCalendars.isNotEmpty() &&
            userCalendars.firstOrNull { it.isActive || it.isDisabled || it.hasIncompleteKeySetup || it.hasUpdatePassphrase } == null) {
            return UseCase.Result.Error("BootstrapCalendarsUseCase: error user has no active calendar", UseCase.Error.NO_ACTIVE_CALENDAR)
        }

        var redoGetCalendars = false

        // We need to create a default calendar if user has none or has only subscribed calendars
        if (userCalendars.isNullOrEmpty()) {

            val createDefaultCalendarResult = createCalendarUseCase.execute(userId, defaultCalendarName)

            createDefaultCalendarResult.ifSuccessAndLogErrors(logger) {
                // Only update user primary timezone if we just created the first calendar
                when (val updateCalendarUserPrimaryTimezoneResponse =
                    settingsApi.updateCalendarUserPrimaryTimezone(
                        userId,
                        fallbackTimeZone(TimeZone.getDefault().id, fallbackToDefault = true)!!
                    )) {
                    is ApiResponse.Error -> logger.e("api error updating user timezone: $updateCalendarUserPrimaryTimezoneResponse")
                    is ApiResponse.Exception -> logger.e("api error updating user timezone: $updateCalendarUserPrimaryTimezoneResponse")
                }
            }

            if (createDefaultCalendarResult !is UseCase.Result.Success<*>) {
                logger.e("BootstrapCalendarsUseCase: error unable to create default calendar for user")
                return UseCase.Result.Error("BootstrapCalendarsUseCase: error unable to create default calendar for user")
            }
            redoGetCalendars = true
        }

        // We fix both normal and subscribed calendars
        calendarsResponse.data.calendars.forEach {
            if (it.hasIncompleteKeySetup) {
                // Handle flag INCOMPLETE_SETUP
                val keySetupResult = keySetupUseCase.execute(userId, it.id)

                keySetupResult.ifSuccessAndLogErrors(logger) { }

                redoGetCalendars = true
            }

            if (it.hasUpdatePassphrase) {
                // Handle flag UPDATE_PASSPHRASE

                // Skip confirmation dialog if we just handled flag RESET_NEEDED
                if (showConfirmationDialog) return UseCase.Result.Error("BootstrapCalendarsUseCase: error update passphrase for calendar", UseCase.Error.UPDATE_PASSPHRASE)

                val reactivateCalendarKeyResult = reactivateCalendarKeyUseCase.execute(userId, it.id)

                reactivateCalendarKeyResult.ifSuccessAndLogErrors(logger) { }

                redoGetCalendars = true
            }
        }

        if (redoGetCalendars) {
            // GET the calendar list again after creating default one or fixing incomplete setup
            calendarsResponse = calendarsApi.getCalendars(userId)
            if (calendarsResponse !is ApiResponse.Success) {
                logger.e("BootstrapCalendarsUseCase: error getting calendars from API after creating default calendar: $calendarsResponse")
                return UseCase.Result.Error("BootstrapCalendarsUseCase: error getting calendars from API: $calendarsResponse")
            }

            // Subscribed calendars do not count for those checks
            userCalendars = calendarsResponse.data.calendars.filterNot { it.isSubscribed }
            if (userCalendars.isNullOrEmpty()) {
                logger.e("BootstrapCalendarsUseCase: still no calendar after creating default calendar")
                return UseCase.Result.Error("BootstrapCalendarsUseCase: error user has no calendar", UseCase.Error.NO_CALENDAR)
            } else if (userCalendars.firstOrNull { it.isActive || it.isDisabled } == null) {
                logger.e("BootstrapCalendarsUseCase: still no active calendar after creating default calendar")
                return UseCase.Result.Error("BootstrapCalendarsUseCase: error user has no active calendar", UseCase.Error.NO_ACTIVE_CALENDAR)
            }
        }

        val calendarUserSettingsResponse = settingsApi.getCalendarUserSettings(userId) // TODO this will have a value if we have at least 1 calendar
        if (calendarUserSettingsResponse !is ApiResponse.Success) {
            logger.e("BootstrapCalendarsUseCase: error getting calendar user settings from API: $calendarUserSettingsResponse")
            return UseCase.Result.Error("BootstrapCalendarsUseCase: error getting calendar user settings from API: $calendarUserSettingsResponse")
        }
        calendarsRepository.persistCalendarUserSettings(
            userId.id,
            calendarUserSettingsResponse.data.calendarUserSettings
        )

        val userSettingsResponse = settingsApi.getUserSettings(userId)
        if (userSettingsResponse !is ApiResponse.Success) {
            logger.e("BootstrapCalendarsUseCase: error getting user settings from API: $userSettingsResponse")
            return UseCase.Result.Error("BootstrapCalendarsUseCase: error getting user settings from API: $userSettingsResponse")
        }
        userSettingsRepository.persistUserSettings(userId.id, userSettingsResponse.data.userSettings)

        val failedCalendarIds = mutableListOf<String>()

        calendarsResponse.data.calendars.forEach { calendarEntity ->
            val executeBootstrapResult = executeBootstrap(calendarEntity, userId, calendarUserSettingsResponse.data.calendarUserSettings.primaryTimezone)
            executeBootstrapResult.ifSuccessAndLogErrors(logger) { }
            if (executeBootstrapResult !is UseCase.Result.Success<*>) {
                failedCalendarIds.add(calendarEntity.id)
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
            UseCase.Result.Error("calendar bootstrap failed for: ${failedCalendarIds.joinToString(separator = ", ")}", UseCase.Error.SOME_CALENDARS_FAILED_BOOTSTRAP)
        } else {

            // sync alarms right after downloading calendars and events
            syncAlarmsUseCase.execute(userId).ifSuccessAndLogErrors(logger) { }

            UseCase.Result.Success<Unit>()
        }
    }

    suspend fun executeBootstrap(calendarEntity: CalendarEntity, userId: UserId, displayTimeZoneId: String): UseCase.Result {
        when (val bootstrapResponse = calendarsApi.getBootstrap(userId, calendarEntity.id)) {
            is ApiResponse.Success -> {
                logger.v("got successful bootstrap response for calendar ${calendarEntity.id}")
                calendarsRepository.apply {
                    persistCalendar(userId.id, calendarEntity)
                    persistCalendarSettings(bootstrapResponse.data.calendarSettings)
                    if (calendarEntity.isSubscribed && bootstrapResponse.data.calendarSubscriptionEntity != null) {
                        persistCalendarSubscription(bootstrapResponse.data.calendarSubscriptionEntity)
                    } else if (calendarEntity.isSubscribed && bootstrapResponse.data.calendarSubscriptionEntity == null) {
                        logger.e("BootstrapCalendarsUseCase: calendarSubscriptionEntity was null")
                    }
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
                    is UseCase.Result.Success<*> -> {
                        // fetch events
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
                                    widgetRefresher.refresh()
                                }
                            }
                        }

                        // for each bootstrapped calendar, get its latest Event ID
                        val valueStore = valueStoreProvider.provideValueStore(userId.id)
                        val latestEventIdResponse = serverEventsApi.getLatestServerCalendarEvent(userId, calendarEntity.id)
                        if (latestEventIdResponse is ApiResponse.Success) {
                            valueStore.putStringInSet(ValueSet.LAST_SERVER_CALENDAR_EVENT_ID, calendarEntity.id, latestEventIdResponse.data.calendarEventId)
                        } else {
                            logger.e("could not get latest calendar server event ID response in BootstrapCalendarsUseCase")
                        }


                        return UseCase.Result.Success<Unit>()
                    }
                    is UseCase.Result.InvalidParams -> {
                        return UseCase.Result.InvalidParams("BootstrapCalendarsUseCase: cachePassphraseResult invalid params: ${cachePassphraseResult.message}")
                    }
                    is UseCase.Result.Error -> {
                        return UseCase.Result.Error("BootstrapCalendarsUseCase: cachePassphraseResult error: ${cachePassphraseResult.message}")
                    }
                }
            }
            is ApiResponse.Error -> {
                return UseCase.Result.Error("BootstrapCalendarsUseCase: api error getting calendar bootstrap: $bootstrapResponse")
            }
            is ApiResponse.Exception -> {
                return UseCase.Result.Error("BootstrapCalendarsUseCase: api exception getting calendar bootstrap: $bootstrapResponse")
            }
        }
    }

}
