package me.proton.android.calendar.domain.usecase

import me.proton.android.calendar.WidgetRefresher
import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.entity.CalendarEntity
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.ValueSet
import me.proton.android.calendar.domain.ValueStoreProvider
import me.proton.android.calendar.domain.api.CalendarsApi
import me.proton.android.calendar.domain.api.ServerEventsApi
import me.proton.core.domain.entity.UserId
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

/**
 * Sets up all the user's calendars, call this only once after successful login.
 */
class BootstrapCalendarUseCase @Inject constructor( // TODO TEST
    private val logger: Logger,
    private val calendarsApi: CalendarsApi,
    private val calendarsRepository: CalendarsRepository,
    private val cacheCalendarPassphraseUseCase: CacheCalendarPassphraseUseCase,
    private val fetchEventsUseCase: FetchEventsUseCase,
    private val updateAlarmsUseCase: UpdateAlarmsUseCase,
    private val serverEventsApi: ServerEventsApi,
    private val valueStoreProvider: ValueStoreProvider,
    private val widgetRefresher: WidgetRefresher
): UseCase {

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
                        val fetchEventsResult = fetchEventsUseCase.splitFetchEvents(
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
                                    widgetRefresher.refreshEventList()
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
