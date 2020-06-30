package me.proton.android.calendar.domain.usecase

import com.google.gson.Gson
import me.proton.android.calendar.common.TimberLogger
import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.domain.*
import me.proton.android.calendar.domain.api.AddressesApi
import me.proton.android.calendar.domain.api.CalendarsApi
import me.proton.android.calendar.domain.api.KeysApi
import java.time.*

class FetchEventsUseCase( // TODO TESTS, ALSO FOR MERGING MULTIPLE CALENDARS
    private val logger: Logger,
    private val gson: Gson,
    private val calendarsApi: CalendarsApi,
    private val addressesApi: AddressesApi,
    private val keysApi: KeysApi,
    private val crypto: Crypto,
    private val fetchPublicKeysUseCase: FetchPublicKeysUseCase,
    private val calendarsRepository: CalendarsRepository): UseCase {

    suspend fun execute(userId: String, calendarIds: List<String>, fromDate: LocalDate, toDate: LocalDate, timeZoneId: String) : UseCase.Result {

        // TODO see if we should pass coroutinescope, so if worker gets cancelled, all operations continue anyway (is this needed?)

        logger.v("executing FetchEventsUseCase")

        //withContext()

        val results = mutableListOf<UseCase.Result>()

        calendarIds.forEach { calendarId ->

            for (type in 0..3) { // we need to fire off 4 requests with different types

                var page = 0

                do {

                    val eventsResponse = calendarsApi.getEvents(/*userId,*/
                        calendarId,
                        fromDate.atStartOfDay(ZoneId.of(timeZoneId)).toEpochSecond(),
                        toDate.plusDays(1).atStartOfDay(ZoneId.of(timeZoneId)).toEpochSecond(),
                        timeZoneId,
                        type,
                        page++,
                        1
                    )

                    val result = if (eventsResponse is ApiResponse.Success) {

                        TimberLogger.e("more: ${eventsResponse.data.more}")

                        calendarsRepository.persistEvents(*eventsResponse.data.events.toTypedArray())

                        logger.v("persisted ${eventsResponse.data.events.size} events for calendar ${calendarId}")

                        // TODO collect all emails and move this to worker
                        try {
                            val emails = eventsResponse.data.events.flatMap {
                                it.sharedEvents.map { it.asJsonObject.get("Author").asString } +
                                        it.calendarEvents.map { it.asJsonObject.get("Author").asString } +
                                        it.personalEvents.map { it.asJsonObject.get("Author").asString }
                            }
                            emails.distinct().forEach {
                                fetchPublicKeysUseCase.execute(it)
                            }
                        } catch (e: IllegalStateException) {
                            logger.e("error getting event's author from JSON")
                        }

                        UseCase.Result.Success
                    } else {
                        UseCase.Result.Error("error fetching events for calendar: $eventsResponse")
                    }

                    results.add(result)

                } while (eventsResponse is ApiResponse.Success && eventsResponse.data.more == 1)

            }


        }

        return if (results.all { it == UseCase.Result.Success }) UseCase.Result.Success else UseCase.Result.Error("error fetching events") // TODO which calendar?


    }

}
