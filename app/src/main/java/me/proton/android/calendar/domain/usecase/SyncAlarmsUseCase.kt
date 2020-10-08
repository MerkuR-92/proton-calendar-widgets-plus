package me.proton.android.calendar.domain.usecase

import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.entity.CalendarEntity
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.ValueKey
import me.proton.android.calendar.domain.ValueStoreProvider
import me.proton.android.calendar.domain.api.CalendarsApi
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
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

    private val ALARMS_CACHE_WINDOW_SIZE = Duration.ofDays(14)
    private val ALARMS_CACHE_STEP_SIZE = Duration.ofDays(5)
    private val ALARMS_REQUEST_PAGE_SIZE = 100 // server supports maximum 100

    suspend fun execute(userId: String): UseCase.Result {

        logger.v("executing SyncAlarmsUseCase")

        val calendarIds = calendarsRepository.selectCalendars(userId)

//        val syncStart = ZonedDateTime.now().toEpochSecond()

        val results = calendarIds.map {
            handleCalendarAlarms(it)
        }

        val success = results.all { it is UseCase.Result.Success }

        logger.v("syncing alarms result = $results")

        return if (success) {

//            val valueStore = valueStoreProvider.provideValueStore(userId)
//            valueStore.putLong(ValueKey.LAST_ALARM_SYNC_SUCCESS, syncStart)

            UseCase.Result.Success
        } else {
            results.firstOrNull { it !is UseCase.Result.Success } ?: UseCase.Result.Error("error getting result from SyncAlarmsUseCase")
        }
    }

    private suspend fun handleCalendarAlarms(calendarEntity: CalendarEntity): UseCase.Result {

        val start = LocalDateTime.now().minusHours(1) // magic number for local time drift
        val end = start.plus(ALARMS_CACHE_WINDOW_SIZE)
        var windowStart = start
        var windowEnd = windowStart.plus(ALARMS_CACHE_STEP_SIZE)

        val processedAlarmIds = mutableSetOf<String>()

        do {
            var hasMore = false

            when (val alarmsResponse = calendarsApi.getAlarms(calendarEntity.id, windowStart.toEpochSecond(ZoneOffset.UTC), windowEnd.toEpochSecond(ZoneOffset.UTC), ALARMS_REQUEST_PAGE_SIZE)) {
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
                        calendarsRepository.persistEventAlarm(alarmEntity)

                        processedAlarmIds.add(alarmEntity.id)

                        // TODO FETCH EVENT IF IT DOESN'T EXIST IN LOCAL DATABASE

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
