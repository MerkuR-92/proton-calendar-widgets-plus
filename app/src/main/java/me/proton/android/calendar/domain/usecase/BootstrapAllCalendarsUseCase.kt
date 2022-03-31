package me.proton.android.calendar.domain.usecase

import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.fallbackTimeZone
import me.proton.android.calendar.common.utils.getAddressesOrNull
import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.domain.*
import me.proton.android.calendar.domain.api.CalendarsApi
import me.proton.android.calendar.domain.api.SettingsApi
import me.proton.core.domain.entity.UserId
import me.proton.core.user.domain.UserManager
import me.proton.core.usersettings.domain.repository.UserSettingsRepository
import java.util.*
import javax.inject.Inject

class BootstrapAllCalendarsUseCase @Inject constructor( // TODO TEST
    private val logger: Logger,
    private val boostrapCalendarUseCase: BootstrapCalendarUseCase,
    private val calendarsApi: CalendarsApi,
    private val settingsApi: SettingsApi,
    private val calendarsRepository: CalendarsRepository,
    private val createCalendarUseCase: CreateCalendarUseCase,
    private val syncAlarmsUseCase: SyncAlarmsUseCase,
    private val keySetupUseCase: KeySetupUseCase,
    private val reactivateCalendarKeyUseCase: ReactivateCalendarKeyUseCase,
    private val userManager: UserManager,
    private val userSettingsRepository: UserSettingsRepository
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
            return UseCase.Result.Error(
                "BootstrapCalendarsUseCase: error reset needed for calendar",
                UseCase.Error.Bootstrap.ResetNeeded
            )
        } else if (userCalendars.isNotEmpty() &&
            userCalendars.firstOrNull { it.isActive || it.isDisabled || it.hasIncompleteKeySetup || it.hasUpdatePassphrase } == null
        ) {
            return UseCase.Result.Error(
                "BootstrapCalendarsUseCase: error user has no active calendar",
                UseCase.Error.Bootstrap.NoActiveCalendar
            )
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
        val addresses =
            if (calendarsResponse.data.calendars.any { it.hasIncompleteKeySetup || it.hasUpdatePassphrase }) {
                // Fetch the user addresses only once if we need to do key setup or reactivate calendar keys
                userManager.getAddressesOrNull(userId, refresh = true)
            } else null
        calendarsResponse.data.calendars.forEach {
            if (it.hasIncompleteKeySetup) {
                // Handle flag INCOMPLETE_SETUP
                val keySetupResult = keySetupUseCase.execute(userId, it.id, addresses)

                keySetupResult.ifSuccessAndLogErrors(logger) { }

                redoGetCalendars = true
            }

            if (it.hasUpdatePassphrase) {
                // Handle flag UPDATE_PASSPHRASE

                // Skip confirmation dialog if we just handled flag RESET_NEEDED
                if (showConfirmationDialog) return UseCase.Result.Error(
                    "BootstrapCalendarsUseCase: error update passphrase for calendar",
                    UseCase.Error.Bootstrap.UpdatePassphrase
                )

                val reactivateCalendarKeyResult = reactivateCalendarKeyUseCase.execute(userId, it.id, addresses)

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
                return UseCase.Result.Error(
                    "BootstrapCalendarsUseCase: error user has no calendar",
                    UseCase.Error.Bootstrap.NoCalendar
                )
            } else if (userCalendars.firstOrNull { it.isActive || it.isDisabled } == null) {
                logger.e("BootstrapCalendarsUseCase: still no active calendar after creating default calendar")
                return UseCase.Result.Error(
                    "BootstrapCalendarsUseCase: error user has no active calendar",
                    UseCase.Error.Bootstrap.NoActiveCalendar
                )
            }
        }

        val calendarUserSettingsResponse =
            settingsApi.getCalendarUserSettings(userId) // TODO this will have a value if we have at least 1 calendar
        if (calendarUserSettingsResponse !is ApiResponse.Success) {
            logger.e("BootstrapCalendarsUseCase: error getting calendar user settings from API: $calendarUserSettingsResponse")
            return UseCase.Result.Error("BootstrapCalendarsUseCase: error getting calendar user settings from API: $calendarUserSettingsResponse")
        }
        calendarsRepository.persistCalendarUserSettings(
            userId.id,
            calendarUserSettingsResponse.data.calendarUserSettings
        )

        val userSettingsResponseException =
            kotlin.runCatching { userSettingsRepository.getUserSettings(userId, refresh = true) }.exceptionOrNull()
        if (userSettingsResponseException != null) {
            logger.e(
                "BootstrapCalendarsUseCase: error getting user settings from API: ${userSettingsResponseException.message}",
                userSettingsResponseException
            )
            return UseCase.Result.Error("BootstrapCalendarsUseCase: error getting user settings from API: $userSettingsResponseException")
        }

        val failedCalendarIds = mutableListOf<String>()

        calendarsResponse.data.calendars.forEach { calendarEntity ->
            val executeBootstrapResult = boostrapCalendarUseCase.executeBootstrap(
                calendarEntity,
                userId,
                calendarUserSettingsResponse.data.calendarUserSettings.primaryTimezone
            )
            executeBootstrapResult.ifSuccessAndLogErrors(logger) { }
            if (executeBootstrapResult !is UseCase.Result.Success<*>) {
                failedCalendarIds.add(calendarEntity.id)

                val failReason = if (executeBootstrapResult is UseCase.Result.Error) {
                    "${executeBootstrapResult.message} + ${executeBootstrapResult.error}"
                } else if (executeBootstrapResult is UseCase.Result.InvalidParams) {
                    executeBootstrapResult.message
                } else null

                logger.e("calendar ${calendarEntity.id} failed bootstrap: ${failReason}")
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
            UseCase.Result.Error(
                "calendar bootstrap failed for: ${failedCalendarIds.joinToString(separator = ", ")}",
                UseCase.Error.Bootstrap.SomeCalendarsFailedBootstrap(failedCalendarIds)
            )
        } else {

            // sync alarms right after downloading calendars and events
            syncAlarmsUseCase.execute(userId).ifSuccessAndLogErrors(logger) { }

            UseCase.Result.Success<Unit>()
        }
    }
}
