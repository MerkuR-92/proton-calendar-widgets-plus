package me.proton.android.calendar.domain.usecase

import android.database.sqlite.SQLiteConstraintException
import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.api.ServerEvent
import me.proton.android.calendar.data.api.ServerEventsApiResponse
import me.proton.android.calendar.data.entity.CalendarFlags
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.UserSettingsRepository
import me.proton.android.calendar.domain.ValueStoreProvider
import me.proton.android.calendar.domain.api.CalendarsApi
import me.proton.android.calendar.domain.api.ServerEventsApi
import me.proton.core.domain.entity.UserId
import me.proton.core.user.data.extension.toAddress
import me.proton.core.user.data.extension.toUser
import me.proton.core.user.domain.entity.AddressId
import me.proton.core.user.domain.repository.UserAddressRepository
import me.proton.core.user.domain.repository.UserRepository
import me.proton.core.util.kotlin.toBoolean
import java.time.Instant
import java.time.ZoneId

class HandleServerEventsUseCase(
    private val logger: Logger,
    private val userRepository: UserRepository,
    private val userAddressRepository: UserAddressRepository,
    private val calendarsRepository: CalendarsRepository,
    private val userSettingsRepository: UserSettingsRepository,
    private val cacheCalendarPassphraseUseCase: CacheCalendarPassphraseUseCase,
    private val handleAlarmsUseCase: HandleAlarmsUseCase,
    private val updateAlarmsUseCase: UpdateAlarmsUseCase,
    private val handleEventsMetadataUseCase: HandleEventsMetadataUseCase,
    private val calendarUserSettingsChangedUseCase: CalendarUserSettingsChangedUseCase,
    private val keySetupUseCase: KeySetupUseCase,
    private val bootstrapCalendarsUseCase: BootstrapCalendarsUseCase,
    private val calendarsApi: CalendarsApi,
    private val valueStoreProvider: ValueStoreProvider,
    private val serverEventsApi: ServerEventsApi

) : UseCase {

    suspend fun execute(eventsResponse: ServerEventsApiResponse, userId: UserId) : UseCase.Result {

        logger.v("handling server events in usecase")

        return try {

            val valueStore = valueStoreProvider.provideValueStore(userId.id)

            eventsResponse.user?.toUser()?.let {
                userRepository.updateUser(it)
            }
            eventsResponse.userSettings?.let {
                userSettingsRepository.persistUserSettings(userId.id, it)
            }
            eventsResponse.calendarUserSettings?.let {
                calendarUserSettingsChangedUseCase.execute(userId.id, it)
            }

            eventsResponse.calendars?.forEach {
                it.handleAction(
                    { calendarsRepository.deleteCalendarById(it.id) },
                    {

                        // Try to complete key setup for calendar:
                        // - we persist newly updated calendar fetched from API if it succeeds
                        // - we persist calendar from server event if it fails
                        val calendarToPersist = if (it.calendar?.hasIncompleteKeySetup == true) {
                            when (val keySetupResult = keySetupUseCase.execute(userId, it.id)) {
                                is UseCase.Result.Success<*> -> {
                                    val calendarResponse = calendarsApi.getCalendar(userId, it.id)
                                    if (calendarResponse !is ApiResponse.Success) {
                                        logger.e("error getting calendar from API in HandleServerEventsUseCase")

                                        var calendarFlags = it.calendar.flags
                                        calendarFlags -= CalendarFlags.INCOMPLETE_SETUP.value
                                        // if calendar is inactive and no other error flags are set, make it active
                                        if (calendarFlags == 0) calendarFlags = CalendarFlags.ACTIVE.value

                                        it.calendar.copy(flags = calendarFlags)
                                    } else {
                                        calendarResponse.data.calendar
                                    }
                                }
                                is UseCase.Result.InvalidParams -> {
                                    logger.e("keySetupResult invalid params: ${keySetupResult.message}")
                                    it.calendar
                                }
                                is UseCase.Result.Error -> {
                                    logger.e("keySetupResult error: ${keySetupResult.message}")
                                    it.calendar
                                }
                            }
                        } else {
                            it.calendar!!
                        }

                        // because of the separate event loops for calendars, we need to execute bootstrap
                        // when new calendar is created
                        val timezone = calendarsRepository.selectCalendarUserSettings(userId.id)?.primaryTimezone ?: ZoneId.systemDefault().id
                        val executeBootstrapResult = bootstrapCalendarsUseCase.executeBootstrap(calendarToPersist, userId, timezone)
                        executeBootstrapResult.ifSuccessAndLogErrors(logger) { }

                        // bootstrap persists CalendarEntity on its own
                        if (executeBootstrapResult !is UseCase.Result.Success<*>) {
                            calendarsRepository.persistCalendar(userId.id, calendarToPersist)
                        }

                    },
                    { calendarsRepository.updateCalendar(userId.id, it.calendar!!) }
                )
            }

            val justDeletedCalendarIds = eventsResponse.calendars?.mapNotNull { if (it.action == ServerEvent.Action.DELETE.value) it.id else null } ?: emptyList()

            // TODO Uncomment check calendar once key reactivation is fixed
//            var checkCalendarFlags = false
            eventsResponse.addresses?.forEach {
                it.handleAction(
                    delete = {
                        userAddressRepository.deleteAddresses(listOf(AddressId(it.id)))
                    },
                    create = {
                        it.address?.toAddress(userId)?.let { address ->
                            userAddressRepository.addAddresses(listOf(address))
                        }
                    },
                    update = {
                        it.address?.toAddress(userId)?.let { address ->
                            userAddressRepository.updateAddresses(listOf(address))
                        }
                        // if (!checkCalendarFlags && usersRepository.hasReactivatedAddressKeys(it.address!!)) checkCalendarFlags = true
                        calendarsRepository.refreshCalendarsFlagsForAddress(it.address!!.email, it.address.status.toBoolean(), userId.id)
                    }
                )
            }

//            if (checkCalendarFlags) {
//                calendarsRepository.refreshCalendars(userId)
//            }

            eventsResponse.calendarEvents?.let {
                handleEventsMetadataUseCase.execute(userId, it)
            }

            eventsResponse.calendarAlarms?.forEach {
                it.handleAction(
                    {
                        // delete all local Event Alarms along with the one from API
                        calendarsRepository.selectEventAlarm(it.id)?.let {
                            calendarsRepository.deleteEventAlarmsByEventIdAndOccurrence(it.eventId, it.occurrence)
                        }
                    },
                    {
                        try {

                            if (!calendarsRepository.hasEvent(it.alarm!!.eventId, it.alarm.calendarId)) {

                                if (HandleEventsMetadataUseCase.shouldFetchEvent(it.alarm)) {
                                    logger.v("event ${it.alarm.eventId} for alarm doesn't exist in DB")
                                    // event doesn't exist locally, fetch and save it before inserting alarm
                                    when (val event = calendarsApi.getEvent(userId, it.alarm.calendarId, it.alarm.eventId)) {
                                        is ApiResponse.Success -> {
                                            logger.v("event ${it.alarm.eventId} for alarm successfully fetched")
                                            calendarsRepository.persistEvents(event.data.event)
                                            logger.v("persisting EventAlarm from loop for instant: ${Instant.ofEpochSecond(it.alarm.occurrence)}")
                                            calendarsRepository.persistEventAlarm(it.alarm)
                                            updateAlarmsUseCase.execute(userId.id, listOf(event.data.event.id))
                                        }
                                        // TODO maybe ignore some errors like non-existing Event, but let's see what kind of error reports we get
                                        is ApiResponse.Error -> {
                                            logger.e("couldn't fetch event for alarm: ${event.errorCode}, ${event.error}")
                                        }
                                        is ApiResponse.Exception -> {
                                            logger.e("couldn't fetch event for alarm: ${event.exception}")
                                        }
                                    }
                                } else {
                                    logger.v("event ${it.alarm.eventId} for alarm doesn't exist in DB but is outside of sync window")
                                }

                            } else {
                                logger.v("persisting EventAlarm from loop for instant: ${Instant.ofEpochSecond(it.alarm.occurrence)}")
                                calendarsRepository.persistEventAlarm(it.alarm)
                                updateAlarmsUseCase.execute(userId.id, listOf(it.alarm.eventId))
                            }

                        } catch (e: SQLiteConstraintException) {
                            // if this fails with `787 SQLITE_CONSTRAINT_FOREIGNKEY` it means CalendarEvent no longer exists
                            //  and we're trying to insert its Alarm to the database or something failed when inserting
                            //  that CalendarEvent into database, either way there's nothing we can do here
                            logger.i("exception persisting event alarm", e)
                        }
                    }
                )
            }

            eventsResponse.calendarKeys?.forEach {
                it.handleAction(
                    { calendarsRepository.deleteCalendarKeyById(it.id) },
                    {
                        if (justDeletedCalendarIds.contains(it.key?.calendarId)) {
                            logger.i("action CREATE/UPDATE for calendarKey in just deleted calendar")
                        } else {
                            calendarsRepository.persistCalendarKey(it.key!!)
                            // TODO If new key, fetch all calendars to get flags (flags are not yet returned in fetch by id)
                            //  Replace this with select calendar by id once BE implements updated flags there
                            calendarsRepository.refreshCalendarsFlags(userId)
                        }
                    }
                )
            }
            eventsResponse.calendarMembers?.forEach {
                it.handleAction(
                    { calendarsRepository.deleteMemberById(it.id) },
                    {
                        if (justDeletedCalendarIds.contains(it.member?.calendarId)) {
                            logger.i("action CREATE/UPDATE for calendarMember in just deleted calendar")
                        } else {
                            calendarsRepository.persistMember(it.member!!)
                        }
                    }
                )
            }
            eventsResponse.calendarPassphrases?.forEach {
                // TODO when passphrase is force changed, we will get action=UPDATE for old (now inactive) passphrase
                // and action=CREATE for the new one
                it.handleAction(
                    { calendarsRepository.deletePassphraseById(it.id) },
                    {
                        if (justDeletedCalendarIds.contains(it.passphrase?.calendarId)) {
                            logger.i("action CREATE/UPDATE for calendarPassphrase in just deleted calendar")
                        } else {
                            calendarsRepository.persistPassphrase(it.passphrase!!)
                            // TODO make sure we delete passphrase from cache if it becomes inactive
                            when (val result = cacheCalendarPassphraseUseCase.execute(userId, it.passphrase.calendarId)) {
                                is UseCase.Result.InvalidParams -> logger.e("event looop calendar passphrase caching InvalidParams in HandleServerEventsUseCase: ${result.message}")
                                is UseCase.Result.Error -> logger.e("event looop calendar passphrase caching Error in HandleServerEventsUseCase: ${result.message}")
                            }
                        }
                    }
                )
            }
            eventsResponse.calendarSettings?.forEach {
                it.handleAction(
                    { calendarsRepository.deleteCalendarSettingsById(it.id) },
                    {
                        if (justDeletedCalendarIds.contains(it.calendarSettings?.calendarId)) {
                            logger.i("action CREATE/UPDATE for calendarSettings in just deleted calendar")
                        } else {
                            calendarsRepository.persistCalendarSettings(it.calendarSettings!!)
                        }
                    }
                )
            }
            eventsResponse.calendarSubscriptions?.forEach {
                it.handleAction(
                    { }, // Can't receive action delete for CalendarSubscription
                    {
                        val calendarId = it.calendarSubscriptionEntity?.calendarId
                        if (justDeletedCalendarIds.contains(calendarId)) {
                            logger.i("action CREATE/UPDATE for calendarSubscriptions in just deleted calendar")
                        } else if (calendarId != null) {
                            calendarsRepository.persistCalendarSubscription(it.calendarSubscriptionEntity)
                        }
                    }
                )
            }

            UseCase.Result.Success<Unit>()
        } catch (e: kotlinx.coroutines.CancellationException) {
            logger.e("CancellationException in HandleServerEventsUseCase")
            UseCase.Result.Error(e.message ?: "CancellationException")
        } catch (e: Exception) {
            logger.e("Error in HandleServerEventsUseCase", e)
            UseCase.Result.Error(e.message ?: "no stack trace message available")
        }
    }

    private suspend fun ServerEvent.BaseServerEventApiResponse.handleAction(delete: suspend () -> Unit, create: suspend () -> Unit, update: suspend () -> Unit = create) {
        when (this.action) {
            0 -> delete.invoke()
            1 -> create.invoke()
            2 -> update.invoke()
            // TODO there's also 3 = UPDATE FLAGS but not used yet
        }
    }

}
