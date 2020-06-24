package me.proton.android.calendar.domain.usecase

import com.google.gson.Gson
import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.domain.*
import me.proton.android.calendar.domain.api.AddressesApi
import me.proton.android.calendar.domain.api.CalendarsApi
import me.proton.android.calendar.domain.api.KeysApi
import me.proton.android.calendar.presentation.calendar.EventEditDeleteOption

class DeleteEventUseCase( // TODO TESTS
    private val logger: Logger, // TODO remove unnecessary dependencies
    private val gson: Gson,
    private val calendarsApi: CalendarsApi,
    private val addressesApi: AddressesApi,
    private val keysApi: KeysApi,
    private val crypto: Crypto,
    private val valueStoreProvider: ValueStoreProvider,
    private val calendarsRepository: CalendarsRepository): UseCase {

    suspend fun execute(eventId: String, deleteOption: EventEditDeleteOption) : UseCase.Result {

        // TODO migrate to /sync route and handle recurring deletes

        // TODO

        when (deleteOption) {
            EventEditDeleteOption.THIS_EVENT -> { // TODO this should also work for single, non-recurring event, right???????
                // 1st event in chain  or  Nth event in chain
                // add EXDATE to original event

                /*
                BEGIN:VCALENDAR
		    VERSION:2.0
		    BEGIN:VEVENT
		    DTSTART;VALUE=DATE:20200629
		    DTEND;VALUE=DATE:20200630
		    RRULE:FREQ=DAILY;COUNT=3
		    SUMMARY:(3) every day 3 times
		    UID:aWcnaxMH4KEIVAw_om4ZfJ89QbYJ@proton.me
		    DTSTAMP:20200624T112548Z
		    BEGIN:VALARM
		    TRIGGER:-PT15H
		    ACTION:DISPLAY
		    END:VALARM
		    END:VEVENT
		    END:VCALENDAR

			after deleting 2nd out of 3

			BEGIN:VCALENDAR
			    VERSION:2.0
			    BEGIN:VEVENT
			    DTSTART;VALUE=DATE:20200629
			    DTEND;VALUE=DATE:20200630
			    RRULE:FREQ=DAILY;COUNT=3
			    EXDATE;VALUE=DATE:20200630
			    SUMMARY:(3) every day 3 times
			    UID:aWcnaxMH4KEIVAw_om4ZfJ89QbYJ@proton.me
			    DTSTAMP:20200624T112548Z
			    BEGIN:VALARM
			    TRIGGER:-PT15H
			    ACTION:DISPLAY
			    END:VALARM
			    END:VEVENT
			    END:VCALENDAR
                 */
            }
            EventEditDeleteOption.THIS_EVENT_AND_FOLLOWING -> {

            }
            EventEditDeleteOption.ALL_EVENTS -> {

            }
            // TODO don't handle null and fallback to THIS_EVENT?
        }



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
