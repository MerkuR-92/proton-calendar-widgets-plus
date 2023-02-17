@file:OptIn(ExperimentalCoroutinesApi::class)

package me.proton.android.calendar.domain.usecase

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.coroutineScope
import me.proton.android.calendar.common.FETCH_EVENTS_MAX_DAYS_WINDOW
import kotlinx.coroutines.launch
import me.proton.android.calendar.common.utils.isNotFound
import me.proton.android.calendar.common.utils.isTimeout
import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.api.logErrorIfNeeded
import me.proton.android.calendar.data.entity.EventEntity
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.api.CalendarsApi
import me.proton.core.domain.entity.UserId
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject

class FetchEventsUseCase @Inject constructor( // TODO TESTS, ALSO FOR MERGING MULTIPLE CALENDARS
    private val logger: Logger,
    private val calendarsApi: CalendarsApi
) : UseCase {

    suspend fun splitFetchEvents(
        userId: UserId,
        calendarIds: List<String>,
        fromDate: LocalDate,
        toDate: LocalDate,
        timeZoneId: String
    ): Pair<UseCase.Result, List<EventEntity>?> { // TODO introduce new type of result with payload

        val daysInTimeWindow = ChronoUnit.DAYS.between(fromDate, toDate)
        val timeWindows = arrayListOf<Pair<LocalDate, LocalDate>>()
        if (daysInTimeWindow > FETCH_EVENTS_MAX_DAYS_WINDOW) {
            var timeWindowsCount = 0
            while (timeWindowsCount < kotlin.math.ceil(daysInTimeWindow / FETCH_EVENTS_MAX_DAYS_WINDOW.toDouble())) {
                val newFromDate = fromDate.plusDays(FETCH_EVENTS_MAX_DAYS_WINDOW.toLong() * timeWindowsCount)
                val newToDate = fromDate.plusDays(FETCH_EVENTS_MAX_DAYS_WINDOW.toLong() * (timeWindowsCount + 1))
                timeWindows.add(
                    Pair(
                        newFromDate,
                        if (newToDate.isAfter(toDate)) toDate else newToDate
                    )
                )
                timeWindowsCount++
            }
        } else {
            timeWindows.add(
                Pair(fromDate, toDate)
            )
        }

        val results = coroutineScope {
            timeWindows.map { timeWindow ->
                async {
                    fetchEvents(userId, calendarIds, timeWindow.first, timeWindow.second, timeZoneId)
                }
            }.awaitAll()
        }

        return if (results.all { it.first is UseCase.Result.Success<*> }) {
            Pair(
                UseCase.Result.Success<Unit>(),
                results.flatMap { it.second ?: arrayListOf() }
            )
        } else {
            Pair(
                UseCase.Result.Error("error fetching events"),
                null
            )
        }
    }

    private suspend fun fetchEvents(
        userId: UserId,
        calendarIds: List<String>,
        fromDate: LocalDate,
        toDate: LocalDate,
        timeZoneId: String
    ): Pair<UseCase.Result, List<EventEntity>?> { // TODO introduce new type of result with payload

        logger.v("executing FetchEventsUseCase")

        val combinedResults = coroutineScope {

            calendarIds.map { calendarId ->
                async {
                    val resultsEventsPairs = (0..3).map { type -> // we need to fire off 4 requests with different types

                        async {
                            var page = 0
                            var pageSize = 64 // initial page size has to be power of 2, because it's going to be divided by 2 if needed
                            var shouldRetryOnTimeout = false
                            val results = mutableListOf<UseCase.Result>()
                            val events = mutableListOf<EventEntity>()

                            do {

                                shouldRetryOnTimeout = false

                                val eventsResponse = calendarsApi.getEvents(
                                    userId,
                                    calendarId,
                                    fromDate.atStartOfDay(ZoneId.of(timeZoneId)).toEpochSecond(),
                                    toDate.plusDays(1).atStartOfDay(ZoneId.of(timeZoneId)).toEpochSecond(),
                                    timeZoneId,
                                    type,
                                    page++,
                                    pageSize
                                )

                                val result = if (eventsResponse is ApiResponse.Success) {

                                    logger.v("more: ${eventsResponse.data.more}")

                                    events.addAll(eventsResponse.data.events)

                                    UseCase.Result.Success<Unit>()
                                } else if (eventsResponse is ApiResponse.Error) {
                                    if (eventsResponse.isNotFound()) {
                                        logger.e("NOT_FOUND requesting events in FetchEventsUseCase")
                                        UseCase.Result.Success<Unit>()
                                    } else if (eventsResponse.isTimeout()) {

                                        pageSize = pageSize / 2
                                        // subtract 2 in order to go back "1 previous page" == "2 new pages"
                                        page = page * 2 - 2

                                        shouldRetryOnTimeout = pageSize > 1

                                        if (shouldRetryOnTimeout) {
                                            continue // don't add timeout error to "results" just yet
                                        } else {
                                            logger.e("timeout fetching events for calendar with pageSize $pageSize")
                                            UseCase.Result.Error("timeout fetching events")
                                        }

                                    } else {
                                        eventsResponse.logErrorIfNeeded("api error fetching events for calendar", logger)
                                        UseCase.Result.Error("api error in FetchEventsUseCase: ${eventsResponse}")
                                    }
                                } else {
                                    eventsResponse.logErrorIfNeeded("error fetching events for calendar", logger)
                                    UseCase.Result.Error("error fetching events for calendar: $eventsResponse")
                                }

                                results.add(result)

                            } while (shouldRetryOnTimeout || eventsResponse is ApiResponse.Success && eventsResponse.data.more == 1)

                            Pair(results, events)
                        }

                    }.awaitAll()

                    //fetchPublicKeysUseCase.enqueueFetchPublicKeys(userId, resultsEventsPairs.flatMap { it.second })

                    Pair(resultsEventsPairs.flatMap { it.first }, resultsEventsPairs.flatMap { it.second })
                }
            }

        }.awaitAll()

        return if (combinedResults.all { it.first.all { it is UseCase.Result.Success<*> } }) {
            Pair(
                UseCase.Result.Success<Unit>(),
                combinedResults.flatMap { it.second }
            )
        } else {
            Pair(
                UseCase.Result.Error("error fetching events"),
                null
            )
        } // TODO which calendar?
    }

    /**
     * @param lastKnownEventId in case we want to resume export starting after this Event ID
     * @return EventEntities in batches, consume with one worker to maintain the order!
     */
    suspend fun execute(
        userId: UserId,
        calendarId: String,
        lastKnownEventId: String?,
        coroutineScope: CoroutineScope
    ): ReceiveChannel<List<EventEntity>> {

        // eventIdsRequestPageSize has to be evenly divisible by (workerCount * workerBatchSize)!
        val workerCount = 5
        val workerBatchSize = 5
        val eventIdsRequestPageSize = 200

        val eventIdsChannel = Channel<List<String>>(1)
        val eventEntitiesChannel = Channel<List<EventEntity>>(5)

        coroutineScope.launch {

            var afterId = lastKnownEventId

            // Event IDs downloaded in one call
            var batchOfIds = emptyList<String>()

            launch { // PRODUCE Event IDs

                do {

                    when (val response = calendarsApi.getEventIdsForExport(userId, calendarId, eventIdsRequestPageSize, afterId)) {
                        is ApiResponse.Error -> {
                            response.logErrorIfNeeded("[FetchEventsUseCase] error in getEventIdsForExport", logger)
                            eventIdsChannel.close(Exception("Error in getEventIdsForExport"))
                            break
                        }
                        is ApiResponse.Exception -> {
                            response.logErrorIfNeeded("[FetchEventsUseCase] exception in getEventIdsForExport", logger)
                            eventIdsChannel.close(Exception("Exception in getEventIdsForExport", response.exception))
                            break
                        }
                        is ApiResponse.Success -> {

                            batchOfIds = response.data.events
                            afterId = batchOfIds.lastOrNull()

                            if (eventIdsChannel.isClosedForSend.not()) eventIdsChannel.send(batchOfIds)
                        }
                    }

                } while (batchOfIds.isNotEmpty())

                eventIdsChannel.close()

            }

        }

        coroutineScope.launch {
            for (eventIds in eventIdsChannel) { // CONSUME Event IDs

                // we have to fetch EventEntites somewhat synchronously, because we need to maintain order
                // of Event IDs in downloaded Event batches we publish to consumer

                eventIds.chunked(workerCount * workerBatchSize).map { chunkForAllWorkers ->

                    val fetchedEntities = chunkForAllWorkers.chunked(workerBatchSize).map { eventIds ->
                        async {
                            eventIds.mapNotNull { eventId ->
                                when (val eventResponse = calendarsApi.getEvent(userId, calendarId, eventId)) {
                                    is ApiResponse.Error -> if (eventResponse.isNotFound()) {
                                        null // legitimate situation if Event was deleted in the meantime
                                    } else {
                                        eventResponse.logErrorIfNeeded("[FetchEventsUseCase] error in fetching chunked entities", logger)
                                        eventEntitiesChannel.close(Exception("Error in fetching chunked entities"))
                                        null
                                    }
                                    is ApiResponse.Exception -> {
                                        eventResponse.logErrorIfNeeded("[FetchEventsUseCase] exception in fetching chunked entities", logger)
                                        eventEntitiesChannel.close(Exception("Exception in fetching chunked entities"))
                                        null
                                    }
                                    is ApiResponse.Success -> {
                                        eventResponse.data.event
                                    }
                                }
                            }
                        }
                    }.awaitAll().flatten()

                    if (!eventEntitiesChannel.isClosedForSend) {
                        eventEntitiesChannel.send(fetchedEntities) // PRODUCE Event Entities
                    }

                }

            }

            eventEntitiesChannel.close()
        }

        return eventEntitiesChannel

    }

}
