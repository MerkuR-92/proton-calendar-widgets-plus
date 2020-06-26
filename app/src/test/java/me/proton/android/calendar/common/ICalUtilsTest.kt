package me.proton.android.calendar.common

import assertk.assertThat
import assertk.assertions.*
import me.proton.android.calendar.domain.model.Event
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
        val displayTimeZoneId = "Europe/Vilnius"

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
        val displayTimeZoneId = "Europe/Vilnius"

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

        TestsLogger.d("start = ${event.getStart(displayTimeZoneId)}")
        TestsLogger.d("end = ${event.getEnd(displayTimeZoneId)}")

        val displayRangeFrom = LocalDate.of(2020, 7, 1)
        val displayRangeTo = LocalDate.of(2020, 7, 1)

        val occurrences = event.occurrencesInFullDayRange(displayRangeFrom, displayRangeTo, displayTimeZoneId)

        assertThat(occurrences!!.size).isEqualTo(1)
        assertThat(occurrences.first().occurrenceNumber).isEqualTo(7)

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

        TestsLogger.d("start = ${event.getStart(displayTimeZoneId)}")
        TestsLogger.d("end = ${event.getEnd(displayTimeZoneId)}")

        val displayRangeFrom = LocalDate.of(2020, 7, 1)
        val displayRangeTo = LocalDate.of(2020, 7, 1)

        val occurrences = event.occurrencesInFullDayRange(displayRangeFrom, displayRangeTo, displayTimeZoneId)

        assertThat(occurrences!!.size).isEqualTo(1)
        assertThat(occurrences.first().occurrenceNumber).isEqualTo(6)

    }


}
