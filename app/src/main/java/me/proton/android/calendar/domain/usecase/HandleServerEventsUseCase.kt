package me.proton.android.calendar.domain.usecase

import android.database.sqlite.SQLiteConstraintException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.api.ServerEvent
import me.proton.android.calendar.data.api.ServerEventsApiResponse
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.UsersRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.api.CalendarsApi
import me.proton.core.domain.entity.UserId
import java.lang.Exception

class HandleServerEventsUseCase(
    private val logger: Logger,
    private val calendarsRepository: CalendarsRepository,
    private val usersRepository: UsersRepository,
    private val cacheCalendarPassphraseUseCase: CacheCalendarPassphraseUseCase,
    private val handleAlarmsUseCase: HandleAlarmsUseCase,
    private val fetchPublicKeysUseCase: FetchPublicKeysUseCase,
    private val calendarsApi: CalendarsApi) : UseCase {

    suspend fun execute(eventsResponse: ServerEventsApiResponse, userId: UserId) : UseCase.Result {

        logger.v("handling server events in usecase")

        return try {
            eventsResponse.user?.let {
                usersRepository.updateUser(it)
            }
            eventsResponse.userSettings?.let {
                usersRepository.persistUserSettings(userId.id, it)
            }
            eventsResponse.calendarUserSettings?.let {
                calendarsRepository.persistCalendarUserSettings(userId.id, it)
            }

            val calendarsToRefresh = ArrayList<String>()
            eventsResponse.calendars?.forEach {
                it.handleAction(
                    { calendarsRepository.deleteCalendarById(it.id) },
                    { calendarsRepository.persistCalendar(userId.id, it.calendar!!) },
                    {
                        //Refresh events if calendar db is not up to date on display value
                        if (it.calendar != null && !calendarsRepository.isCalendarDisplayUpToDate(it.id, it.calendar.display))
                            calendarsToRefresh.add(it.id)
                        calendarsRepository.updateCalendar(userId.id, it.calendar!!)
                    }
                )
            }
            if (calendarsToRefresh.isNotEmpty()) calendarsRepository.refreshEvents(calendarsToRefresh)

            eventsResponse.addresses?.forEach {
                it.handleAction(
                    { usersRepository.deleteAddressById(it.id) },
                    { usersRepository.persistAddress(userId.id, it.address!!) },
                    {
                        usersRepository.updateAddress(userId.id, it.address!!)
                        calendarsRepository.refreshCalendarsFlagsForAddress(it.address.email, it.address.status, userId.id)
                    }
                )
            }
            eventsResponse.calendarEvents?.forEach {
                logger.d("usecase calendar events: ${it}")
                it.handleAction(
                    { calendarsRepository.deleteEventById(it.id) },
                    {

                        // TODO optimise this so we don't fetch unnecessary events outside of desired window
                        // https://jira.protontech.ch/browse/CALAND-463

                        // after "event metadata migration", we need to fetch events separately
                        val singleEventResponse = calendarsApi.getEvent(userId, it.event!!.calendarId, it.event!!.id)

                        // TODO MOVE THIS TO SEPARATE USECASE
                        when (singleEventResponse) {
                            is ApiResponse.Success -> {
                                calendarsRepository.persistEvents(singleEventResponse.data.event)

                                // TODO move this to worker, remove duplicated code
                                try {
                                    val emails =
                                        (singleEventResponse.data.event.sharedEvents.map { (it as? JsonObject)?.get("Author")?.jsonPrimitive?.content } +
                                                singleEventResponse.data.event.calendarEvents.map { (it as? JsonObject)?.get("Author")?.jsonPrimitive?.content } +
                                                singleEventResponse.data.event.personalEvents.map { (it as? JsonObject)?.get("Author")?.jsonPrimitive?.content })
                                            .filterNotNull()
                                    emails.distinct().forEach {
                                        fetchPublicKeysUseCase.execute(userId, it)
                                    }

                                } catch (e: IllegalStateException) {
                                    logger.e("error getting event's author from JSON")
                                }
                            }
                            is ApiResponse.Error -> throw Exception(singleEventResponse.error)
                            is ApiResponse.Exception -> throw Exception(singleEventResponse.exception)
                        }

                    }
                )
            }

            eventsResponse.calendarAlarms?.forEach {
                it.handleAction(
                    { calendarsRepository.deleteEventAlarmById(it.id) },
                    {
                        try {
                            calendarsRepository.persistEventAlarm(it.alarm!!)
                        } catch (e: SQLiteConstraintException) {
                            // if this fails with `787 SQLITE_CONSTRAINT_FOREIGNKEY` it means CalendarEvent no longer exists
                            //  and we're trying to insert its Alarm to the database or something failed when inserting
                            //  that CalendarEvent into database, either way there's nothing we can do here
                            logger.i("exception persisting event alarm", e)
                        }
                    }
                )
            }

            // if alarms changed, we need to reschedule them
            if (eventsResponse.calendarAlarms?.isNotEmpty() == true) handleAlarmsUseCase.execute(userId)

            eventsResponse.calendarKeys?.forEach {
                it.handleAction(
                    { calendarsRepository.deleteCalendarKeyById(it.id) },
                    { calendarsRepository.persistCalendarKey(it.key!!) }
                )
            }
            eventsResponse.calendarMembers?.forEach {
                it.handleAction(
                    { calendarsRepository.deleteMemberById(it.id) },
                    { calendarsRepository.persistMember(it.member!!) }
                )
            }
            eventsResponse.calendarPassphrases?.forEach {
                // TODO when passphrase is force changed, we will get action=UPDATE for old (now inactive) passphrase
                // and action=CREATE for the new one
                it.handleAction(
                    { calendarsRepository.deletePassphraseById(it.id) },
                    {
                        calendarsRepository.persistPassphrase(it.passphrase!!)
                        // TODO make sure we delete passphrase from cache if it becomes inactive
                        cacheCalendarPassphraseUseCase.execute(userId, it.passphrase.calendarId)
                    }
                )
            }
            eventsResponse.calendarSettings?.forEach {
                it.handleAction(
                    { calendarsRepository.deleteCalendarSettingsById(it.id) },
                    { calendarsRepository.persistCalendarSettings(it.calendarSettings!!) }
                )
            }

            UseCase.Result.Success
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
