package me.proton.android.calendar.domain.usecase

import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.api.ServerEvent
import me.proton.android.calendar.data.api.ServerEventsApiResponse
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.UsersRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.api.CalendarsApi
import me.proton.android.calendar.domain.api.ServerEventsApi
import java.lang.Exception
import kotlin.math.log

class HandleServerEventsUseCase(
    private val logger: Logger,
    private val calendarsRepository: CalendarsRepository,
    private val usersRepository: UsersRepository,
    private val cacheCalendarPassphraseUseCase: CacheCalendarPassphraseUseCase,
    private val fetchPublicKeysUseCase: FetchPublicKeysUseCase,
    private val calendarsApi: CalendarsApi) : UseCase {

    suspend fun execute(eventsResponse: ServerEventsApiResponse, userId: String) : UseCase.Result {

        logger.v("handling server events in usecase")

        return try {
            eventsResponse.user?.let {
                usersRepository.persistUser(it)
            }
            eventsResponse.calendarUserSettings?.let {
                calendarsRepository.persistUserSettings(userId, it)
            }

            val calendarsToRefresh = ArrayList<String>()
            eventsResponse.calendars?.forEach {
                it.handleAction(
                    { calendarsRepository.deleteCalendarById(it.id) },
                    { calendarsRepository.persistCalendar(userId, it.calendar!!) },
                    {
                        //Refresh events if calendar db is not up to date on display value
                        if (it.calendar != null && !calendarsRepository.calendarDisplayUpToDate(it.id, it.calendar.display))
                            calendarsToRefresh.add(it.id)
                        calendarsRepository.updateCalendar(userId, it.calendar!!)
                    }
                )
            }
            if (calendarsToRefresh.isNotEmpty()) calendarsRepository.refreshEvents(calendarsToRefresh)

            eventsResponse.addresses?.forEach {
                it.handleAction(
                    { usersRepository.deleteAddressById(it.id) },
                    { usersRepository.persistAddress(userId, it.address!!) }
                )
            }
            eventsResponse.calendarEvents?.forEach {
                logger.d("usecase calendar events: ${it}")
                it.handleAction(
                    { calendarsRepository.deleteEventById(it.id) },
                    {

                        // TODO optimise this so we don't fetch unnecessary events outside of desired window

                        // after "event metadata migration", we need to fetch events separately
                        val singleEventResponse = calendarsApi.getEvent(it.event!!.calendarId, it.event!!.id)
//                        logger.e("single event = ${singleEventResponse}")

                        // TODO MOVE THIS TO SEPARATE USECASE
                        when (singleEventResponse) {
                            is ApiResponse.Success -> calendarsRepository.persistEvents(singleEventResponse.data.event)
                            is ApiResponse.Error -> throw Exception(singleEventResponse.error)
                            is ApiResponse.Exception -> throw Exception(singleEventResponse.exception)
                        }

//                        calendarsRepository.persistEvents(it.event!!)

                        // TODO move this to worker
                        try {
                            val emails = /* TODO GSON is causing trouble here, make this pretty*/
                                (if (it.event.sharedEvents?.isNotEmpty() == true) it.event.sharedEvents.map { it.asJsonObject.get("Author").asString } else emptyList()) +
                                (if (it.event.calendarEvents?.isNotEmpty() == true) it.event.calendarEvents.map { it.asJsonObject.get("Author").asString } else emptyList()) +
                                (if (it.event.personalEvents?.isNotEmpty() == true) it.event.personalEvents.map { it.asJsonObject.get("Author").asString } else emptyList())

                            emails.distinct().forEach {
                                fetchPublicKeysUseCase.execute(it)
                            }
                        } catch (e: IllegalStateException) {
                            logger.e("error getting event's author from JSON")
                        }
                    }
                )
            }
            eventsResponse.calendarAlarms?.forEach {
                it.handleAction(
                    { calendarsRepository.deleteEventAlarmById(it.id) },
                    { calendarsRepository.persistEventAlarm(it.alarm!!) }
                )
            }
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
