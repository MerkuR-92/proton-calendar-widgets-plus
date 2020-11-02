package me.proton.android.calendar.domain.usecase

import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.entity.CalendarEntity
import me.proton.android.calendar.domain.*
import me.proton.android.calendar.domain.api.CalendarsApi
import me.proton.core.domain.entity.UserId
import java.time.*
import java.time.temporal.ChronoUnit


class SyncAlarmsUseCase(
    private val logger: Logger,
    private val valueStoreProvider: ValueStoreProvider,
    private val calendarsApi: CalendarsApi,
    private val calendarsRepository: CalendarsRepository
): UseCase {

    companion object {
        const val WORKER_ID = "SYNC_ALARMS"
    }

    private val ALARMS_CACHE_OVERLAP_WINDOW_SIZE = Duration.ofDays(3) // minimum time that has to pass after last sync
    private val ALARMS_CACHE_WINDOW_SIZE = Duration.ofDays(14)
    private val ALARMS_CACHE_STEP_SIZE = Duration.ofDays(5)
    private val ALARMS_REQUEST_PAGE_SIZE = 100 // server supports maximum 100

    suspend fun execute(userId: UserId): UseCase.Result {

        logger.v("executing SyncAlarmsUseCase")

        val calendarIds = calendarsRepository.selectCalendars(userId.id)
        val valueStore = valueStoreProvider.provideValueStore(userId.id)

        val results = calendarIds.map {

            val syncStartDate = ZonedDateTime.now()

            // skip sync for this calendar if last successful sync happend recently
            val lastSuccessfulSyncTimestamp = valueStore.getLongFromSet(ValueSet.LAST_CALENDAR_ALARM_SYNC_SUCCESS_TIMESTAMP, it.id)
            val lastSuccessfulSyncDate = ZonedDateTime.ofInstant(Instant.ofEpochSecond(lastSuccessfulSyncTimestamp ?: 0L), ZoneId.of("UTC"))
            if (lastSuccessfulSyncDate.plus(ALARMS_CACHE_OVERLAP_WINDOW_SIZE).isBefore(syncStartDate)) {
                logger.v("have to sync alarms calendar ${it.name}, at $syncStartDate")
                val result = handleCalendarAlarms(userId, it)
                if (result == UseCase.Result.Success) {
                    logger.v("success syncing alarms for calendar ${it.name}, writing timestamp $syncStartDate")
                    valueStore.putLongInSet(ValueSet.LAST_CALENDAR_ALARM_SYNC_SUCCESS_TIMESTAMP, it.id, syncStartDate.toEpochSecond())
                }

                result
            } else {
                logger.v("no need for sync of calendar ${it.name} at $syncStartDate")
                UseCase.Result.Success
            }
        }

        val success = results.all { it is UseCase.Result.Success }

        logger.v("syncing alarms result = $results")

        return if (success) {
            UseCase.Result.Success
        } else {
            results.firstOrNull { it !is UseCase.Result.Success } ?: UseCase.Result.Error("error getting result from SyncAlarmsUseCase")
        }
    }

    private suspend fun handleCalendarAlarms(userId: UserId, calendarEntity: CalendarEntity): UseCase.Result {

        val start = LocalDateTime.now().minusHours(1) // magic number for local time drift
        val end = start.plus(ALARMS_CACHE_WINDOW_SIZE)
        var windowStart = start
        var windowEnd = windowStart.plus(ALARMS_CACHE_STEP_SIZE)

        do {
            var hasMore = false

            when (val alarmsResponse = calendarsApi.getAlarms(userId, calendarEntity.id, windowStart.toEpochSecond(ZoneOffset.UTC), windowEnd.toEpochSecond(ZoneOffset.UTC), ALARMS_REQUEST_PAGE_SIZE)) {
                is ApiResponse.Success -> {
                    logger.v("fetched alarms for: ${calendarEntity.name}")
                    logger.v("alarms response: ${alarmsResponse.data}")

                    // if we got as many alarms as we requested, it's possible that not all of them
                    //  fit into the requested page size, and that is fixed so let's half the requested time period
                    if (alarmsResponse.data.alarms.size == ALARMS_REQUEST_PAGE_SIZE) {

                        logger.v("alarms got page size, halving")

                        val lastWindowMinutes = ChronoUnit.MINUTES.between(windowStart, windowEnd)

                        logger.v("alarms last minutes between: $lastWindowMinutes")

                        if (lastWindowMinutes > 1) {
                            logger.v("alarms halving and using minutes between: ${lastWindowMinutes / 2}")

                            windowEnd = windowStart.plusMinutes(lastWindowMinutes / 2)
                            hasMore = true
                            continue
                        } // else give up halving, process all the events from that 1-minute window and continue

                        logger.i("gave up halving alarms")
                    }

                    alarmsResponse.data.alarms.forEach { alarmEntity ->
                        logger.v("alarm: ${alarmEntity}")

                        if (!calendarsRepository.hasCalendar(alarmEntity.calendarId))           {
                            // Calendar doesn't exist locally, silently fail
                            logger.e("calendar ${alarmEntity.calendarId} doesn't exist in DB, can't insert alarm")
                        } else if (!calendarsRepository.hasEvent(alarmEntity.eventId, alarmEntity.calendarId)) {
                            logger.v("event ${alarmEntity.eventId} for alarm doesn't exist in DB")
                            // event doen's exist locally, fetch and save it before inserting alarm
                            when (val event = calendarsApi.getEvent(userId, alarmEntity.calendarId, alarmEntity.eventId)) {
                                is ApiResponse.Success -> {
                                    logger.v("event ${alarmEntity.eventId} for alarm successfully fetched")
                                    calendarsRepository.persistEvents(event.data.event)
                                    calendarsRepository.persistEventAlarm(alarmEntity)
                                }
                                // TODO maybe ignore some errors like non-existing Event, but let's see what kind of error reports we get
                                is ApiResponse.Error -> {
                                    logger.e("couldn't fetch event ${alarmEntity.eventId} for alarm: ${event.errorCode}, ${event.error}")
                                    return UseCase.Result.Error("could not fetch missing event for alarm: ${event.errorCode}, ${event.error}")
                                }
                                is ApiResponse.Exception -> return UseCase.Result.Error("could not fetch missing event for alarm: ${event.exception}k")
                            }
                        } else {
                            calendarsRepository.persistEventAlarm(alarmEntity)
                        }
                    }

                    logger.v("alarm window before adjusting => ${windowStart}-${windowEnd}, end = $end")

                    // move window and continue
                    if (windowEnd.isBefore(end)) {
                        hasMore = true

                        windowStart = windowEnd
                        windowEnd = windowEnd.plus(ALARMS_CACHE_STEP_SIZE)
                    }

                    logger.v("alarm window after adjusting => ${windowStart}-${windowEnd}, end = $end")
                }
                is ApiResponse.Error -> return UseCase.Result.Error("api error getting server events: $alarmsResponse")
                is ApiResponse.Exception -> return UseCase.Result.Error("exception getting server events: $alarmsResponse")
            }

        } while (hasMore)

        return UseCase.Result.Success
    }

}
