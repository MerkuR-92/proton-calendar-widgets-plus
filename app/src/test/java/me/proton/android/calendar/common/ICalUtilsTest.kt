package me.proton.android.calendar.common

import assertk.assertThat
import assertk.assertions.*
import biweekly.util.Frequency
import biweekly.util.Recurrence
import me.proton.android.calendar.common.ICalUtils.sanitise
import me.proton.android.calendar.domain.model.Event
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.time.*
import java.util.*


internal class ICalUtilsTest {

    @Test
    fun `all timezone IDs allowed by server are correctly recognised`() {

        val availableTimezoneIds = TimeZone.getAvailableIDs()

        allowedTimezoneIds.forEach {
            assertThat(availableTimezoneIds.contains(it)).isTrue()
        }

        assertThat(availableTimezoneIds.contains("Non/Existing_Timezone")).isFalse()

    }

    @Test
    fun `all-day event has no time and no timezone property`() {

        val event = ICalUtils.createNewEvent()
        event.setStart(LocalDate.of(2020, 1, 20))
        event.setEnd(LocalDate.of(2020, 1, 22))

        val calendar = event.wrapInICalendar()

        assertThat(event.dateStart.value.hasTime()).isFalse()
        assertThat(event.dateEnd.value.hasTime()).isFalse()

        assertThat(calendar.timezoneInfo.defaultTimezone).isNull()
        assertThat(calendar.timezoneInfo.getTimezone(event.dateStart)).isNull()
        assertThat(calendar.timezoneInfo.getTimezone(event.dateEnd)).isNull()

    }

    @Test
    fun `partial-day event has correct time and timezone property`() {

        val event = ICalUtils.createNewEvent()
        event.setStart(LocalDate.of(2020, 1, 20), LocalTime.of(10, 0), "Europe/Zurich")
        event.setEnd(LocalDate.of(2020, 1, 20), LocalTime.of(11, 0), "Europe/Zurich")

        val calendar = event.wrapInICalendar()
        calendar.setStartTimeZone("Europe/Zurich")
        calendar.setEndTimeZone("Europe/Zurich")

        assertThat(event.dateStart.value.hasTime()).isTrue()
        assertThat(event.dateEnd.value.hasTime()).isTrue()

        val printedICal = calendar.printToString()
        assertThat(printedICal).contains("DTSTART;TZID=Europe/Zurich:20200120T100000")
        assertThat(printedICal).contains("DTEND;TZID=Europe/Zurich:20200120T110000")
    }

    @Test
    fun `multiple edits of datetimes and timezones properly overwrite old values`() {

        val event = ICalUtils.createNewEvent()
        event.setStart(LocalDate.of(2020, 1, 20), LocalTime.of(10, 0), "Europe/Zurich")
        event.setEnd(LocalDate.of(2020, 1, 20), LocalTime.of(11, 0), "Europe/Zurich")

        event.setStart(LocalDate.of(2020, 1, 20))
        event.setEnd(LocalDate.of(2020, 1, 20))

        assertThat(event.dateStart.value.hasTime()).isFalse()
        assertThat(event.dateEnd.value.hasTime()).isFalse()

        val calendar = event.wrapInICalendar()
        calendar.setStartTimeZone("Europe/Zurich")

        assertThat(calendar.timezoneInfo.getTimezone(event.dateStart).component.timezoneId.value).isEqualTo("Europe/Zurich")

        calendar.setStartTimeZone(null)

        assertThat(calendar.timezoneInfo.getTimezone(event.dateStart)).isNull()
    }

    @Test
    fun `adjust time zones of start & end dates for partial-day event`() {

        val originalTimeZoneId = "Pacific/Saipan"
        val requestedTimeZoneId = "Europe/Vilnius"
        val requestedStartHour = 10
        val requestedEndHour = 15

        val event = ICalUtils.createNewEvent()
        event.setStart(LocalDate.of(2020, 1, 20), LocalTime.of(requestedStartHour, 0, 10), originalTimeZoneId)
        event.setEnd(LocalDate.of(2020, 1, 20), LocalTime.of(requestedEndHour, 0, 10), originalTimeZoneId)

        val calendar = event.wrapInICalendar()

        TestsLogger.d("${calendar.printToString()}")

        calendar.adjustStartEndTimeZones(originalTimeZoneId, requestedTimeZoneId)

        // TODO assert for empty timezones in assignments

        TestsLogger.d("${calendar.printToString()}")
        assertThat(calendar.timezoneInfo.getTimezone(event.dateStart).component.timezoneId.value).isEqualTo(requestedTimeZoneId)
        assertThat(calendar.timezoneInfo.getTimezone(event.dateEnd).component.timezoneId.value).isEqualTo(requestedTimeZoneId)

        val printedICal = calendar.printToString()
        assertThat(printedICal).contains("DTSTART;TZID=${requestedTimeZoneId}:20200120T${requestedStartHour}0000")
        assertThat(printedICal).contains("DTEND;TZID=${requestedTimeZoneId}:20200120T${requestedEndHour}0000")

    }

    @Test
    fun `adjust iCalendar for all-day event`() {

        val timeZoneId = "Europe/Zurich"

        // set
        val event = ICalUtils.createNewEvent()
        event.setStart(LocalDate.of(2020, 1, 20), LocalTime.of(1, 0), timeZoneId)
        event.setEnd(LocalDate.of(2020, 1, 22), LocalTime.of(2, 0), "Europe/Zurich")

        val calendar = event.wrapInICalendar()
        calendar.setStartTimeZone(timeZoneId)
        calendar.setEndTimeZone(timeZoneId)

        calendar.adjustOutgoingAllDayEvent(timeZoneId)

        assertThat(calendar.events.first().dateStart.value.hasTime()).isFalse()
        assertThat(calendar.events.first().dateEnd.value.hasTime()).isFalse()

        assertThat(calendar.timezoneInfo.timezones).isEmpty()
        assertThat(calendar.timezoneInfo.getTimezone(event.dateStart)).isNull()
        assertThat(calendar.timezoneInfo.getTimezone(event.dateEnd)).isNull()

        assertThat(ZonedDateTime.ofInstant(event.dateStart.value.toInstant(), ZoneId.of(timeZoneId)).dayOfMonth).isEqualTo(20)
        assertThat(ZonedDateTime.ofInstant(event.dateEnd.value.toInstant(), ZoneId.of(timeZoneId)).dayOfMonth).isEqualTo(23)
//        assertThat(event.dateEnd.value.date).isEqualTo(23) // according to iCal standard, all-day event ends at the start-of-day of the following day

    }

    @Test
    fun `all-day event overlaps with full day range`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    DTSTART;VALUE=DATE:20200626
    DTEND;VALUE=DATE:20200627
    SUMMARY:event
    UID:EGVy407XddW2_ESpoOVn7oN1qc9V@proton.me
    DTSTAMP:20200625T143822Z
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val iCal = ICalUtils.parseICalString(iCalString)!!
        val event = Event("id", me.proton.android.calendar.domain.model.Calendar("id", "calendar", ""), iCal, null)
        val displayTimeZoneId = "Europe/Zurich"

        assertThat(event.overlapsWithFullDayRange(
            LocalDate.of(2020, 6, 25),
            LocalDate.of(2020, 6, 25),
            displayTimeZoneId
        )).isFalse()

        assertThat(event.overlapsWithFullDayRange(
            LocalDate.of(2020, 6, 25),
            LocalDate.of(2020, 6, 26),
            displayTimeZoneId
        )).isTrue()

        assertThat(event.overlapsWithFullDayRange(
            LocalDate.of(2020, 6, 26),
            LocalDate.of(2020, 6, 26),
            displayTimeZoneId
        )).isTrue()

        assertThat(event.overlapsWithFullDayRange(
            LocalDate.of(2020, 6, 26),
            LocalDate.of(2020, 6, 27),
            displayTimeZoneId
        )).isTrue()

        assertThat(event.overlapsWithFullDayRange(
            LocalDate.of(2020, 6, 27),
            LocalDate.of(2020, 6, 27),
            displayTimeZoneId
        )).isFalse()

    }

    @Test
    fun `multi-day all-day all-day event overlaps with full day range`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    DTSTART;VALUE=DATE:20200626
    DTEND;VALUE=DATE:20200629
    SUMMARY:event
    UID:EGVy407XddW2_ESpoOVn7oN1qc9V@proton.me
    DTSTAMP:20200625T143822Z
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val iCal = ICalUtils.parseICalString(iCalString)!!
        val event = Event("id", me.proton.android.calendar.domain.model.Calendar("id", "calendar", ""), iCal, null)
        val displayTimeZoneId = "Europe/Zurich"

        assertThat(event.overlapsWithFullDayRange(
            LocalDate.of(2020, 6, 25),
            LocalDate.of(2020, 6, 25),
            displayTimeZoneId
        )).isFalse()

        assertThat(event.overlapsWithFullDayRange(
            LocalDate.of(2020, 6, 25),
            LocalDate.of(2020, 6, 26),
            displayTimeZoneId
        )).isTrue()

        assertThat(event.overlapsWithFullDayRange(
            LocalDate.of(2020, 6, 26),
            LocalDate.of(2020, 6, 26),
            displayTimeZoneId
        )).isTrue()

        assertThat(event.overlapsWithFullDayRange(
            LocalDate.of(2020, 6, 27),
            LocalDate.of(2020, 6, 27),
            displayTimeZoneId
        )).isTrue()

        assertThat(event.overlapsWithFullDayRange(
            LocalDate.of(2020, 6, 28),
            LocalDate.of(2020, 6, 29),
            displayTimeZoneId
        )).isTrue()

        assertThat(event.overlapsWithFullDayRange(
            LocalDate.of(2020, 6, 29),
            LocalDate.of(2020, 7, 2),
            displayTimeZoneId
        )).isFalse()

    }

    @Test
    fun `part-day event overlaps with full day range`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    DTSTART;TZID=/Europe/Budapest:20200625T000000
    DTEND;TZID=/Europe/Budapest:20200625T003000
    SUMMARY:part-day on 25th Jun, starts at midnight
    UID:EEB9eHnvGPpXK62b799jf9kL8OpG@proton.me
    DTSTAMP:20200625T133626Z
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val iCal = ICalUtils.parseICalString(iCalString)!!
        val event = Event("id", me.proton.android.calendar.domain.model.Calendar("id", "calendar", ""), iCal, null)
        val displayTimeZoneId = "Europe/Vilnius"

        assertThat(event.overlapsWithFullDayRange(
            LocalDate.of(2020, 6, 24),
            LocalDate.of(2020, 6, 24),
            displayTimeZoneId
        )).isFalse()

        assertThat(event.overlapsWithFullDayRange(
            LocalDate.of(2020, 6, 24),
            LocalDate.of(2020, 6, 25),
            displayTimeZoneId
        )).isTrue()

        assertThat(event.overlapsWithFullDayRange(
            LocalDate.of(2020, 6, 25),
            LocalDate.of(2020, 6, 25),
            displayTimeZoneId
        )).isTrue()

        assertThat(event.overlapsWithFullDayRange(
            LocalDate.of(2020, 6, 25),
            LocalDate.of(2020, 6, 26),
            displayTimeZoneId
        )).isTrue()

        assertThat(event.overlapsWithFullDayRange(
            LocalDate.of(2020, 6, 27),
            LocalDate.of(2020, 6, 27),
            displayTimeZoneId
        )).isFalse()

    }

    @Test
    fun `multi-day part-day event overlaps with full day range`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    DTSTART;TZID=/Europe/Budapest:20200625T000000
    DTEND;TZID=/Europe/Budapest:20200627T003000
    SUMMARY:part-day on 25th Jun until 27 Jun, starts at midnight
    UID:EEB9eHnvGPpXK62b799jf9kL8OpG@proton.me
    DTSTAMP:20200625T133626Z
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val iCal = ICalUtils.parseICalString(iCalString)!!
        val event = Event("id", me.proton.android.calendar.domain.model.Calendar("id", "calendar", ""), iCal, null)
        val displayTimeZoneId = "Europe/Vilnius"

        assertThat(event.overlapsWithFullDayRange(
            LocalDate.of(2020, 6, 24),
            LocalDate.of(2020, 6, 24),
            displayTimeZoneId
        )).isFalse()

        assertThat(event.overlapsWithFullDayRange(
            LocalDate.of(2020, 6, 24),
            LocalDate.of(2020, 6, 25),
            displayTimeZoneId
        )).isTrue()

        assertThat(event.overlapsWithFullDayRange(
            LocalDate.of(2020, 6, 25),
            LocalDate.of(2020, 6, 25),
            displayTimeZoneId
        )).isTrue()

        assertThat(event.overlapsWithFullDayRange(
            LocalDate.of(2020, 6, 25),
            LocalDate.of(2020, 6, 26),
            displayTimeZoneId
        )).isTrue()

        assertThat(event.overlapsWithFullDayRange(
            LocalDate.of(2020, 6, 25),
            LocalDate.of(2020, 6, 27),
            displayTimeZoneId
        )).isTrue()

        assertThat(event.overlapsWithFullDayRange(
            LocalDate.of(2020, 6, 25),
            LocalDate.of(2020, 6, 28),
            displayTimeZoneId
        )).isTrue()

        assertThat(event.overlapsWithFullDayRange(
            LocalDate.of(2020, 6, 28),
            LocalDate.of(2020, 6, 28),
            displayTimeZoneId
        )).isFalse()

    }

    @Test
    fun `generate occurrences of part-day event within full-day range`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    DTSTART;TZID=/Europe/Budapest:20200625T160000
    DTEND;TZID=/Europe/Budapest:20200625T163000
    RRULE:FREQ=DAILY;COUNT=20
    SUMMARY:recurring every day 20 times
    UID:EGVy407XddW2_ESpoOVn7oN1qc9V@proton.me
    DTSTAMP:20200625T143822Z
    BEGIN:VALARM
    TRIGGER:-PT15H
    ACTION:DISPLAY
    END:VALARM
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val iCal = ICalUtils.parseICalString(iCalString)!!
        val displayTimeZoneId = "Europe/Vilnius"
        val event = Event("id", me.proton.android.calendar.domain.model.Calendar("id", "calendar", ""), iCal, null)

        val displayRangeFrom = LocalDate.of(2020, 7, 1)
        val displayRangeTo = LocalDate.of(2020, 7, 1)

        val occurrences = event.generateOccurrencesInFullDayRange(displayRangeFrom, displayRangeTo, displayTimeZoneId)

        assertThat(occurrences!!.size).isEqualTo(1)
        assertThat(occurrences.first().occurrenceNumber).isEqualTo(7)
        assertThat(occurrences.first().startDateTime).isEqualTo(ZonedDateTime.of(2020, 7, 1, 17, 0, 0, 0, ZoneId.of(displayTimeZoneId)))
        assertThat(occurrences.first().endDateTime).isEqualTo(ZonedDateTime.of(2020, 7, 1, 17, 30, 0, 0, ZoneId.of(displayTimeZoneId)))

    }

    @Test
    fun `generate occurrences of full-day event within full-day range`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    DTSTART;VALUE=DATE:20200626
    DTEND;VALUE=DATE:20200627
    RRULE:FREQ=DAILY;COUNT=20
    SUMMARY:recurring every day 20 times
    UID:EGVy407XddW2_ESpoOVn7oN1qc9V@proton.me
    DTSTAMP:20200625T143822Z
    BEGIN:VALARM
    TRIGGER:-PT15H
    ACTION:DISPLAY
    END:VALARM
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val iCal = ICalUtils.parseICalString(iCalString)!!
        val displayTimeZoneId = "Europe/Vilnius"
        val event = Event("id", me.proton.android.calendar.domain.model.Calendar("id", "calendar", ""), iCal, null)

        val displayRangeFrom = LocalDate.of(2020, 7, 1)
        val displayRangeTo = LocalDate.of(2020, 7, 1)

        val occurrences = event.generateOccurrencesInFullDayRange(displayRangeFrom, displayRangeTo, displayTimeZoneId)

        assertThat(occurrences!!.size).isEqualTo(1)
        assertThat(occurrences.first().occurrenceNumber).isEqualTo(6)
        assertThat(occurrences.first().startDateTime).isEqualTo(ZonedDateTime.of(2020, 7, 1, 0, 0, 0, 0, ZoneId.of(displayTimeZoneId)))
        assertThat(occurrences.first().endDateTime).isEqualTo(ZonedDateTime.of(2020, 7, 2, 0, 0, 0, 0, ZoneId.of(displayTimeZoneId)))

    }

    @Test
    fun `generate occurrences of part-day event with EXDATES`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    DTSTART;TZID=/Europe/Budapest:20200625T160000
    DTEND;TZID=/Europe/Budapest:20200625T163000
    RRULE:FREQ=DAILY;COUNT=20
    SUMMARY:recurring every day 20 times
    UID:EGVy407XddW2_ESpoOVn7oN1qc9V@proton.me
    DTSTAMP:20200625T143822Z
    BEGIN:VALARM
    TRIGGER:-PT15H
    ACTION:DISPLAY
    END:VALARM
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val iCal = ICalUtils.parseICalString(iCalString)!!
        val displayTimeZoneId = "Europe/Vilnius"
        val event = Event("id", me.proton.android.calendar.domain.model.Calendar("id", "calendar", ""), iCal, null)

        val displayRangeTo = LocalDate.of(2020, 7, 30)

        event.addExceptionDate(2)
        event.addExceptionDate(3)
        event.addExceptionDate(5)
        event.addExceptionDate(10)
        event.addExceptionDate(40) // non-existing occurrence

        val occurrences = event.generateFilteredOccurrencesUntil(displayRangeTo, displayTimeZoneId) ?: emptyList()

        assertThat(occurrences.size).isEqualTo(16)
        assertThat(occurrences.none { it.occurrenceNumber == 2 }).isTrue()
        assertThat(occurrences.none { it.occurrenceNumber == 3 }).isTrue()
        assertThat(occurrences.none { it.occurrenceNumber == 5 }).isTrue()
        assertThat(occurrences.none { it.occurrenceNumber == 10 }).isTrue()
        assertThat(occurrences.all { it.startDateTime.zone.id == displayTimeZoneId }).isTrue()

    }

    @Test
    fun `generate occurrences of all-day event with EXDATES`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    DTSTART;VALUE=DATE:20200626
    DTEND;VALUE=DATE:20200627
    RRULE:FREQ=DAILY;COUNT=20
    SUMMARY:recurring every day 20 times
    UID:EGVy407XddW2_ESpoOVn7oN1qc9V@proton.me
    DTSTAMP:20200625T143822Z
    BEGIN:VALARM
    TRIGGER:-PT15H
    ACTION:DISPLAY
    END:VALARM
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val iCal = ICalUtils.parseICalString(iCalString)!!
        val displayTimeZoneId = "Europe/Vilnius"
        val event = Event("id", me.proton.android.calendar.domain.model.Calendar("id", "calendar", ""), iCal, null)

        val displayRangeTo = LocalDate.of(2020, 7, 30)

        event.addExceptionDate(2)
        event.addExceptionDate(3)
        event.addExceptionDate(5)
        event.addExceptionDate(10)
        event.addExceptionDate(40) // non-existing occurrence

        val occurrences = event.generateFilteredOccurrencesUntil(displayRangeTo, displayTimeZoneId) ?: emptyList() //event.filterOutOccurrences(event.generateOccurrencesUntil(displayRangeTo, ZoneId.systemDefault().id) ?: emptyList())

        assertThat(occurrences.size).isEqualTo(16)
        assertThat(occurrences.none { it.occurrenceNumber == 2 }).isTrue()
        assertThat(occurrences.none { it.occurrenceNumber == 3 }).isTrue()
        assertThat(occurrences.none { it.occurrenceNumber == 5 }).isTrue()
        assertThat(occurrences.none { it.occurrenceNumber == 10 }).isTrue()
        assertThat(occurrences.all { it.startDateTime.zone.id == displayTimeZoneId }).isTrue()

    }

    @Test
    fun `generate n-th occurrence of full-day event`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    DTSTART;VALUE=DATE:20200626
    DTEND;VALUE=DATE:20200627
    RRULE:FREQ=DAILY;COUNT=20
    SUMMARY:recurring every day 20 times
    UID:EGVy407XddW2_ESpoOVn7oN1qc9V@proton.me
    DTSTAMP:20200625T143822Z
    BEGIN:VALARM
    TRIGGER:-PT15H
    ACTION:DISPLAY
    END:VALARM
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val iCal = ICalUtils.parseICalString(iCalString)!!
        val displayTimeZoneId = "Europe/Vilnius"
        val event = Event("id", me.proton.android.calendar.domain.model.Calendar("id", "calendar", ""), iCal, null)

        val occurrence1 = event.generateOccurrence(1, displayTimeZoneId)
        val occurrence2 = event.generateOccurrence(2, displayTimeZoneId)
        val occurrence5 = event.generateOccurrence(5, displayTimeZoneId)
        val occurrence20 = event.generateOccurrence(20, displayTimeZoneId)
        val occurrence21 = event.generateOccurrence(21, displayTimeZoneId)

        assertThat(occurrence1).isEqualTo(Event.Occurrence(
            ZonedDateTime.of(2020, 6, 26, 0, 0, 0, 0, ZoneId.of(displayTimeZoneId)),
            ZonedDateTime.of(2020, 6, 27, 0, 0, 0, 0, ZoneId.of(displayTimeZoneId)),
            1))

        assertThat(occurrence2).isEqualTo(Event.Occurrence(
            ZonedDateTime.of(2020, 6, 27, 0, 0, 0, 0, ZoneId.of(displayTimeZoneId)),
            ZonedDateTime.of(2020, 6, 28, 0, 0, 0, 0, ZoneId.of(displayTimeZoneId)),
            2))

        assertThat(occurrence5).isEqualTo(Event.Occurrence(
            ZonedDateTime.of(2020, 6, 30, 0, 0, 0, 0, ZoneId.of(displayTimeZoneId)),
            ZonedDateTime.of(2020, 7, 1, 0, 0, 0, 0, ZoneId.of(displayTimeZoneId)),
            5))

        assertThat(occurrence20).isEqualTo(Event.Occurrence(
            ZonedDateTime.of(2020, 7, 15, 0, 0, 0, 0, ZoneId.of(displayTimeZoneId)),
            ZonedDateTime.of(2020, 7, 16, 0, 0, 0, 0, ZoneId.of(displayTimeZoneId)),
            20))

        assertThat(occurrence21).isNull()

    }

    @Test
    fun `handle EXDATEs in partial-day event`() {

        val iCalEvent = ICalUtils.createNewEvent()
        iCalEvent.setStart(LocalDate.of(2020, 1, 20), LocalTime.of(10, 0), "Europe/Vilnius")
        iCalEvent.setEnd(LocalDate.of(2020, 1, 20), LocalTime.of(11, 0), "Europe/Vilnius")
        iCalEvent.setRecurrenceRule(Recurrence.Builder(Frequency.DAILY).interval(1).count(10).build())

        val calendar = iCalEvent.wrapInICalendar()
        calendar.setStartTimeZone("Europe/Vilnius")
        calendar.setEndTimeZone("Europe/Vilnius")

        val event = Event("id", me.proton.android.calendar.domain.model.Calendar("id", "calendar", ""), calendar, null)

        event.addExceptionDate(1)
        event.addExceptionDate(3)
        event.addExceptionDate(10)

        val exceptionDates = event.getExceptionDates()!!

        assertThat(exceptionDates.size).isEqualTo(3)
        assertThat(exceptionDates[0]).isEqualTo(ZonedDateTime.of(2020, 1, 20, 10, 0, 0, 0, ZoneId.of("Europe/Vilnius")))
        assertThat(exceptionDates[1]).isEqualTo(ZonedDateTime.of(2020, 1, 22, 10, 0, 0, 0, ZoneId.of("Europe/Vilnius")))
        assertThat(exceptionDates[2]).isEqualTo(ZonedDateTime.of(2020, 1, 29, 10, 0, 0, 0, ZoneId.of("Europe/Vilnius")))

    }

    @Test
    fun `handle EXDATEs in full-day event`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    DTSTART;VALUE=DATE:20200626
    DTEND;VALUE=DATE:20200627
    RRULE:FREQ=DAILY;COUNT=20
    SUMMARY:recurring every day 20 times
    UID:EGVy407XddW2_ESpoOVn7oN1qc9V@proton.me
    DTSTAMP:20200625T143822Z
    BEGIN:VALARM
    TRIGGER:-PT15H
    ACTION:DISPLAY
    END:VALARM
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val iCal = ICalUtils.parseICalString(iCalString)!!
        val event = Event("id", me.proton.android.calendar.domain.model.Calendar("id", "calendar", ""), iCal, null)

        event.addExceptionDate(1)
        event.addExceptionDate(3)
        event.addExceptionDate(10)

        val exceptionDates = event.getExceptionDates()!!

        TestsLogger.d("excepption dates: $exceptionDates")
        TestsLogger.d("cal=${event.iCalendar.printToString()}")

        assertThat(exceptionDates.size).isEqualTo(3)
        assertThat(exceptionDates[0].toLocalDate()).isEqualTo(LocalDate.of(2020, 6, 26))
        //assertThat(exceptionDates[1]).isEqualTo(ZonedDateTime.of(2020, 6, 28, 0, 0, 0, 0, ZoneId.of("Europe/Vilnius")))
        //assertThat(exceptionDates[2]).isEqualTo(ZonedDateTime.of(2020, 7, 5, 0, 0, 0, 0, ZoneId.of("Europe/Vilnius")))

    }

    @Test
    fun `filter out occurrences based on EXDATEs in partial-day event`() {

        val iCalEvent = ICalUtils.createNewEvent()
        iCalEvent.setStart(LocalDate.of(2020, 1, 20), LocalTime.of(10, 0), "Europe/Vilnius")
        iCalEvent.setEnd(LocalDate.of(2020, 1, 20), LocalTime.of(11, 0), "Europe/Vilnius")
        iCalEvent.setRecurrenceRule(Recurrence.Builder(Frequency.DAILY).interval(1).count(10).build())

        val calendar = iCalEvent.wrapInICalendar()
        calendar.setStartTimeZone("Europe/Vilnius")
        calendar.setEndTimeZone("Europe/Vilnius")

        val event = Event("id", me.proton.android.calendar.domain.model.Calendar("id", "calendar", ""), calendar, null)

        event.addExceptionDate(1)
        event.addExceptionDate(3)
        event.addExceptionDate(10)

        val exceptionDates = event.getExceptionDates()!!

        assertThat(exceptionDates.size).isEqualTo(3)
        assertThat(exceptionDates[0]).isEqualTo(ZonedDateTime.of(2020, 1, 20, 10, 0, 0, 0, ZoneId.of("Europe/Vilnius")))
        assertThat(exceptionDates[1]).isEqualTo(ZonedDateTime.of(2020, 1, 22, 10, 0, 0, 0, ZoneId.of("Europe/Vilnius")))
        assertThat(exceptionDates[2]).isEqualTo(ZonedDateTime.of(2020, 1, 29, 10, 0, 0, 0, ZoneId.of("Europe/Vilnius")))

    }

    @Test
    fun `generate n-th occurrence of partial-day event, display in different timezone`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    DTSTART;TZID=/Europe/Budapest:20200625T100000
    DTEND;TZID=/Europe/Budapest:20200625T103000
    RRULE:FREQ=DAILY;COUNT=20
    SUMMARY:recurring every day 20 times
    UID:EGVy407XddW2_ESpoOVn7oN1qc9V@proton.me
    DTSTAMP:20200625T143822Z
    BEGIN:VALARM
    TRIGGER:-PT15H
    ACTION:DISPLAY
    END:VALARM
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val iCal = ICalUtils.parseICalString(iCalString)!!
        val displayTimeZoneId = "Europe/Vilnius"
        val event = Event("id", me.proton.android.calendar.domain.model.Calendar("id", "calendar", ""), iCal, null)

        val occurrence1 = event.generateOccurrence(1, displayTimeZoneId)
        val occurrence5 = event.generateOccurrence(5, displayTimeZoneId)
        val occurrence21 = event.generateOccurrence(21, displayTimeZoneId)

        assertThat(occurrence1).isEqualTo(Event.Occurrence(
            ZonedDateTime.of(2020, 6, 25, 11, 0, 0, 0, ZoneId.of(displayTimeZoneId)),
            ZonedDateTime.of(2020, 6, 25, 11, 30, 0, 0, ZoneId.of(displayTimeZoneId)),
            1))

        assertThat(occurrence5).isEqualTo(Event.Occurrence(
            ZonedDateTime.of(2020, 6, 29, 11, 0, 0, 0, ZoneId.of(displayTimeZoneId)),
            ZonedDateTime.of(2020, 6, 29, 11, 30, 0, 0, ZoneId.of(displayTimeZoneId)),
            5))

        assertThat(occurrence21).isNull()

    }

    @Test
    fun `correctly sanitise partial-day Event without DTEND`() {

        val event = ICalUtils.createNewEvent()
        event.setStart(LocalDate.of(2020, 1, 20), LocalTime.of(10, 0), "Europe/Zurich")

        assertThat(event.sanitise()).isTrue()

        assertThat(event.dateStart.value.hasTime()).isTrue()
        assertThat(event.dateEnd.value.hasTime()).isTrue()

        assertThat(event.getEnd()!!.toLocalDate()).isEqualTo(LocalDate.of(2020, 1, 20))
        assertThat(event.getEnd()!!.toLocalTime()).isEqualTo(LocalTime.of(10, 0))

    }

    @Test
    fun `correctly sanitise all-day Event without DTEND`() {

        val event = ICalUtils.createNewEvent()
        event.setStart(LocalDate.of(2020, 1, 20))

        assertThat(event.sanitise()).isTrue()

        assertThat(event.dateStart.value).isNotNull()
        assertThat(event.dateEnd.value).isNotNull()

        assertThat(event.getEnd()!!.toLocalDate()).isEqualTo(LocalDate.of(2020, 1, 21))

    }


}
