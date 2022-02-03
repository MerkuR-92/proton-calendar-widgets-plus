package me.proton.android.calendar.domain.usecase

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import me.proton.android.calendar.common.utils.isTimeout
import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.api.logErrorIfNeeded
import me.proton.android.calendar.data.entity.EventEntity
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.api.CalendarsApi
import me.proton.core.domain.entity.UserId
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

class FetchEventsUseCase @Inject constructor( // TODO TESTS, ALSO FOR MERGING MULTIPLE CALENDARS
    private val logger: Logger,
    private val calendarsApi: CalendarsApi,
    private val fetchPublicKeysUseCase: FetchPublicKeysUseCase,
) : UseCase {

    suspend fun execute(
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
                                    if (eventsResponse.httpCode == 404) {
                                        logger.e("404 requesting events in FetchEventsUseCase")
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

                    fetchPublicKeysUseCase.enqueueFetchPublicKeys(userId, resultsEventsPairs.flatMap { it.second })

                    Pair(resultsEventsPairs.flatMap { it.first }, resultsEventsPairs.flatMap { it.second })
                }
            }

        }.awaitAll()

        return if (combinedResults.all { it.first.all { it is UseCase.Result.Success<*> } }) Pair(
            UseCase.Result.Success<Unit>(),
            combinedResults.flatMap { it.second }) else Pair(
            UseCase.Result.Error("error fetching events"),
            null
        ) // TODO which calendar?

    }

}
