package me.proton.android.calendar.domain.usecase

import me.proton.android.calendar.data.api.ServerEvent
import me.proton.android.calendar.data.api.ServerEventsApiResponse
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.UsersRepository
import me.proton.android.calendar.domain.Logger
import java.lang.Exception

class HandleServerEventsUseCase(
    private val logger: Logger,
    private val calendarsRepository: CalendarsRepository,
    private val usersRepository: UsersRepository,
    private val cacheCalendarPassphraseUseCase: CacheCalendarPassphraseUseCase,
    private val fetchPublicKeysUseCase: FetchPublicKeysUseCase) : UseCase {

    suspend fun execute(eventsResponse: ServerEventsApiResponse, userId: String) : UseCase.Result {

        logger.v("handling server events in usecase")

        return try {
            eventsResponse.user?.let {
                usersRepository.persistUser(it)
            }
            eventsResponse.calendars?.forEach {
                it.handleAction(
                    { calendarsRepository.deleteCalendarById(it.id) },
                    { calendarsRepository.persistCalendar(userId, it.calendar!!) }
                )
            }
            eventsResponse.addresses?.forEach {
                it.handleAction(
                    { usersRepository.deleteAddressById(it.id) },
                    { usersRepository.persistAddress(userId, it.address!!) }
                )
            }
            eventsResponse.calendarEvents?.forEach {
                it.handleAction(
                    { calendarsRepository.deleteEventById(it.id) },
                    {
                        calendarsRepository.persistEvents(it.event!!)

                        // TODO move this to worker
                        try {
                            val emails =
                                it.event.sharedEvents.map { it.asJsonObject.get("Author").asString } +
                                it.event.calendarEvents.map { it.asJsonObject.get("Author").asString } +
                                it.event.personalEvents.map { it.asJsonObject.get("Author").asString }

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
