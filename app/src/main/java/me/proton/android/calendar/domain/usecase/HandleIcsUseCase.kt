package me.proton.android.calendar.domain.usecase

import biweekly.ICalendar
import biweekly.parameter.ParticipationStatus
import biweekly.property.Attendee
import biweekly.property.Method
import biweekly.property.Status
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.proton.android.calendar.common.*
import me.proton.android.calendar.common.utils.AndroidUtils.toInt
import me.proton.android.calendar.common.utils.AndroidUtils.tryCast
import me.proton.android.calendar.common.CustomICalPropertyParameter.X_PM_SHARED_EVENT_ID
import me.proton.android.calendar.common.CustomICalPropertyParameter.X_PM_TOKEN
import me.proton.android.calendar.common.utils.EventUtilsImpl.getParticipationStatus
import me.proton.android.calendar.common.FeatureFlag.OPEN_ICS_FILES
import me.proton.android.calendar.common.utils.ICalUtilsImpl.clone
import me.proton.android.calendar.common.utils.ICalUtilsImpl.extractEmail
import me.proton.android.calendar.common.utils.IcsSurgeryUtils.cleanRecurrenceId
import me.proton.android.calendar.common.utils.ProtonUtilsImpl.canonicalizeProtonEmail
import me.proton.android.calendar.common.utils.IcsSurgeryUtils
import me.proton.android.calendar.common.utils.ICalUtilsImpl
import me.proton.android.calendar.common.utils.ProtonUtilsImpl.canonicalizeProtonEmails
import me.proton.android.calendar.common.utils.getAddressesOrNull
import me.proton.android.calendar.data.api.valueOrNullAndLogErrors
import me.proton.android.calendar.data.entity.EventEntity
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.EventDecryptor
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.model.Calendar
import me.proton.android.calendar.domain.model.Event
import me.proton.core.domain.entity.UserId
import me.proton.core.user.domain.UserManager
import me.proton.core.util.kotlin.toBoolean
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class HandleIcsUseCase @Inject constructor(
    private val logger: Logger,
    private val json: Json,
    private val userManager: UserManager,
    private val calendarsRepository: CalendarsRepository,
    private val transformEventUseCase: TransformEventUseCase,
    private val editCreateEventUseCase: EditCreateEventUseCase,
    private val updateParticipationStatusUseCase: UpdateParticipationStatusUseCase,
    private val updateCalendarUseCase: UpdateCalendarUseCase,
    private val handleDeleteUseCase: HandleDeleteUseCase,
    private val canonicalEmailsUseCase: GetCanonicalEmailsUseCase,
    private val eventDecryptor: EventDecryptor
) {

    suspend fun execute(iCalString: String, userId: UserId, senderEmail: String?, recipientEmail: String?): IcsSurgeryUtils.HandleIcsResult {

        val cleanIcsResult = IcsSurgeryUtils.cleanIcs(iCalString)

        if (cleanIcsResult !is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            // Only log error as it doesn't contain any sensitive information
            if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.Error) logger.i("HandleIcsUseCase parsing error ${cleanIcsResult.javaClass}")
            return cleanIcsResult
        }

        val iCalendar = cleanIcsResult.iCalendar ?: return IcsSurgeryUtils.HandleIcsResult.Error.ParsingFailed

        if (iCalendar.method.isPublish) return IcsSurgeryUtils.HandleIcsResult.Error.Unsupported.Publish // TODO Remove once PUBLISH is handled

        val canonicalUserEmails = userManager.getAddressesOrNull(userId)?.map { address ->
            canonicalizeProtonEmail(address.email, forceCanonicalization = true)
        } ?: return IcsSurgeryUtils.HandleIcsResult.Error.DefaultError

        val canonicalSenderEmail = canonicalizeProtonEmail(senderEmail ?: "", forceCanonicalization = true)
        val canonicalRecipientEmail = canonicalizeProtonEmail(recipientEmail ?: "", forceCanonicalization = true)

        val organizerEmail = iCalendar.events.first().organizer?.extractEmail() ?: run {
            // The ORGANIZER field is mandatory in an invitation, but some providers forget about it.
            //  In those cases, we build one from the sender of the email: ORGANIZER;CN=address:mailto:address
            if (!senderEmail.isNullOrBlank() && (iCalendar.method.isRequest || iCalendar.method.isCancel)) {
                iCalendar.events.first().setOrganizer(senderEmail)
                senderEmail
            } else if (!recipientEmail.isNullOrBlank() && iCalendar.method.isReply) {
                iCalendar.events.first().setOrganizer(recipientEmail)
                recipientEmail
            } else {
                logger.i("HandleIcsUseCase error missing organizer")
                return IcsSurgeryUtils.HandleIcsResult.Error.Invalid.MissingOrganizer
            }
        }

        // Find out if we are in organizer mode or attendee mode
        val canonicalOrganizerEmail = canonicalizeProtonEmail(organizerEmail, forceCanonicalization = true)
        val isOrganizerMode = canonicalUserEmails.firstOrNull { canonicalOrganizerEmail == it } != null

        var isCurrentUserSender = false // TODO Replace by val once we remove OPEN_ICS_FILES intent
        if (!OPEN_ICS_FILES || (canonicalSenderEmail.isNotBlank() && canonicalRecipientEmail.isNotBlank())) {

            isCurrentUserSender = canonicalUserEmails.contains(canonicalSenderEmail) == true
            val isCurrentUserRecipient = canonicalUserEmails.contains(canonicalRecipientEmail)

            if (!isCurrentUserSender && !isCurrentUserRecipient) return IcsSurgeryUtils.HandleIcsResult.Error.PartyCrasher

            val attendeeEmails = iCalendar.events.first().attendees.mapNotNull { it.extractEmail() }
            val canonicalAttendeeEmails = canonicalizeProtonEmails(attendeeEmails, forceCanonicalization = true)

            if (isOrganizerMode && isCurrentUserRecipient && !canonicalAttendeeEmails.values.contains(canonicalSenderEmail)) return IcsSurgeryUtils.HandleIcsResult.Error.PartyCrasher
        }

        // METHOD: We support REQUEST, CANCEL, REPLY.
        if (iCalendar.method.isAdd) {
            // TODO Remove once ADD is handled
            return if (isOrganizerMode) IcsSurgeryUtils.HandleIcsResult.Error.Invalid.Method
            else IcsSurgeryUtils.HandleIcsResult.Error.Unsupported.Add
        }
        if (iCalendar.method.isCounter) {
            // TODO Remove once COUNTER is handled
            return if (isOrganizerMode) IcsSurgeryUtils.HandleIcsResult.Error.Unsupported.Counter
            else IcsSurgeryUtils.HandleIcsResult.Error.Invalid.Method
        }
        if (iCalendar.method.isRefresh) {
            // TODO Remove once REFRESH is handled
            return if (isOrganizerMode) IcsSurgeryUtils.HandleIcsResult.Error.Unsupported.Refresh
            else IcsSurgeryUtils.HandleIcsResult.Error.Invalid.Method
        }

        if (!iCalendar.method.isRequest &&
            !iCalendar.method.isCancel &&
            !iCalendar.method.isReply) return IcsSurgeryUtils.HandleIcsResult.Error.Invalid.Method // TODO Remove once other methods are handled

        if (iCalendar.method.isCancel && isOrganizerMode) return IcsSurgeryUtils.HandleIcsResult.Error.Invalid.Method

        if (isOrganizerMode && iCalendar.method.isReply && iCalendar.events.first().recurrenceId?.value != null)
            return IcsSurgeryUtils.HandleIcsResult.Error.Unsupported.SingleEditReply

        // Try to extract the current user from the attendee list if it exists
        val userAttendee = iCalendar.events.first().attendees.find { attendee ->
            canonicalUserEmails.firstOrNull { canonicalUserEmail ->
                val attendeeEmail = attendee.extractEmail()
                attendeeEmail != null && canonicalizeProtonEmail(attendeeEmail, forceCanonicalization = true).equals(canonicalUserEmail, ignoreCase = true)
            } != null
        }

        // Make sure all attendees have a part stat (default is NEEDS-ACTION)
        iCalendar.events.first().attendees.forEach {
            if (it.participationStatus == null) it.participationStatus = ParticipationStatus.NEEDS_ACTION

            // When in organizer mode, we do not accept invalid emails, as there we require the attendee email to be canonicalizable to generate the token
            if (isOrganizerMode && it.extractEmail() == null) {
                logger.i("HandleIcsUseCase organizer mode invalid attendee email")
                return IcsSurgeryUtils.HandleIcsResult.Error.Invalid.Attendees
            }
        }

        // If current user is not in the attendee list and is not the organizer then it is a party crasher
        if (!isOrganizerMode && userAttendee == null) return IcsSurgeryUtils.HandleIcsResult.Error.PartyCrasher

        // Use the default calendar to create the event
        val defaultCalendarId = calendarsRepository.getDefaultCalendarIdOrFirstActiveId(userId.id)
            ?: return IcsSurgeryUtils.HandleIcsResult.Error.NoDefaultCalendarFound
        var defaultCalendar = calendarsRepository.selectCalendar(defaultCalendarId)
        if (defaultCalendar == null || !defaultCalendar.isActive) {
            defaultCalendar = calendarsRepository.selectActiveUserCalendars(userId.id).firstOrNull()
                ?: return IcsSurgeryUtils.HandleIcsResult.Error.NoDefaultCalendarFound
        }

        // Fetch all events sharing UID from BE
        val eventsSharingUidResponse = (
                calendarsRepository.getEventsByUid(userId, iCalendar.events.first().uid.value).valueOrNullAndLogErrors(logger)
                    ?: return IcsSurgeryUtils.HandleIcsResult.Error.NetworkError
                ).events

        // IMPORTANT: We need parent event to clean recurrence id
        val parentEventEntity = eventsSharingUidResponse.firstOrNull { eventEntity ->
            eventEntity.sharedEvents.any {
                try {
                    // only root event contains RRULE
                    it.jsonObject.get("Data")?.jsonPrimitive?.content?.contains("RRULE:") == true
                } catch (e: IllegalArgumentException) {
                    false
                }
            }
        }
        val parentEvent = if (parentEventEntity != null) {
            if (FeatureFlag.USE_EVENT_DECRYPTOR) {
                eventDecryptor.decrypt(parentEventEntity)
            } else {
                transformEventUseCase.execute(parentEventEntity)
            }
        } else null

        // IMPORTANT: Unlike the rest of the surgery, clean recurrence id is called outside of cleanIcs, but it is still mandatory
        if (!iCalendar.cleanRecurrenceId(iCalendar.method == Method.reply(), parentEvent?.iCalendar)) {
            logger.i("HandleIcsUseCase error invalid recurrence id")
            return IcsSurgeryUtils.HandleIcsResult.Error.Invalid.RecurrenceId
        }

        // Find an existing event from the ones sharing the same UID
        var existingEvent: Event? = null
        var existingEventEntity: EventEntity? = null
        eventsSharingUidResponse.let {
            for (eventEntity in eventsSharingUidResponse) {
                val event = if (FeatureFlag.USE_EVENT_DECRYPTOR) {
                    eventDecryptor.decrypt(eventEntity)
                } else {
                    transformEventUseCase.execute(eventEntity)
                }
                if (event?.iCalEvent?.recurrenceId == iCalendar.events.first().recurrenceId) {
                    existingEvent = event
                    existingEventEntity = eventEntity
                    calendarsRepository.persistEvents(*(listOf(eventEntity)).toTypedArray())
                    break
                }
            }
        }

        val immutableExistingEvent = existingEvent
        val immutableExistingEventEntity = existingEventEntity

        val attendees = immutableExistingEvent?.iCalEvent?.attendees
        // Check using the original event that the reply sender is indeed an attendee
        if (iCalendar.method.isReply && isOrganizerMode && canonicalSenderEmail.isNotBlank() && attendees != null &&
            attendees.find {
                canonicalizeProtonEmail(it.extractEmail() ?: "", forceCanonicalization = true).equals(canonicalSenderEmail)
            } == null) return IcsSurgeryUtils.HandleIcsResult.Error.ReplyPartyCrasher(immutableExistingEvent.id)

        if (iCalendar.method.isReply && !isOrganizerMode) {
            return IcsSurgeryUtils.HandleIcsResult.Error.Method(existingEvent?.id)
        }
        if (existingEvent?.decryptionStatus == Event.DecryptionStatus.FAILURE) return IcsSurgeryUtils.HandleIcsResult.Error.DecryptionFailed(existingEvent?.id, existingEvent?.calendar?.id, existingEvent?.isRecurring())
        if (existingEvent?.calendar?.isActive == false) return IcsSurgeryUtils.HandleIcsResult.Error.DisabledCalendar(existingEvent?.id)

        if (isCurrentUserSender && existingEvent != null) {
            // We are opening an invite sent by the current user, no changes are needed, open event details
            existingEvent?.let { makeCalendarVisible(it, userId) }
            return IcsSurgeryUtils.HandleIcsResult.Success(existingEvent?.id ?: return IcsSurgeryUtils.HandleIcsResult.Error.EventNotFound, IcsSurgeryUtils.HandleIcsAction.OPEN_EVENT, isRecurring = existingEvent?.isRecurring())
        }

        val isNew = eventsSharingUidResponse.isNullOrEmpty() || existingEvent == null || (existingEvent != null && existingEvent?.decryptionStatus == Event.DecryptionStatus.FAILURE)

        // If a series already exist, use the same calendar, else use the default one
        val existingCalendar = if (!eventsSharingUidResponse.isNullOrEmpty()) {
            val existingCalendarId = eventsSharingUidResponse.first().calendarId
            val existingCalendarEntity = calendarsRepository.selectCalendar(existingCalendarId)

            if (existingCalendarEntity?.isActive == false) {
                // If calendar is disabled, display error message and try to open event details
                return if (existingEvent != null) IcsSurgeryUtils.HandleIcsResult.Error.DisabledCalendar(existingEvent?.id)
                else IcsSurgeryUtils.HandleIcsResult.Error.DisabledCalendar(null)
            }

            existingCalendarEntity
        } else null

        // Create a new event with the clean iCalendar
        val newEvent = Event.from(
            ICalUtilsImpl.generateOfflineEventId(), Calendar(
                existingCalendar?.id ?: defaultCalendar.id,
                existingCalendar?.name ?: defaultCalendar.name,
                existingCalendar?.email ?: defaultCalendar.email,
                existingCalendar?.color ?: defaultCalendar.color,
                existingCalendar?.flags ?: defaultCalendar.flags,
                if (existingCalendar != null) existingCalendar.display else defaultCalendar.display,
                existingCalendar?.type ?: defaultCalendar.type,
                existingCalendar?.permissions ?: defaultCalendar.permissions
            ), iCalendar, Instant.now().epochSecond) ?: return IcsSurgeryUtils.HandleIcsResult.Error.ParsingFailed

        val isNewNonCancelled  = isNew && !isOrganizerMode && !iCalendar.method.isCancel
        val isNewSingleEditCancelled = isNew && existingEvent == null && iCalendar.method.isCancel

        val isReInvitation = newEvent.iCalendar.method.isRequest && !newEvent.isCancelled() && existingEvent != null && existingEvent?.isCancelled() == true
        if (isReInvitation && immutableExistingEvent != null) {
            val deleteResult = handleDeleteUseCase.handleDelete(userId, immutableExistingEvent.id, immutableExistingEvent.calendar.id, EventEditDeleteOption.ALL_EVENTS, null)
            if (deleteResult !is UseCase.Result.Success<*>) {
                deleteResult.ifSuccessAndLogErrors(logger) {}
                return IcsSurgeryUtils.HandleIcsResult.Error.EditCreateEventError(
                    when (deleteResult) {
                        is UseCase.Result.Error -> deleteResult.userErrorMessage
                        is UseCase.Result.InvalidParams -> deleteResult.userErrorMessage
                        else -> null
                    }
                )
            }
        }

        if (isNewNonCancelled || isNewSingleEditCancelled || isReInvitation) {
            // Create brand new event
            if (!newEvent.iCalendar.setAttendeesXPmToken(userId, isOrganizerMode)) {
                logger.i("HandleIcsUseCase error failed to set attendees xpm token")
                return IcsSurgeryUtils.HandleIcsResult.Error.Invalid.Attendees
            }
            if (isNewSingleEditCancelled) {
                newEvent.iCalendar.method = Method.request()
                newEvent.iCalEvent.status = Status.cancelled() // In case of un-invite, the status needs to be set to cancelled
            }
            return editCreateEventFromIcs(
                IcsSurgeryUtils.HandleIcsAction.CREATE_EVENT,
                userId,
                newEvent
            )
        } else {
            // Event already exists, check if we need to update it using the ics content
            if (!isOrganizerMode && immutableExistingEvent != null && immutableExistingEventEntity != null && newEvent.iCalEvent.dateTimeStamp.value.after(immutableExistingEvent.iCalEvent.dateTimeStamp?.value)) {
                if (immutableExistingEventEntity.isProtonProtonInvite?.toBoolean() == true || immutableExistingEvent.sharedEventId == newEvent.iCalEvent.getExperimentalProperty(X_PM_SHARED_EVENT_ID)?.value) {
                    // Event is a proton to proton invite
                    // Fetch event to make sure we have the latest version
                    makeCalendarVisible(immutableExistingEvent, userId)
                    return IcsSurgeryUtils.HandleIcsResult.Success(immutableExistingEvent.id, IcsSurgeryUtils.HandleIcsAction.OPEN_EVENT, isRecurring = immutableExistingEvent.isRecurring())
                }
                if (!newEvent.iCalendar.setAttendeesXPmToken(userId, isOrganizerMode)) return IcsSurgeryUtils.HandleIcsResult.Error.Invalid.Attendees
                return updateEventAsAnAttendee(newEvent, immutableExistingEvent, canonicalUserEmails, userAttendee, userId)
            } else if (isOrganizerMode && immutableExistingEvent != null && immutableExistingEventEntity != null && !iCalendar.events.first().attendees.isNullOrEmpty()) {
                if (newEvent.hasProtonProtonProperties || newEvent.isProtonProtonReply) {
                    // Attendee added the event as a Proton to Proton invite
                    // Fetch event to make sure we have the latest version
                    makeCalendarVisible(immutableExistingEvent, userId)
                    return IcsSurgeryUtils.HandleIcsResult.Success(immutableExistingEvent.id, IcsSurgeryUtils.HandleIcsAction.OPEN_EVENT, isRecurring = immutableExistingEvent.isRecurring())
                }
                return updateEventAsAnOrganizer(immutableExistingEvent, immutableExistingEventEntity, iCalendar, userId)
            } else if (isOrganizerMode && immutableExistingEvent == null) {
                return IcsSurgeryUtils.HandleIcsResult.Error.EventDeleted
            }
        }

        if (immutableExistingEventEntity != null && immutableExistingEvent != null) {
            // Fetch event to make sure we have the latest version
            makeCalendarVisible(immutableExistingEvent, userId)
        }

        // If no update is needed, return the existing event id
        return IcsSurgeryUtils.HandleIcsResult.Success(immutableExistingEvent?.id ?: return IcsSurgeryUtils.HandleIcsResult.Error.EventNotFound, IcsSurgeryUtils.HandleIcsAction.OPEN_EVENT, isRecurring = immutableExistingEvent.isRecurring())
    }

    private suspend fun ICalendar.setAttendeesXPmToken(userId: UserId, isOrganizerMode: Boolean): Boolean {
        val missingToken = this.events.first().attendees.firstOrNull { attendee ->
            attendee.getParameter(X_PM_TOKEN) == null
        } != null
        val eventUid = this.events.first().uid?.value
        if (missingToken && eventUid != null) {
            val canonicalEmails = canonicalEmailsUseCase.invoke(userId, this.events.first().attendees.mapNotNull { it.extractEmail() })
            if (canonicalEmails.values.any { it.isNullOrEmpty() }) return false
            this.events.first().attendees.forEach { attendee ->
                val attendeeCanonicalEmail =
                    if (attendee.extractEmail() != null) canonicalEmails[attendee.extractEmail()]
                    else if (isOrganizerMode) return false
                    else attendee.email ?: return false // We still need to generate tokens for invalid emails when in attendee mode
                if (attendee.getParameter(X_PM_TOKEN) == null && attendeeCanonicalEmail != null) {
                    val token = ICalUtilsImpl.generateXPmToken(attendeeCanonicalEmail, eventUid)
                    attendee.addParameter(X_PM_TOKEN, token)
                }
            }
        }
        return true
    }

    private suspend fun updateEventAsAnAttendee(newEvent: Event, existingEvent: Event, canonicalUserEmails: List<String>?, userAttendee: Attendee?, userId: UserId): IcsSurgeryUtils.HandleIcsResult {
        // Update existing event as an attendee

        val newICalendar = newEvent.iCalendar.clone()

        val updatedEvent = if (newICalendar.method.isCancel) {
            // TODO Cancel just one occurrence: if the ICS contains a RECURRENCE-ID which matches an occurrence of the series for which no previous single edit exists.
            //  In that case you have to create a single edit with status CANCELLED and no alarms.

            // Cancel the event via the sync route by changing STATUS, DTSTAMP (update with the ICS DTSTAMP), and drop the alarms
            existingEvent.iCalEvent.status = Status.cancelled()
            existingEvent.iCalEvent.alarms?.clear()
            existingEvent.iCalEvent.dateTimeStamp = newICalendar.events.first().dateTimeStamp
            existingEvent
        } else {
            canonicalUserEmails?.let {
                val currentParticipationStatus = existingEvent.getParticipationStatus(canonicalUserEmails)

                val currentSequence = existingEvent.iCalEvent.sequence?.value
                if (currentSequence != null && currentSequence < newEvent.iCalEvent.sequence.value) {
                    // Sequence changed, clear participation status
                    newICalendar.events.first().attendees.firstOrNull { it == userAttendee }?.participationStatus =
                        ParticipationStatus.NEEDS_ACTION
                } else {
                    // Sequence did not change, keep current participation status
                    newICalendar.events.first().attendees.firstOrNull { it == userAttendee }?.participationStatus =
                        currentParticipationStatus

                    // Copy existing alarms
                    newICalendar.events.first().alarms.clear()
                    newICalendar.events.first().alarms.addAll(existingEvent.iCalEvent.alarms)
                }
            }

            Event.from(existingEvent)
        }

        return editCreateEventFromIcs(
            IcsSurgeryUtils.HandleIcsAction.UPDATE_EVENT,
            userId,
            updatedEvent
        )
    }

    private suspend fun updateEventAsAnOrganizer(existingEvent: Event, existingEventEntity: EventEntity, iCalendar: ICalendar, userId: UserId): IcsSurgeryUtils.HandleIcsResult {
        // Update existing event as an organizer

        // Handle party crashers in replies
        val updatedAttendee = iCalendar.events.first().attendees.firstOrNull()
        val updatedAttendeeEmail = updatedAttendee?.extractEmail() ?: return IcsSurgeryUtils.HandleIcsResult.Error.EditCreateEventError()
        val canonicalAttendeeEmail = canonicalEmailsUseCase.invoke(userId, listOf(updatedAttendeeEmail))[updatedAttendeeEmail] ?: return IcsSurgeryUtils.HandleIcsResult.Error.EditCreateEventError()
        val existingEventCanonicalAttendeeEmails = canonicalEmailsUseCase.invoke(userId, existingEvent.iCalEvent.attendees.mapNotNull { it.extractEmail() })

        if (existingEvent.iCalEvent.attendees?.none {
                canonicalAttendeeEmail == existingEventCanonicalAttendeeEmails[it.extractEmail()]
            } == true) return IcsSurgeryUtils.HandleIcsResult.Error.ReplyPartyCrasher(existingEvent.id)

        // Get attendees part from existing event entity
        val attendees = existingEventEntity.attendees.map {
            json.decodeFromJsonElement<Event.AttendeeStatusEvent>(it)
        }

        existingEvent.iCalEvent.attendees?.forEach { attendee ->

            if (attendee.extractEmail().equals(updatedAttendeeEmail, true)) {
                val attendeeToken = attendee.getParameter(X_PM_TOKEN)
                val attendeeStatusEvent = attendees.find { it.token == attendeeToken }

                // Find the current update time value for this attendee from the event entity attendees part
                val existingUpdateTime = attendeeStatusEvent?.updateTime
                val newUpdateTime = TimeUnit.MILLISECONDS.toSeconds(iCalendar.events.first().dateTimeStamp.value.time).toInt()

                // If new update time is more recent then update participation status, else do nothing and open event details
                if (existingUpdateTime == null || existingUpdateTime < newUpdateTime) {

                    val calendarId = existingEvent.calendar.id

                    // Update participation status for the attendee that replied
                    val updateParticipationStatusUseCaseResult = updateParticipationStatusUseCase.execute(
                        userId,
                        calendarId,
                        existingEvent.id,
                        attendeeStatusEvent?.id ?: return IcsSurgeryUtils.HandleIcsResult.Error.EditCreateEventError(),
                        updatedAttendee.participationStatus.toInt(),
                        null,
                        newUpdateTime
                    )

                    if (updateParticipationStatusUseCaseResult !is UseCase.Result.Success<*>) {
                        updateParticipationStatusUseCaseResult.ifSuccessAndLogErrors(logger) { }
                        return IcsSurgeryUtils.HandleIcsResult.Error.EditCreateEventError()
                    }

                    makeCalendarVisible(existingEvent, userId)

                    return IcsSurgeryUtils.HandleIcsResult.Success(
                        eventId = existingEvent.id,
                        IcsSurgeryUtils.HandleIcsAction.UPDATE_EVENT,
                        Pair(updatedAttendeeEmail, updatedAttendee.participationStatus),
                        isRecurring = existingEvent.isRecurring()
                    )
                }
            }
        }

        makeCalendarVisible(existingEvent, userId)
        return IcsSurgeryUtils.HandleIcsResult.Success(existingEvent.id, IcsSurgeryUtils.HandleIcsAction.OPEN_EVENT, isRecurring = existingEvent.isRecurring())
    }

    private suspend fun editCreateEventFromIcs(action: IcsSurgeryUtils.HandleIcsAction, userId: UserId, newEvent: Event): IcsSurgeryUtils.HandleIcsResult {
        val createLinkedEventAsAttendee =
            action == IcsSurgeryUtils.HandleIcsAction.CREATE_EVENT && newEvent.hasProtonProtonProperties
        when (val editCreateEventResult = editCreateEventUseCase.execute(userId, newEvent, createLinkedEventAsAttendee = createLinkedEventAsAttendee)) {
            is UseCase.Result.Success<*> -> {
                var eventId: String? = null
                editCreateEventResult.returnValue.tryCast<List<String>> {
                    eventId = this.firstOrNull()
                }

                makeCalendarVisible(newEvent, userId)

                return IcsSurgeryUtils.HandleIcsResult.Success(eventId = eventId ?: return IcsSurgeryUtils.HandleIcsResult.Error.EditCreateEventError(), action, isRecurring = newEvent.isRecurring())
            }
            is UseCase.Result.InvalidParams -> {
                logger.i("HandleIcsUseCase: invalid params in create event: ${editCreateEventResult.message}")
                return IcsSurgeryUtils.HandleIcsResult.Error.EditCreateEventError(editCreateEventResult.userErrorMessage)
            }
            is UseCase.Result.Error -> {
                logger.i("HandleIcsUseCase: error in create event: ${editCreateEventResult.message}")
                return IcsSurgeryUtils.HandleIcsResult.Error.EditCreateEventError(editCreateEventResult.userErrorMessage)
            }
        }
    }

    private suspend fun makeCalendarVisible(event: Event, userId: UserId) {
        if (!event.calendar.display) {
            // 1. Update in DB
            calendarsRepository.updateCalendarDisplay(event.calendar.id, true)
            // 2. Update on Server
            updateCalendarUseCase.executeUpdateFromDb(userId, event.calendar.id)
        }
    }
}
