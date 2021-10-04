package me.proton.android.calendar.mocks

import biweekly.parameter.ParticipationStatus
import biweekly.property.Attendee
import me.proton.android.calendar.common.ICalUtilsImpl
import me.proton.android.calendar.common.ICalUtilsImpl.setDefaultTimeZone
import me.proton.android.calendar.data.entity.CalendarFlags
import me.proton.android.calendar.data.entity.EventEntity
import me.proton.android.calendar.domain.model.Calendar
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.mocks.CalendarMocks.getCalendar
import me.proton.core.util.kotlin.toBoolean

object EventMocks {

    fun getEvent(isRecurring: Boolean = false, hasAttendees: Boolean = false, isSingleEdit: Boolean = false, hasDisabledCalendar: Boolean = false): Event {

        val ics = when {
            isRecurring -> recurringIcs
            isSingleEdit -> singleEditIcs
            else -> baseIcs
        }
        val iCalendar = ICalUtilsImpl.parseICalString(ics)!!

        // Event with attendees
        if (hasAttendees) {
            val attendee = Attendee(
                attendeeName,
                attendeeEmail
            )
            attendee.participationStatus = ParticipationStatus.ACCEPTED
            iCalendar.events.first().addAttendee(attendee)
        }

        iCalendar.setDefaultTimeZone(defaultTimezone)

        return Event.from(
            if (isSingleEdit) singleEditEventId else eventId,
            getCalendar(hasDisabledCalendar),
            iCalendar,
            null // TODO
        )!!
    }

    fun getEventEntity(isSingleEdit: Boolean = false): EventEntity {
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
