package me.proton.android.calendar.domain.usecase

import biweekly.property.ExceptionDates
import com.google.gson.Gson
import me.proton.android.calendar.common.TimberLogger
import me.proton.android.calendar.data.api.*
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.domain.*
import me.proton.android.calendar.domain.api.AddressesApi
import me.proton.android.calendar.domain.api.CalendarsApi
import me.proton.android.calendar.domain.api.KeysApi
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.presentation.calendar.EventEditDeleteOption

class DeleteEventUseCase( // TODO TESTS
    private val logger: Logger, // TODO remove unnecessary dependencies
    private val gson: Gson,
    private val calendarsApi: CalendarsApi,
    private val addressesApi: AddressesApi,
    private val database: AppDatabase,
    private val editCreateEventUseCase: EditCreateEventUseCase,
    private val transformEventUseCase: TransformEventUseCase,
    private val calendarsRepository: CalendarsRepository): UseCase {

    suspend fun execute(userId: String, eventId: String, deleteOption: EventEditDeleteOption, occurrenceNumber: Int?) : UseCase.Result {

        // TODO migrate to /sync route and handle recurring deletes

        logger.v("executing DeleteEventUseCase")

        val eventEntity = calendarsRepository.selectEventEntity(eventId) ?: return UseCase.Result.InvalidParams("event $eventId doesn't exist in DB")
        val event = transformEventUseCase.execute(eventEntity) ?: return UseCase.Result.InvalidParams("event $eventId could not be transformed")

        val member = database.membersDao().select(event.calendar.id).firstOrNull() ?: return UseCase.Result.InvalidParams("could not get Member for calendar ${event.calendar.id}")

        var result = when (deleteOption) {
            EventEditDeleteOption.THIS_EVENT -> {

                if (event.isRecurring()) {

                    event.addExceptionDate(occurrenceNumber!!) // TODO
                    editCreateEventUseCase.execute(userId, event.calendar.id, event)

                } else { // simple delete
                    deleteOriginalEvent(event.id, event.calendar.id, member.id)
                }

            }
            EventEditDeleteOption.THIS_EVENT_AND_FOLLOWING -> {

                event.handleDeleteThisAndFollowing(occurrenceNumber!!) // TODO
                editCreateEventUseCase.execute(userId, event.calendar.id, event)

                // TODO FIXME nuke rest of the occurrences!!!

            }
            EventEditDeleteOption.ALL_EVENTS -> {
                // simple delete
                deleteOriginalEvent(event.id, event.calendar.id, member.id)
            }
            // TODO don't handle null and fallback to THIS_EVENT?
        }

        return result
    }

    private suspend fun deleteOriginalEvent(eventId: String, calendarId: String, memberId: String): UseCase.Result  {
        val syncRequestBody = SyncEventsUpdateApiRequest(
            memberId = memberId,
            events = listOf(
                SyncEventDeleteContainer(eventId)
            )
        )

        return when (val syncResponse = calendarsApi.syncEvents(calendarId, syncRequestBody)) {
            is ApiResponse.Success -> {
                database.eventsDao().deleteById(eventId)
                UseCase.Result.Success
            }
            is ApiResponse.Error -> UseCase.Result.Error(syncResponse.error)
            is ApiResponse.Exception -> UseCase.Result.Error(syncResponse.exception.message ?: "(no exception message)")
        }
    }

}
