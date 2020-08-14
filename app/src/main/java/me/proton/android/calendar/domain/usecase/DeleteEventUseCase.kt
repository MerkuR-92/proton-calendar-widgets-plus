package me.proton.android.calendar.domain.usecase

import com.google.gson.Gson
import me.proton.android.calendar.common.TimberLogger
import me.proton.android.calendar.common.printToString
import me.proton.android.calendar.data.api.*
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.domain.*
import me.proton.android.calendar.domain.api.CalendarsApi
import me.proton.android.calendar.presentation.calendar.EventEditDeleteOption
import java.time.ZoneId
import java.time.ZonedDateTime

class DeleteEventUseCase( // TODO TESTS
    private val logger: Logger, // TODO remove unnecessary dependencies
    private val gson: Gson,
    private val calendarsApi: CalendarsApi,
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

        val rootEvent = calendarsRepository.selectRootEventEntity(event.uid)?.let { transformEventUseCase.execute(it) } ?: return UseCase.Result.InvalidParams("root event for $eventId doesn't exist in DB")

        // TODO when event is in the middle of chain, we need to select the root event and deal with it accordingly!!!

        var result = when (deleteOption) {
            EventEditDeleteOption.THIS_EVENT -> {

                if (event.isRecurring()) {
                    // add EXDATE to root event
                    rootEvent.addExceptionDate(occurrenceNumber!!) // TODO
                    editCreateEventUseCase.execute(userId, rootEvent.calendar.id, rootEvent)
                }
                else if (event.isFromRecurring()) {
                    // add EXDATE to root event
                    rootEvent.addExceptionDate(occurrenceNumber!!) // TODO
                    editCreateEventUseCase.execute(userId, rootEvent.calendar.id, rootEvent)

                    // delete the single edit
                    deleteEvents(listOf(event.id), event.calendar.id, member.id)
                } else {
                    // delete the non-recurring event
                    deleteEvents(listOf(event.id), event.calendar.id, member.id)
                }

            }
            EventEditDeleteOption.THIS_EVENT_AND_FUTURE -> {

                val occurrenceStart = event.generateOccurrence(occurrenceNumber!! /* TODO*/, ZoneId.systemDefault().id /*TODO calendar's timezone?*/)?.startDateTime ?: return UseCase.Result.Error("could not generate occurrence in >delete this and following< events")

                event.handleDeleteThisAndFuture(occurrenceNumber)
                val editResult = editCreateEventUseCase.execute(userId, event.calendar.id, event)

                // delete single edits happening after this occurrence
                val deleteSingleEditsResult = deleteSingleEditsAfter(userId, event.id, occurrenceStart)

                if ((editResult is UseCase.Result.Success) && (deleteSingleEditsResult is UseCase.Result.Success)) UseCase.Result.Success else UseCase.Result.Error("error deleting >this and future< events")

            }
            EventEditDeleteOption.ALL_EVENTS -> {

                // delete single edits and the original event as the last one

                // TODO maybe merge this into one request
                val deleteSingleEditsResult = deleteSingleEditsAfter(userId, event.id, event.getStart(ZoneId.systemDefault().id)!!.minusNanos(1))
                val deleteResult = deleteEvents(listOf(event.id), event.calendar.id, member.id)

                if ((deleteSingleEditsResult is UseCase.Result.Success) && (deleteResult is UseCase.Result.Success)) UseCase.Result.Success else UseCase.Result.Error("error deleting >all< events")
            }
        }

        return result
    }

    private suspend fun deleteEvents(eventIds: List<String>, calendarId: String, memberId: String): UseCase.Result  {
        val syncRequestBody = SyncEventsUpdateApiRequest(
            memberId = memberId,
            events = eventIds.map { SyncEventDeleteContainer(it) }
        )

        return when (val syncResponse = calendarsApi.syncEvents(calendarId, syncRequestBody)) {
            is ApiResponse.Success -> {

                // TODO check .isSuccessful on Proton Responses, this will still crash in case of malformed request etc.

                syncResponse.data.responses.forEach {
                    logger.e("error deleting event on server: ${it.response.code} ${it.response.error}")
                }

                val errorEventIds = syncResponse.data.responses.map { eventIds[it.index] }

                database.eventsDao().deleteByIds(eventIds.filterNot { it in errorEventIds })

                if (errorEventIds.isEmpty()) {
                    UseCase.Result.Success
                } else {
                    UseCase.Result.Error("there were errors when deleting events")
                }
            }
            is ApiResponse.Error -> UseCase.Result.Error(syncResponse.error)
            is ApiResponse.Exception -> UseCase.Result.Error(syncResponse.exception.message ?: "(no exception message)")
        }
    }

    // TODO maybe use UseCase.Params instead of overloaded methods
    suspend fun execute(userId: String, eventId: String, recurrenceIdIsAfter: ZonedDateTime) : UseCase.Result {
        return deleteSingleEditsAfter(userId, eventId, recurrenceIdIsAfter)
    }

    private suspend fun deleteSingleEditsAfter(userId: String, eventId: String, recurrenceIdIsAfter: ZonedDateTime) : UseCase.Result {

        val eventEntity = calendarsRepository.selectEventEntity(eventId) ?: return UseCase.Result.InvalidParams("event $eventId doesn't exist in DB")
        val event = transformEventUseCase.execute(eventEntity) ?: return UseCase.Result.InvalidParams("event $eventId could not be transformed")

        val member = database.membersDao().select(event.calendar.id).firstOrNull() ?: return UseCase.Result.InvalidParams("could not get Member for calendar ${event.calendar.id}")

        val eventsSharingUidResponse = calendarsApi.getEventsByUid(event.uid,0,100) // TODO paging
        val eventsSharingUid = if (eventsSharingUidResponse is ApiResponse.Success) eventsSharingUidResponse.data.events.mapNotNull { transformEventUseCase.execute(it) } else return UseCase.Result.Error("error fetching events sharing UID")

        // we need to manually delete all "single-edited" events with RecurrenceID after just-deleted occurrence
        val eventsToDelete = eventsSharingUid.filter {
            it.iCalEvent.recurrenceId != null &&
                    ZonedDateTime.ofInstant(it.iCalEvent.recurrenceId.value.toInstant(), ZoneId.systemDefault()).isAfter(recurrenceIdIsAfter)
        }

        val deleteResult = if (eventsToDelete.isNotEmpty()) {
            deleteEvents(eventsToDelete.map { it.id }, event.calendar.id, member.id)
        } else UseCase.Result.Success

        return deleteResult
    }

}
