package me.proton.android.calendar.mocks

import biweekly.component.VAlarm
import biweekly.parameter.ParticipationStatus
import biweekly.parameter.Related
import biweekly.property.Attendee
import biweekly.property.ExceptionDates
import biweekly.property.Organizer
import biweekly.property.Trigger
import biweekly.util.Duration
import biweekly.util.ICalDate
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import me.proton.android.calendar.common.utils.ICalUtilsImpl
import me.proton.android.calendar.common.utils.ICalUtilsImpl.setDefaultTimeZone
import me.proton.android.calendar.data.entity.EventEntity
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.mocks.CalendarMocks.provideCalendar
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*

object EventMocks {

    fun provideEvent(
        isRecurring: Boolean = false,
        isOrganizer: Boolean = false,
        isAttendee: Boolean = false,
        isProtonProtonInvite: Boolean? = null,
        isSingleEdit: Boolean = false,
        hasDisabledCalendar: Boolean = false,
        hasExDate: Boolean = false,
        hasHiddenCalendar: Boolean = false,
        hasDefaultAlarms: Boolean = true,
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

        if (hasExDate) {
            // Ex date on fourth occurrence
            val exceptionDates = ExceptionDates()
            val newUntilDate = ZonedDateTime.of(
                LocalDate.of(2021, 9, 17),
                LocalTime.of(15, 30),
                ZoneId.of(defaultTimezone)
            ).toInstant()
            exceptionDates.values.add(ICalDate(Date.from(newUntilDate), true))
            iCalendar.events.first().addExceptionDates(exceptionDates)
        }

        if (hasDefaultAlarms) {
            // One alarm 15 minutes before
            iCalendar.events.first().addAlarm(VAlarm.display(Trigger(Duration.builder().prior(true).minutes(15).build(), Related.START), null))
        }

        iCalendar.setDefaultTimeZone(defaultTimezone)

        return Event.from(
            if (isSingleEdit) singleEditEventId else eventId,
            provideCalendar(hasDisabledCalendar, hasHiddenCalendar),
            iCalendar,
            null, // TODO
            isProtonProtonInvite = isProtonProtonInvite,
            currentUserAttendeeId = if (isAttendee) attendeeId else null
        )!!
    }

    fun provideEventEntity(
        isSingleEdit: Boolean = false,
        isProtonProtonInvite: Boolean = false,
        hasAttendees: Boolean = false
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
            emptyList(), // TODO Mock attendeesEvents too ?
            if (hasAttendees) listOf(Json.decodeFromString<JsonElement>("{\"Type\":2,\"Data\":\"BEGIN:VCALENDAR\\r\\nVERSION:2.0\\r\\nBEGIN:VEVENT\\r\\nUID:4vi99f4jksbjr1472nir63suvk@google.com\\r\\nATTENDEE;CN=calendarsingle9@proton.dev;ROLE=REQ-PARTICIPANT;RSVP=TRUE;X-PM-\\r\\n TOKEN=905eb4e54055cdb47d9edf7e8f6a778bca369a97:mailto:calendarsingle9@proto\\r\\n n.dev\\r\\nEND:VEVENT\\r\\nEND:VCALENDAR\",\"Signature\":\"-----BEGIN PGP SIGNATURE-----\\r\\nVersion: OpenPGP.js v4.10.8\\r\\nComment: https://openpgpjs.org\\r\\n\\r\\nwnUEARYKAAYFAl+PFFEAIQkQvfbFISx9GWsWIQSFmVz3NIh7rYVWIdi99sUh\\r\\nLH0Za8a+AQDA/zlaCVvaSnlRv6HBLyScDTgUhbUE1ArnLaY0G2ot9wD/XGPE\\r\\nB9Ou63paO4mHQJOzBw9LQe6k2HA24doWDD8cfQA=\\r\\n=/tFw\\r\\n-----END PGP SIGNATURE-----\\r\\n\",\"Author\":\"calendarSingle9@proton.dev\"}"))
            else emptyList(),
            null
        )
    }

}
