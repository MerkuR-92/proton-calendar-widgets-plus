package me.proton.android.calendar.domain.usecase

import com.google.gson.Gson
import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.domain.*
import me.proton.android.calendar.domain.api.AddressesApi
import me.proton.android.calendar.domain.api.CalendarsApi
import me.proton.android.calendar.domain.api.KeysApi
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime

class FetchEventsUseCase( // TODO TESTS, ALSO FOR MERGING MULTIPLE CALENDARS
    private val logger: Logger,
    private val gson: Gson,
    private val calendarsApi: CalendarsApi,
    private val addressesApi: AddressesApi,
    private val keysApi: KeysApi,
    private val crypto: Crypto,
    private val fetchPublicKeysUseCase: FetchPublicKeysUseCase,
    private val calendarsRepository: CalendarsRepository): UseCase {

    suspend fun execute(userId: String, calendarId: String /*TODO list of calendarIds*/ /*TODO TYPE: YEAR, START: 2020 <- something like this*/) : UseCase.Result {

        logger.v("executing FetchEventsUseCase")

        // TODO hardcoded timestamps and timezone
//        val eventsResponse = calendarsApi.getEvents(/*userId,*/ calendarId, 1580515200,1585526400, "Europe/Paris")
        // TODO eventually supply local dates and timezone so we can convert them to millis here in worker
        val eventsResponse = calendarsApi.getEvents(/*userId,*/ calendarId, LocalDateTime.now().minusDays(1).toEpochSecond(
            ZoneOffset.UTC),LocalDateTime.now().plusDays(7).toEpochSecond(
            ZoneOffset.UTC), "Europe/Paris")
        return if (eventsResponse is ApiResponse.Success) {
            calendarsRepository.persistEvents(*eventsResponse.data.events.toTypedArray())
            logger.v("persisted events for calendar $calendarId")

            // TODO move this to worker
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

    }

}
