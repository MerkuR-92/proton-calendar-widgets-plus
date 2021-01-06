package me.proton.android.calendar.domain.usecase

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.EventEntity
import me.proton.android.calendar.domain.Crypto
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.api.AddressesApi
import me.proton.android.calendar.domain.api.CalendarsApi
import me.proton.android.calendar.domain.api.KeysApi
import me.proton.core.domain.entity.UserId
import java.time.LocalDate
import java.time.ZoneId

class FetchEventsUseCase( // TODO TESTS, ALSO FOR MERGING MULTIPLE CALENDARS
    private val logger: Logger,
    private val calendarsApi: CalendarsApi,
    private val addressesApi: AddressesApi,
    private val keysApi: KeysApi,
    private val crypto: Crypto,
    private val fetchPublicKeysUseCase: FetchPublicKeysUseCase,
    private val database: AppDatabase
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
                            val results = mutableListOf<UseCase.Result>()
                            val events = mutableListOf<EventEntity>()

                            do {

                                val eventsResponse = calendarsApi.getEvents(
                                    userId,
                                    calendarId,
                                    fromDate.atStartOfDay(ZoneId.of(timeZoneId)).toEpochSecond(),
                                    toDate.plusDays(1).atStartOfDay(ZoneId.of(timeZoneId)).toEpochSecond(),
                                    timeZoneId,
                                    type,
                                    page++,
                                    100
                                )

                                val result = if (eventsResponse is ApiResponse.Success) {

                                    logger.v("more: ${eventsResponse.data.more}")

                                    events.addAll(eventsResponse.data.events)

                                    UseCase.Result.Success
                                } else {
                                    logger.e("error fetching events for calendar: $eventsResponse")
                                    UseCase.Result.Error("error fetching events for calendar: $eventsResponse")
                                }

                                results.add(result)

                            } while (eventsResponse is ApiResponse.Success && eventsResponse.data.more == 1)

                            Pair(results, events)
                        }

                    }.awaitAll()

                    fetchPublicKeysUseCase.execute(userId, resultsEventsPairs.flatMap { it.second })

                    Pair(resultsEventsPairs.flatMap { it.first }, resultsEventsPairs.flatMap { it.second })
                }
            }

        }.awaitAll()

        return if (combinedResults.all { it.first.all { it == UseCase.Result.Success } }) Pair(
            UseCase.Result.Success,
            combinedResults.flatMap { it.second }) else Pair(
            UseCase.Result.Error("error fetching events"),
            null
        ) // TODO which calendar?

    }

}
