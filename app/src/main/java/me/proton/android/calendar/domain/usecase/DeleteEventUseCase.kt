package me.proton.android.calendar.domain.usecase

import com.google.gson.Gson
import me.proton.android.calendar.data.api.*
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.domain.*
import me.proton.android.calendar.domain.api.CalendarsApi
import me.proton.android.calendar.presentation.calendar.EventEditDeleteOption
import java.time.ZoneId

class DeleteEventUseCase( // TODO TESTS
    private val logger: Logger, // TODO remove unnecessary dependencies
    private val gson: Gson,
    private val calendarsApi: CalendarsApi,
    private val deleteSingleEventEditsUseCase: DeleteSingleEventEditsUseCase,
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
                    deleteEvents(listOf(event.id), event.calendar.id, member.id)
                }

            }
            EventEditDeleteOption.THIS_EVENT_AND_FUTURE -> {

                val occurrenceStart = event.generateOccurrence(occurrenceNumber!! /* TODO*/, ZoneId.systemDefault().id)?.startDateTime ?: return UseCase.Result.Error("could not generate occurrence in >delete this and following< events")

                event.handleDeleteThisAndFuture(occurrenceNumber)
                val editResult = editCreateEventUseCase.execute(userId, event.calendar.id, event)

                // delete single edits happening after this occurrence
                val deleteResult = deleteSingleEventEditsUseCase.execute(userId, event.id, occurrenceStart)

                if ((editResult is UseCase.Result.Success) && (deleteResult is UseCase.Result.Success)) UseCase.Result.Success else UseCase.Result.Error("error deleting >this and future< events")

            }
            EventEditDeleteOption.ALL_EVENTS -> {
                // simple delete
                deleteEvents(listOf(event.id), event.calendar.id, member.id)
            }
        }

        return result
    }

    private suspend fun deleteEvents(eventIds: List<String>, calendarId: String, memberId: String): UseCase.Result  {
        val syncRequestBody = SyncEventsUpdateApiRequest(
            memberId = memberId,
            events = eventIds.map { SyncEventDeleteContainer(it) }
        )

        // TODO we will not get IDs of events that were successfully deleted, but we will get them when deletion fails

        return when (val syncResponse = calendarsApi.syncEvents(calendarId, syncRequestBody)) {
            is ApiResponse.Success -> {
                // TODO it looks like /sync does not return IDs of deleted events so we can't check which ones were deleted successfully
                database.eventsDao().deleteByIds(eventIds)
                UseCase.Result.Success
            }
            is ApiResponse.Error -> UseCase.Result.Error(syncResponse.error)
            is ApiResponse.Exception -> UseCase.Result.Error(syncResponse.exception.message ?: "(no exception message)")
        }
    }

}
