package me.proton.android.calendar.mocks

import biweekly.parameter.ParticipationStatus
import biweekly.property.Attendee
import biweekly.property.Organizer
import me.proton.android.calendar.common.ICalUtilsImpl
import me.proton.android.calendar.common.ICalUtilsImpl.setDefaultTimeZone
import me.proton.android.calendar.data.entity.EventEntity
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.mocks.CalendarMocks.getCalendar

object EventMocks {

    fun getEvent(
        isRecurring: Boolean = false,
        isOrganizer: Boolean = false,
        isAttendee: Boolean = false,
        isProtonProtonInvite: Boolean? = null,
        isSingleEdit: Boolean = false,
        hasDisabledCalendar: Boolean = false,
        participationStatus: ParticipationStatus = ParticipationStatus.DECLINED
    ): Event {

        val ics = when {
            isRecurring -> recurringIcs
            isSingleEdit -> singleEditIcs
            else -> baseIcs
        }
        val iCalendar = ICalUtilsImpl.parseICalString(ics)!!

        // Event with attendees
        if (isOrganizer) {
            // Set default attendee
            val attendee = Attendee(
                attendeeName,
                attendeeEmail
            )
            attendee.participationStatus = ParticipationStatus.DECLINED
            iCalendar.events.first().addAttendee(attendee)

            // Set default user as the organizer
            val organizer = Organizer(
                userName,
                userEmail
            )
            iCalendar.events.first().organizer = organizer
        }
        if (isAttendee) {
            // Set default user as an attendee
            val attendee = Attendee(
                userName,
                userEmail
            )
            attendee.participationStatus = participationStatus
            iCalendar.events.first().addAttendee(attendee)

            // Set default organizer
            val organizer = Organizer(
                organizerName,
                organizerEmail
            )
            iCalendar.events.first().organizer = organizer
        }

        iCalendar.setDefaultTimeZone(defaultTimezone)

        return Event.from(
            if (isSingleEdit) singleEditEventId else eventId,
            getCalendar(hasDisabledCalendar),
            iCalendar,
            null, // TODO
            isProtonProtonInvite = isProtonProtonInvite,
            currentUserAttendeeId = if (isAttendee) attendeeId else null
        )!!
    }

    fun getEventEntity(
        isSingleEdit: Boolean = false,
        isProtonProtonInvite: Boolean = false
    ): EventEntity {
        return EventEntity(
            if (isSingleEdit) singleEditEventId else eventId,
            calendarId,
            sharedEventId,
            calendarKeyPacket,
            0L,
            0L,
            1,
            sharedKeyPacket,
            emptyList(),
            emptyList(),
            emptyList(),
            emptyList(),
            emptyList(),
            null
        )
    }

}
