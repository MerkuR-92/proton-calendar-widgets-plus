package me.proton.android.calendar.domain.usecase

import com.google.gson.Gson
import me.proton.android.calendar.data.api.*
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.domain.*
import me.proton.android.calendar.domain.api.AddressesApi
import me.proton.android.calendar.domain.api.CalendarsApi
import java.time.ZoneId
import java.time.ZonedDateTime

class DeleteSingleEventEditsUseCase( // TODO TESTS
    private val logger: Logger, // TODO remove unnecessary dependencies
    private val gson: Gson,
    private val calendarsApi: CalendarsApi,
    private val addressesApi: AddressesApi,
    private val database: AppDatabase,
    private val editCreateEventUseCase: EditCreateEventUseCase,
    private val transformEventUseCase: TransformEventUseCase,
    private val calendarsRepository: CalendarsRepository): UseCase {

    suspend fun execute(userId: String, eventId: String, recurrenceIdIsAfter: ZonedDateTime) : UseCase.Result {

        // TODO migrate to /sync route and handle recurring deletes

        logger.v("executing DeleteEventUseCase")

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
