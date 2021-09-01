package me.proton.android.calendar.domain.usecase

import biweekly.property.Attendee
import me.proton.android.calendar.common.ApiResponseCode
import me.proton.android.calendar.common.EventUtilsImpl.addExceptionDate
import me.proton.android.calendar.common.EventUtilsImpl.generateOccurrence
import me.proton.android.calendar.common.EventUtilsImpl.handleDeleteThisAndFuture
import me.proton.android.calendar.common.ICalUtilsImpl.iCalTimeZone
import me.proton.android.calendar.data.api.*
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.domain.*
import me.proton.android.calendar.domain.api.CalendarsApi
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.domain.model.SendPreferences
import me.proton.android.calendar.presentation.calendar.EventEditDeleteOption
import me.proton.core.domain.entity.UserId
import me.proton.core.mailmessage.domain.entity.Email
import java.time.ZoneId
import java.time.ZonedDateTime

class HandleDeleteUseCase( // TODO TESTS
    private val logger: Logger, // TODO remove unnecessary dependencies
    private val handleAlarmsUseCase: HandleAlarmsUseCase,
    private val calendarsApi: CalendarsApi,
    private val database: AppDatabase,
    private val editCreateEventUseCase: EditCreateEventUseCase,
    private val transformEventUseCase: TransformEventUseCase,
    private val calendarsRepository: CalendarsRepository,
    private val sendEmailUseCase: SendEmailUseCase
    ): UseCase {

    suspend fun handleDelete(userId: UserId, eventId: String, deleteOption: EventEditDeleteOption, occurrenceNumber: Int?) : UseCase.Result {

        // TODO migrate to /sync route and handle recurring deletes

        logger.v("executing DeleteEventUseCase $userId, $eventId, $deleteOption, $occurrenceNumber")

        val eventEntity = calendarsRepository.selectEventEntity(eventId) ?: return UseCase.Result.InvalidParams("DeleteEventUseCase: event $eventId doesn't exist in DB")
        val event = transformEventUseCase.execute(eventEntity) ?: return UseCase.Result.InvalidParams("DeleteEventUseCase: event $eventId could not be transformed")

        val member = database.membersDao().select(event.calendar.id).firstOrNull() ?: return UseCase.Result.InvalidParams("DeleteEventUseCase: could not get Member for calendar ${event.calendar.id}")

        // We need timezone when adding ex dates to handle DST
        val timezone = calendarsRepository.selectCalendarUserSettings(userId.id)?.primaryTimezone

        // TODO when event is in the middle of chain, we need to select the root event and deal with it accordingly!!!

        var result = when (deleteOption) {
            EventEditDeleteOption.THIS_EVENT -> {

                if (event.isRecurring()) {
                    // add EXDATE to it
                    event.addExceptionDate(occurrenceNumber!!, timezone) // TODO
                    editCreateEventUseCase.execute(userId, event.calendar.id, event)
                } else if (event.isSingleEdit()) {

                    val rootEvent = calendarsRepository.selectRootEventEntity(event.uid)?.let { transformEventUseCase.execute(it) } ?: return UseCase.Result.InvalidParams("DeleteEventUseCase: root event for $eventId doesn't exist in DB")

                    // add EXDATE to root event
                    rootEvent.addExceptionDate(occurrenceNumber!!, timezone) // TODO
                    val editResult = editCreateEventUseCase.execute(userId, rootEvent.calendar.id, rootEvent)
                    editResult.ifSuccessAndLogErrors(logger) {}

                    // delete the single edit
                    deleteEvents(userId, listOf(event.id), event.calendar.id, member.id)
                } else {
                    // delete the non-recurring event
                    deleteEvents(userId, listOf(event.id), event.calendar.id, member.id)
                }

            }
            EventEditDeleteOption.THIS_EVENT_AND_FUTURE -> {

                val rootEvent =
                    if (event.isSingleEdit()) calendarsRepository.selectRootEventEntity(event.uid)?.let { transformEventUseCase.execute(it) }
                        ?: return UseCase.Result.InvalidParams("DeleteEventUseCase: root event for $eventId doesn't exist in DB")
                    else event
                val occurrenceStart = rootEvent.generateOccurrence(
                        occurrenceNumber!!,
                        if (rootEvent.isAllDay()) ZoneId.systemDefault().id else rootEvent.iCalendar.iCalTimeZone(rootEvent.iCalEvent.dateStart).id
                    )?.startDateTime
                        ?: return UseCase.Result.Error("DeleteEventUseCase: could not generate occurrence in >delete this and following< events")

                rootEvent.handleDeleteThisAndFuture(occurrenceNumber)
                val editResult = editCreateEventUseCase.execute(userId, rootEvent.calendar.id, rootEvent)
                editResult.ifSuccessAndLogErrors(logger) {}

                // delete single edits happening after this occurrence
                val deleteSingleEditsResult = deleteSingleEditsAfter(userId, rootEvent.id, occurrenceStart.minusNanos(1))
                deleteSingleEditsResult.ifSuccessAndLogErrors(logger) {}

                if ((editResult is UseCase.Result.Success<*>) && (deleteSingleEditsResult is UseCase.Result.Success<*>)) UseCase.Result.Success<Unit>() else UseCase.Result.Error("DeleteEventUseCase: error deleting >this and future< events")

            }
            EventEditDeleteOption.ALL_EVENTS -> {

                // delete single edits and the original event as the last one
                val rootEvent =
                    if (event.isSingleEdit()) calendarsRepository.selectRootEventEntity(event.uid)?.let { transformEventUseCase.execute(it) }
                        ?: return UseCase.Result.InvalidParams("DeleteEventUseCase: root event for $eventId doesn't exist in DB")
                    else event

                // TODO maybe merge this into one request
                val deleteSingleEditsResult = deleteSingleEditsAfter(userId, rootEvent.id, rootEvent.getStart(ZoneId.systemDefault().id)!!.minusNanos(1))
                deleteSingleEditsResult.ifSuccessAndLogErrors(logger) {}
                val deleteResult = deleteEvents(userId, listOf(rootEvent.id), rootEvent.calendar.id, member.id)
                deleteResult.ifSuccessAndLogErrors(logger) {}

                if ((deleteSingleEditsResult is UseCase.Result.Success<*>) && (deleteResult is UseCase.Result.Success<*>)) UseCase.Result.Success<Unit>() else UseCase.Result.Error("DeleteEventUseCase: error deleting >all< events")
            }
        }

        result.ifSuccessAndLogErrors(logger) {}

        return result
    }

    private suspend fun deleteEvents(userId: UserId, eventIds: List<String>, calendarId: String, memberId: String): UseCase.Result  {
        val syncRequestBody = SyncEventsUpdateApiRequest(
            memberId = memberId,
            events = eventIds.map { SyncEventDeleteContainer(it) }
        )

        return when (val syncResponse = calendarsApi.syncEvents(userId, calendarId, syncRequestBody)) {
            is ApiResponse.Success -> {

                // TODO check .isSuccessful on Proton Responses, this will still crash in case of malformed request etc.

                val errorEventIds = syncResponse.data.responses.mapNotNull {
                    if (it.response.code == ApiResponseCode.EVENT_DOES_NOT_EXIST) {
                        // ignore error if event didn't exist on server
                        logger.i("DeleteEventUseCase event didn't exist on server anymore")
                        null
                    } else {
                        logger.e("error deleting event on server: ${it.response.code} ${it.response.error}")
                        eventIds[it.index]
                    }
                }

                val succesEventIds = eventIds.filterNot { it in errorEventIds }
                if (succesEventIds.isNotEmpty()) {
                    calendarsRepository.deleteEventsById(succesEventIds)
                    handleAlarmsUseCase.execute(userId)
                }

                if (errorEventIds.isEmpty()) {
                    UseCase.Result.Success<Unit>()
                } else {
                    UseCase.Result.Error("DeleteEventUseCase: there were errors when deleting events")
                }
            }
            is ApiResponse.Error -> UseCase.Result.Error("DeleteEventUseCase: error in sync events: ${syncResponse.error}")
            is ApiResponse.Exception -> UseCase.Result.Error("DeleteEventUseCase: error in sync events: ${syncResponse.exception.message ?: "(no exception message)"}")
        }
    }

    // TODO maybe use UseCase.Params instead of overloaded methods
    suspend fun handleDeleteSingleEdits(userId: UserId, eventId: String, recurrenceIdIsAfter: ZonedDateTime) : UseCase.Result {
        return deleteSingleEditsAfter(userId, eventId, recurrenceIdIsAfter)
    }

    private suspend fun deleteSingleEditsAfter(userId: UserId, eventId: String, recurrenceIdIsAfter: ZonedDateTime) : UseCase.Result {

        val eventEntity = calendarsRepository.selectEventEntity(eventId) ?: return UseCase.Result.InvalidParams("DeleteEventUseCase: event $eventId doesn't exist in DB")
        val event = transformEventUseCase.execute(eventEntity) ?: return UseCase.Result.InvalidParams("DeleteEventUseCase: event $eventId could not be transformed")

        val member = database.membersDao().select(event.calendar.id).firstOrNull() ?: return UseCase.Result.InvalidParams("DeleteEventUseCase: could not get Member for calendar ${event.calendar.id}")

        val eventsSharingUidResponse = calendarsApi.getEventsByUid(userId, event.uid, 0, 100) // TODO paging
        val eventsSharingUid = if (eventsSharingUidResponse is ApiResponse.Success) eventsSharingUidResponse.data.events.mapNotNull { transformEventUseCase.execute(it) } else return UseCase.Result.Error("error fetching events sharing UID")

        // we need to manually delete all "single-edited" events with RecurrenceID after just-deleted occurrence
        val eventsToDelete = eventsSharingUid.filter {
            it.iCalEvent.recurrenceId != null &&
                    ZonedDateTime.ofInstant(it.iCalEvent.recurrenceId.value.toInstant(), ZoneId.systemDefault()).isAfter(recurrenceIdIsAfter)
        }

        val deleteResult = if (eventsToDelete.isNotEmpty()) {
            deleteEvents(userId, eventsToDelete.map { it.id }, event.calendar.id, member.id)
        } else UseCase.Result.Success<Unit>()

        return deleteResult
    }

    suspend fun handleDeleteAsOrganizer(
        userId: UserId,
        event: Event,
        attendees: List<Attendee>,
        sendPreferences: Map<Email, SendPreferences>,
        timeFormatIs24Hours: Boolean,
        isRecurring: Boolean,
        isDisabled: Boolean
    ): UseCase.Result {

        if (!isDisabled) {
            // If address is disabled, cancellation can't be sent
            val sendCancellationResult = sendEmailUseCase.sendCancellationToAttendees(
                userId,
                event,
                attendees,
                sendPreferences,
                timeFormatIs24Hours
            )
            sendCancellationResult.ifSuccessAndLogErrors(logger) { }

            if (sendCancellationResult is UseCase.Result.Error) {
                return if (sendCancellationResult.error == UseCase.Error.USER_ADDRESS_INVALID_FOR_ENCRYPTION) {
                    UseCase.Result.Error(
                        "HandleSaveUseCase: error in send email (cancel as organizer): ${sendCancellationResult.message}",
                        UseCase.Error.USER_ADDRESS_INVALID_FOR_ENCRYPTION
                    )
                } else {
                    UseCase.Result.Error(
                        "HandleSaveUseCase: error in send email (cancel as organizer): ${sendCancellationResult.message}"
                    )
                }
            } else if (sendCancellationResult is UseCase.Result.InvalidParams) {
                return UseCase.Result.Error(
                    "HandleSaveUseCase: invalid params in send email: ${sendCancellationResult.message}"
                )
            }
        }

        return handleDelete(
            userId,
            event.id,
            if (isRecurring) EventEditDeleteOption.ALL_EVENTS else EventEditDeleteOption.THIS_EVENT,
            if (isRecurring) null else 0
        )
    }
}
