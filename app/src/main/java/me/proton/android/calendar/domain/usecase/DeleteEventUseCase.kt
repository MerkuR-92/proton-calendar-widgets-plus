package me.proton.android.calendar.domain.usecase

import com.google.gson.Gson
import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.domain.*
import me.proton.android.calendar.domain.api.AddressesApi
import me.proton.android.calendar.domain.api.CalendarsApi
import me.proton.android.calendar.domain.api.KeysApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

class DeleteEventUseCase( // TODO TESTS
    private val logger: Logger, // TODO remove unnecessary dependencies
    private val gson: Gson,
    private val calendarsApi: CalendarsApi,
    private val addressesApi: AddressesApi,
    private val keysApi: KeysApi,
    private val crypto: Crypto,
    private val valueStoreProvider: ValueStoreProvider,
    private val calendarsRepository: CalendarsRepository): UseCase {

    suspend fun execute(eventId: String) : UseCase.Result {

        logger.v("executing DeleteEventUseCase")

        val eventEntity = calendarsRepository.selectEventEntity(eventId) ?: return UseCase.Result.InvalidParams("event $eventId doesn't exist in DB")

        val eventsResponse = calendarsApi.deleteEvent(eventEntity.calendarId, eventEntity.id)
        return if (eventsResponse is ApiResponse.Success) {
            //calendarsRepository.deleteEventById(eventEntity.id) // TODO see if /events handle works
            logger.v("successfully deleted event in API: ${eventEntity.id}")
            UseCase.Result.Success
        } else {
            UseCase.Result.Error("error deleting event in API: $eventsResponse")
        }

    }

}