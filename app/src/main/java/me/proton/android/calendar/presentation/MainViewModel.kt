package me.proton.android.calendar.presentation

import android.content.*
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.work.*
import biweekly.parameter.ParticipationStatus
import biweekly.property.Method
import biweekly.property.Status
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.proton.android.calendar.common.*
import me.proton.android.calendar.common.ICalUtils.clone
import me.proton.android.calendar.common.IcsSurgeryUtils.cleanIcs
import me.proton.android.calendar.common.IcsSurgeryUtils.cleanRecurrenceId
import me.proton.android.calendar.data.entity.CalendarEntity
import me.proton.android.calendar.data.entity.EventEntity
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.UsersRepository
import me.proton.android.calendar.domain.model.Address
import me.proton.android.calendar.domain.model.Calendar
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.domain.usecase.*
import me.proton.android.calendar.presentation.calendar.CalendarViewModel
import me.proton.core.domain.entity.UserId
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.*
import java.util.concurrent.TimeUnit


class MainViewModel(
    private val context: Context,
    private val calendarsRepository: CalendarsRepository,
    private val logger: Logger,
    private val editCreateEventUseCase: EditCreateEventUseCase,
    private val usersRepository: UsersRepository,
    private val calendarViewModel: CalendarViewModel,
    private val transformEventUseCase: TransformEventUseCase,
    private val updateParticipationStatusUseCase: UpdateParticipationStatusUseCase,
    private val json: Json
) : ViewModel() {

    private val intents = mutableMapOf<String, Intent>()

    /**
     * Try to open maps with event location.
     */
    fun handleEventLocationShow(location: String): Boolean {
        return try {
            val googleMapsUri = Uri.parse("geo:0,0?q=$location")
            val googleMapsIntent = Intent(Intent.ACTION_VIEW, googleMapsUri)
            googleMapsIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            googleMapsIntent.setPackage("com.google.android.apps.maps")
            context.startActivity(googleMapsIntent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    fun handleCopyToClipboard(content: String): Boolean {
        return try {
            val clipboard: ClipboardManager? = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?
            if (clipboard != null) {
                val clip = ClipData.newPlainText("", content)
                clipboard.setPrimaryClip(clip)
                true
            } else false
        } catch (e: Exception) {
            false
        }
    }

    // TODO sync all "active" accounts
    fun syncServerEvents(userId: UserId) : LiveData<Operation.State> {

        val workState = kotlin.runCatching {
            WorkManager.getInstance(context).getWorkInfosForUniqueWork(UseCaseWorker.UniqueWorkNames.SYNC_SERVER_EVENTS).get(1, TimeUnit.SECONDS)
        }.getOrNull()?.firstOrNull()?.state

        val existingWorkPolicy = if (workState == WorkInfo.State.RUNNING) {
            ExistingWorkPolicy.KEEP
        } else {
            ExistingWorkPolicy.REPLACE
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val work = OneTimeWorkRequestBuilder<UseCaseWorker>()
            .setConstraints(constraints)
            .setInputData(workDataOf(
                UseCaseWorker.INPUT_USE_CASE_ID to UseCaseWorker.UseCaseId.SYNC_SERVER_EVENTS,
                UseCaseWorker.INPUT_USER_ID to userId.id
            ))
            .build()

        // TODO work is unique per user-id, make sure different inputdata => different unique work
        return WorkManager.getInstance(context).enqueueUniqueWork(UseCaseWorker.UniqueWorkNames.SYNC_SERVER_EVENTS, existingWorkPolicy, work).state

    }

    // TODO run only after bootstrap & successful "cold fetch" of events for the first required period
    fun syncAlarms(userId: UserId) : LiveData<Operation.State> {

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val work = OneTimeWorkRequestBuilder<UseCaseWorker>()
            .setConstraints(constraints)
            .setInputData(workDataOf(
                UseCaseWorker.INPUT_USE_CASE_ID to UseCaseWorker.UseCaseId.SYNC_ALARMS,
                UseCaseWorker.INPUT_USER_ID to userId.id
            ))
            .build()

        // TODO work is unique per user-id, make sure different inputdata => different unique work
        return WorkManager.getInstance(context).enqueueUniqueWork(UseCaseWorker.UniqueWorkNames.SYNC_ALARMS, ExistingWorkPolicy.REPLACE, work).state

    }

    private var viewModelJob = Job()

    override fun onCleared() {
        super.onCleared()
        viewModelJob.cancel()
    }

    /**
     * Stores intent with given [action] in a map for clients to [consumeIntent] later.
     */
    fun handleIntent(intent: Intent) {
        intent.action?.let {
            intents.put(it, intent)
        }
    }

    fun containsIntent(action: String) = intents.containsKey(action)

    /**
     * Returns and deletes intent with given [action], if it has been handled previously.
     */
    fun consumeIntent(action: String): Intent? {
        return intents.remove(action)
    }

    companion object {
        const val INTENT_ACTION_SHOW_EVENT_DETAILS = "INTENT_ACTION_SHOW_EVENT_DETAILS"

        fun createMainIntentToShowEventDetails(context: Context, eventId: String, occurrenceNumber: Int?): Intent {
            return Intent(context, MainActivity::class.java).apply {
                action = INTENT_ACTION_SHOW_EVENT_DETAILS
                data = Navigation.Deeplink.toMainActivityWithEventId(eventId, occurrenceNumber ?: 0)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        }
    }

    suspend fun handleIcsFile(bufferedReader: BufferedReader, userId: UserId): IcsSurgeryUtils.HandleIcsResult {
        val iCalString = bufferedReader.use { it.readText() }

        val cleanIcsResult = cleanIcs(iCalString)

        if (cleanIcsResult !is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            return cleanIcsResult
        }

        val iCalendar = cleanIcsResult.iCalendar ?: return IcsSurgeryUtils.HandleIcsResult.Error.ParsingFailed

        if (iCalendar.method.isAdd) return IcsSurgeryUtils.HandleIcsResult.Error.UnsupportedAdd // TODO Remove once ADD is handled
        if (!iCalendar.method.isRequest &&
            !iCalendar.method.isCancel &&
            !iCalendar.method.isReply) return IcsSurgeryUtils.HandleIcsResult.Error.UnsupportedMethod // TODO Remove once other methods are handled


        val userEmails = usersRepository.getUserAddresses(userId.id)?.map { address ->
            canonicalizeProtonEmail(address.email)
        }
        val organizerEmail = iCalendar.events.first().organizer.extractEmail()
        val isOrganizerMode =
            if (organizerEmail != null) {
                val canonicalOrganizerEmail = canonicalizeProtonEmail(organizerEmail)
                userEmails?.firstOrNull { canonicalOrganizerEmail == it } != null
            } else false
        val userAttendee = iCalendar.events.first().attendees.find { attendee ->
            userEmails?.firstOrNull { userEmail ->
                val attendeeEmail = attendee.extractEmail()
                attendeeEmail != null && canonicalizeProtonEmail(attendeeEmail).equals(userEmail, ignoreCase = true)
            } != null
        }
        if (!isOrganizerMode && userAttendee == null) return IcsSurgeryUtils.HandleIcsResult.Error.PartyCrasher

        val defaultCalendarId = calendarsRepository.getDefaultCalendarId(userId.id)
            ?: return IcsSurgeryUtils.HandleIcsResult.Error.NoDefaultCalendarFound // TODO Handle error
        var defaultCalendar = calendarsRepository.selectCalendar(defaultCalendarId)
        if (defaultCalendar == null || !defaultCalendar.isActive) {
            defaultCalendar = calendarsRepository.getActiveCalendars(userId.id).firstOrNull()
                ?: return IcsSurgeryUtils.HandleIcsResult.Error.NoDefaultCalendarFound // TODO Handle error
        }

        val newEvent = Event(ICalUtils.generateOfflineEventId(), Calendar(
            defaultCalendar.id,
            defaultCalendar.name,
            defaultCalendar.color,
            defaultCalendar.flags,
            defaultCalendar.display == 1
        ), iCalendar)

        newEvent.iCalEvent.attendees?.let {
            newEvent.iCalEvent.attendees.forEach {
                if (it.getParameter(CustomICalPropertyParameter.X_PM_TOKEN) == null) {
                    val canonicalEmail = usersRepository.getCanonicalAddresses(userId,
                        listOf(it.extractEmail() ?: return IcsSurgeryUtils.HandleIcsResult.Error.DefaultError)
                    )?.get(it.extractEmail()) ?: return IcsSurgeryUtils.HandleIcsResult.Error.DefaultError
                    val token = ICalUtils.generateXPmToken(canonicalEmail, newEvent.uid)
                    it.addParameter(CustomICalPropertyParameter.X_PM_TOKEN, token)
                }
            }
        }

        val eventsSharingUidResponse = calendarsRepository.getEventsByUid(userId, newEvent.uid)

        // IMPORTANT: We need parent event to clean recurrence id
        val parentEventEntity = eventsSharingUidResponse?.firstOrNull { eventEntity ->
            eventEntity.sharedEvents.any {
                try {
                    // only root event contains RRULE
                    it.jsonObject.get("Data")?.jsonPrimitive?.content?.contains("RRULE:") == true
                } catch (e: IllegalArgumentException) {
                    false
                }
            }
        }
        val parentEvent = if (parentEventEntity != null) transformEventUseCase.execute(parentEventEntity) else null
        // IMPORTANT: Unlike the rest of the surgery, clean recurrence id is called outside of cleanIcs, but it is still mandatory
        if (!iCalendar.cleanRecurrenceId(iCalendar.method == Method.reply(), parentEvent?.iCalendar)) return IcsSurgeryUtils.HandleIcsResult.Error.InvalidRecurrenceId

        var existingEvent: Event? = null
        var existingEventEntity: EventEntity? = null
        eventsSharingUidResponse?.let {
            for (eventEntity in eventsSharingUidResponse) {
                val event = transformEventUseCase.execute(eventEntity)
                if (event?.iCalEvent?.recurrenceId == newEvent.iCalEvent.recurrenceId) {
                    existingEvent = event
                    existingEventEntity = eventEntity
                    break
                }
            }
        }

        if (existingEvent?.calendar?.isActive == false) {
            return IcsSurgeryUtils.HandleIcsResult.Error.DisabledCalendar
        }

        val isNew =
            eventsSharingUidResponse.isNullOrEmpty() || existingEvent == null || (existingEvent != null && existingEvent?.decryptionStatus == Event.DecryptionStatus.FAILURE)

        if (isNew && !isOrganizerMode && !iCalendar.method.isCancel) {
            // Create brand new event
            return editCreateEventFromIcs(
                IcsSurgeryUtils.HandleIcsAction.CREATE_EVENT,
                userId,
                newEvent
            )
        } else {
            // Event already exists, check if we need to update it using the ics content
            if (!isOrganizerMode && existingEvent != null && newEvent.iCalEvent.dateTimeStamp.value.after(existingEvent?.iCalEvent?.dateTimeStamp?.value)) {
                // Update existing event as an attendee

                val newICalendar = newEvent.iCalendar.clone()

                val updatedEvent = if (newICalendar.method.isCancel) {
                    // TODO Cancel just one occurrence: if the ICS contains a RECURRENCE-ID which matches an occurrence of the series for which no previous single edit exists.
                    //  In that case you have to create a single edit with status CANCELLED and no alarms.

                    // Cancel the event via the sync route by changing STATUS, DTSTAMP (update with the ICS DTSTAMP), and drop the alarms
                    existingEvent?.iCalEvent?.status = Status.cancelled()
                    existingEvent?.iCalEvent?.alarms?.clear()
                    existingEvent?.iCalEvent?.dateTimeStamp = newICalendar.events.first().dateTimeStamp
                    existingEvent
                } else {
                    userEmails?.let {
                        val currentParticipationStatus = existingEvent?.getParticipationStatus(userEmails) ?: return@let

                        val currentSequence = existingEvent?.iCalEvent?.sequence?.value
                        if (currentSequence != null && currentSequence < newEvent.iCalEvent.sequence.value) {
                            newICalendar.events.first().attendees.firstOrNull { it == userAttendee }?.participationStatus =
                                ParticipationStatus.NEEDS_ACTION
                        } else {
                            newICalendar.events.first().attendees.firstOrNull { it == userAttendee }?.participationStatus =
                                currentParticipationStatus
                        }
                    }
                    existingEvent?.copy(iCalendar = newICalendar)
                }

                return editCreateEventFromIcs(
                    IcsSurgeryUtils.HandleIcsAction.UPDATE_EVENT,
                    userId,
                    updatedEvent ?: return IcsSurgeryUtils.HandleIcsResult.Error.EditCreateEventError
                )
            } else if (isOrganizerMode && existingEvent != null && !iCalendar.events.first().attendees.isNullOrEmpty()) {
                // Update existing event as an organizer

                val attendees = existingEventEntity?.attendees?.map {
                    json.decodeFromJsonElement<Event.AttendeeStatusEvent>(it)
                }
                existingEvent?.iCalEvent?.attendees?.forEach { attendee ->
                    val updatedAttendee = iCalendar.events.first().attendees.firstOrNull()
                    val updatedAttendeeEmail = updatedAttendee?.extractEmail() ?: return IcsSurgeryUtils.HandleIcsResult.Error.EditCreateEventError
                    if (attendee.extractEmail() == updatedAttendeeEmail) {
                        val attendeeToken = attendee.getParameter(CustomICalPropertyParameter.X_PM_TOKEN)
                        val attendeeStatusEvent = attendees?.find { it.token == attendeeToken }

                        val existingUpdateTime = attendeeStatusEvent?.updateTime
                        val newUpdateTime = TimeUnit.MILLISECONDS.toSeconds(iCalendar.events.first().dateTimeStamp.value.time).toInt()

                        if (existingUpdateTime == null || existingUpdateTime < newUpdateTime) {

                            val updateParticipationStatusUseCaseResult = updateParticipationStatusUseCase.execute(
                                userId,
                                existingEvent?.calendar?.id ?: return IcsSurgeryUtils.HandleIcsResult.Error.EditCreateEventError,
                                existingEvent?.id ?: return IcsSurgeryUtils.HandleIcsResult.Error.EditCreateEventError,
                                attendeeStatusEvent?.id ?: return IcsSurgeryUtils.HandleIcsResult.Error.EditCreateEventError,
                                updatedAttendee.participationStatus.toInt(),
                                null,
                                newUpdateTime
                            )
                            updateParticipationStatusUseCaseResult.ifSuccessAndLogErrors(logger) { }
                            if (updateParticipationStatusUseCaseResult !is UseCase.Result.Success<*>) {
                                return IcsSurgeryUtils.HandleIcsResult.Error.EditCreateEventError
                            }

                            return IcsSurgeryUtils.HandleIcsResult.Success(
                                eventId = existingEvent?.id ?: return IcsSurgeryUtils.HandleIcsResult.Error.EditCreateEventError,
                                IcsSurgeryUtils.HandleIcsAction.UPDATE_EVENT,
                                Pair(updatedAttendeeEmail, updatedAttendee.participationStatus)
                            )
                        }
                    }
                }
            }
        }

        // If no update is needed, return the existing event id
        return IcsSurgeryUtils.HandleIcsResult.Success(existingEvent?.id ?: return IcsSurgeryUtils.HandleIcsResult.Error.DefaultError, IcsSurgeryUtils.HandleIcsAction.OPEN_EVENT)
    }

    private suspend fun editCreateEventFromIcs(action: IcsSurgeryUtils.HandleIcsAction, userId: UserId, newEvent: Event): IcsSurgeryUtils.HandleIcsResult {
        when (val editCreateEventResult = editCreateEventUseCase.execute(userId, newEvent.calendar.id, newEvent)) {
            is UseCase.Result.Success<*> -> {
                var eventId: String? = null
                editCreateEventResult.returnValue.tryCast<List<String>> {
                    eventId = this.firstOrNull()
                }

                if (!newEvent.calendar.display) {
                    // 1. Update in DB
                    calendarViewModel.updateCalendarVisibility(newEvent.calendar.id, display = 1)
                    // 2. Update on Server
                    calendarViewModel.updateServerCalendar(newEvent.calendar.id)
                }

                return IcsSurgeryUtils.HandleIcsResult.Success(eventId = eventId ?: return IcsSurgeryUtils.HandleIcsResult.Error.EditCreateEventError, action)
            }
            is UseCase.Result.InvalidParams -> {
                logger.e("MainViewModel: invalid params in create event: ${editCreateEventResult.message}")
                return IcsSurgeryUtils.HandleIcsResult.Error.EditCreateEventError
            }
            is UseCase.Result.Error -> {
                logger.e("MainViewModel: error in create event: ${editCreateEventResult.message}")
                return IcsSurgeryUtils.HandleIcsResult.Error.EditCreateEventError
            }
        }
    }
}
