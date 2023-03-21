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
import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.api.ServerEvent
import me.proton.android.calendar.data.api.logErrorIfNeeded
import me.proton.android.calendar.data.db.AppDatabase
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
    private val calendarsApi: CalendarsApi,
    private val database: AppDatabase
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

        var results: List<Pair<UseCase.Result, List<EventEntity>?>> = emptyList()

        try {
            coroutineScope {
                launch {
                    results = timeWindows.map { timeWindow ->
                        async {
                            fetchEventEntitiesLocalOrRemote(userId, calendarIds, timeWindow.first, timeWindow.second, timeZoneId, this)
                        }
                    }.awaitAll()
                }
            }
        } catch (e: Exception) {
            logger.e("Exception in splitFetchEvents", e)
            results = emptyList()
        }

        return if (results.isNotEmpty() && results.all { it.first is UseCase.Result.Success<*> }) {
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

    /**
     * Fetch events sequentially for the purpose of exporting all Events in a Calendar. They are
     * returned in batches always in order, so we need to supply [lastKnownEventId] to get the next
     * batch.
     *
     * @param lastKnownEventId in case we want to resume export starting after this Event ID
     * @return EventEntities in batches, consume with one worker to maintain the order!
     */
    suspend fun fetchForExport(
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

    /**
     * Fetch [EventEntity] in given range if needed, or return from local DB.
     */
    private suspend fun fetchEventEntitiesLocalOrRemote(
        userId: UserId,
        calendarIds: List<String>,
        fromDate: LocalDate,
        toDate: LocalDate,
        timeZoneId: String,
        coroutineScope: CoroutineScope
    ): Pair<UseCase.Result, List<EventEntity>?> {

        var result: Pair<UseCase.Result, List<EventEntity>?>

        try {

            coroutineScope {

                val eventEntitiesChannel = fetchMetadataOnly(
                    userId,
                    calendarIds,
                    fromDate,
                    toDate,
                    timeZoneId,
                    coroutineScope
                ).fetchRemoteEventEntities(
                    userId,
                    coroutineScope
                )

                val eventEntitiesResult = mutableListOf<EventEntity>()

                result = try {
                    for (eventEntities in eventEntitiesChannel) {
                        eventEntitiesResult.addAll(eventEntities)
                    }
                    Pair(UseCase.Result.Success<Unit>(), eventEntitiesResult)
                } catch (e: Exception) {
                    Pair(UseCase.Result.Error("Error in fetchEventEntitiesLocalOrRemote ${e.message}"), null)
                }

            }

        } catch (e: Exception) {
            return Pair(UseCase.Result.Error("Error in fetchEventEntitiesLocalOrRemote ${e.message}"), null)
        }

        return result
    }

    /**
     * @throws Exception
     */
    private suspend fun fetchMetadataOnly(
        userId: UserId,
        calendarIds: List<String>,
        fromDate: LocalDate,
        toDate: LocalDate,
        timeZoneId: String,
        coroutineScope: CoroutineScope
    ): ReceiveChannel<List<ServerEvent.EventEntityMetadata>> {

        val eventMetadatasChannel = Channel<List<ServerEvent.EventEntityMetadata>>(8)

        coroutineScope.launch {

            calendarIds.map { calendarId ->

                async {
                    (0..3).map { type -> // we need to fire off 4 requests with different types

                        async {
                            var page = 0

                            do {

                                val eventsResponse = calendarsApi.getEventsMetadata(
                                    userId,
                                    calendarId,
                                    fromDate.atStartOfDay(ZoneId.of(timeZoneId)).toEpochSecond(),
                                    toDate.plusDays(1).atStartOfDay(ZoneId.of(timeZoneId)).toEpochSecond(),
                                    timeZoneId,
                                    type,
                                    page++,
                                    pageSize = 64
                                )

                                if (eventsResponse is ApiResponse.Success) {

                                    if (!eventMetadatasChannel.isClosedForSend && eventsResponse.data.events.isNotEmpty()) {
                                        eventMetadatasChannel.send(eventsResponse.data.events) // PRODUCE
                                    }

                                } else if (eventsResponse is ApiResponse.Error) {
                                    if (eventsResponse.isNotFound()) {
                                        logger.i("NOT_FOUND requesting events in fetchMetadataOnly, type = $type")
                                    } else {
                                        eventsResponse.logErrorIfNeeded("api error fetching events for calendar in fetchMetadataOnly", logger)
                                        eventMetadatasChannel.close(Exception("api error in fetchMetadataOnly: ${eventsResponse}"))
                                    }
                                } else {
                                    eventsResponse.logErrorIfNeeded("error fetching events for calendar in ", logger)
                                    eventMetadatasChannel.close(Exception("error fetching events for calendar in fetchMetadataOnly: $eventsResponse"))
                                }

                            } while (eventsResponse is ApiResponse.Success && eventsResponse.data.more == 1)

                        }

                    }.awaitAll()
                }
            }.awaitAll()

            eventMetadatasChannel.close()

        }

        return eventMetadatasChannel
    }

    /**
     * @throws Exception
     */
    private suspend fun ReceiveChannel<List<ServerEvent.EventEntityMetadata>>.fetchRemoteEventEntities(
        userId: UserId,
        coroutineScope: CoroutineScope
    ): ReceiveChannel<List<EventEntity>> {

        val eventMetadatasChannel = this
        val eventEntitiesChannel = Channel<List<EventEntity>>(10)

        coroutineScope.launch {
            for (eventMetadatas in eventMetadatasChannel) { // CONSUME

                val eventEntities = eventMetadatas.map { metaData ->
                    async {

                        val dbEventEntity = database.eventsDao().selectEvent(metaData.id, metaData.calendarId)

                        // return EventEnity from DB if it's up to date, otherwise call API
                        if (dbEventEntity != null && metaData.modifyTime <= dbEventEntity.modifyTime) {
                            dbEventEntity
                        } else {
                            when (val apiEventEntity = calendarsApi.getEvent(
                                userId,
                                metaData.calendarId,
                                metaData.id
                            )) {
                                is ApiResponse.Error -> {
                                    if (!apiEventEntity.isNotFound()) {
                                        eventEntitiesChannel.close(Exception("Error fetching EventEntity in fetchAndPersistEventEntities (${apiEventEntity.error})"))
                                    }
                                    null
                                }
                                is ApiResponse.Exception -> {
                                    eventEntitiesChannel.close(Exception("Exception fetching EventEntity in fetchAndPersistEventEntities", apiEventEntity.exception))
                                    null
                                }
                                is ApiResponse.Success -> {
                                    apiEventEntity.data.event
                                }
                            }
                        }
                    }
                }.awaitAll().filterNotNull()

                if (!eventEntitiesChannel.isClosedForSend) {
                    eventEntitiesChannel.send(eventEntities) // PRODUCE
                }
            }

            eventEntitiesChannel.close()
        }

        return eventEntitiesChannel
    }

}
