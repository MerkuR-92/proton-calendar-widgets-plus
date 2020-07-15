package me.proton.android.calendar.common

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.fail
import biweekly.Biweekly
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import timber.log.Timber
import java.time.Instant

internal class ICalTest {

    val iCal = ICalUtils

    @Test
    fun `merge calendars for all-day event`() {

        val calendarParts = listOf(
    """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    UID:proton-calendar-02a20ccd-b335-4e4a-2f10-48bc45503993
    DTSTAMP:20200228T170551Z
    SUMMARY:Event after successful connection between database\, usecase and ap
     i
    END:VEVENT
    END:VCALENDAR""".trimIndent(),
    """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    UID:proton-calendar-02a20ccd-b335-4e4a-2f10-48bc45503993
    DTSTAMP:20200228T170551Z
    DTSTART;VALUE=DATE:20200228
    DTEND;VALUE=DATE:20200229
    END:VEVENT
    END:VCALENDAR""".trimIndent(),
    """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Proton Technologies//AndroidCalendar 1.0//EN
    BEGIN:VEVENT
    UID:proton-calendar-02a20ccd-b335-4e4a-2f10-48bc45503993
    DTSTAMP:20200228T170551Z
    BEGIN:VALARM
    TRIGGER:-PT15H
    ACTION:DISPLAY
    END:VALARM
    BEGIN:VALARM
    TRIGGER:-PT30H
    ACTION:DISPLAY
    END:VALARM
    BEGIN:VALARM
    TRIGGER:-PT45H
    ACTION:DISPLAY
    END:VALARM
    END:VEVENT
    END:VCALENDAR""".trimIndent()
        )

        val mergedCalendar = iCal.mergeCalendarPartsIntoICalendar(calendarParts) ?: fail("calendar merging failed")
        val mergedEvent = mergedCalendar?.events?.first() ?: fail("calendar merging failed")

        assertThat(mergedEvent.dateStart.value.toInstant()).isEqualTo(Instant.parse("2020-02-27T23:00:00Z"))
        assertThat(mergedEvent.summary.value).isEqualTo("Event after successful connection between database, usecase and api")
        assertThat(mergedEvent.alarms.first().action.value).isEqualTo("DISPLAY")
        assertThat(mergedEvent.alarms).hasSize(3)
        assertThat(mergedCalendar.events).hasSize(1)
        assertThat(mergedCalendar.productId.value).isEqualTo("-//Proton Technologies//AndroidCalendar 1.0//EN")

    }

    @Test
    fun `merge calendars preserving timezone info`() { // TODO FIXME add RECURRENCE-ID & EXDATES

        val calendarParts = listOf(
            """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    UID:proton-calendar-5a1e31ee-0ba7-7094-93d8-26dd58ca8b37
    DTSTAMP:20200330T155311Z
    SUMMARY:Start on 31st March 10:00\, end on 1st April 18:00
    END:VEVENT
    END:VCALENDAR""".trimIndent(),
    """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    UID:proton-calendar-5a1e31ee-0ba7-7094-93d8-26dd58ca8b37
    DTSTAMP:20200330T155311Z
    DTSTART;TZID=Europe/Budapest:20200331T100000
    DTEND;TZID=Europe/Budapest:20200401T180000
    END:VEVENT
    END:VCALENDAR""".trimIndent()
        )

        val mergedCalendar = iCal.mergeCalendarPartsIntoICalendar(calendarParts) ?: fail("calendar merging failed")
        val mergedEvent = mergedCalendar?.events?.first() ?: fail("calendar merging failed")

        TestsLogger.d("${mergedCalendar.printToString()}")

        assertThat(mergedCalendar.timezoneInfo.getTimezone(mergedEvent.dateStart).timeZone.id).isEqualTo("Europe/Budapest")
        assertThat(mergedCalendar.timezoneInfo.getTimezone(mergedEvent.dateEnd).timeZone.id).isEqualTo("Europe/Budapest")

    }

    @Test
    fun `properly parse event with empty fields`() {

        val testVCal = """
                BEGIN:VCALENDAR
                PRODID:-//Proton Technologies//ProtonCalendar Beta//EN
                VERSION:2.0
                BEGIN:VEVENT
                DTSTART:20200514T080000Z
                DTEND:20200514T083000Z
                DTSTAMP:20200511T123232Z
                UID:7peg3ed7bodi8shj243ikiaeqi@google.com
                CREATED:20200511T123226Z
                DESCRIPTION:no title
                LAST-MODIFIED:20200511T123226Z
                LOCATION:
                SEQUENCE:0
                STATUS:CONFIRMED
                SUMMARY:
                TRANSP:OPAQUE
                END:VEVENT
                END:VCALENDAR
            """.trimIndent()

        assertThat(ICalUtils.parseICalString(testVCal)).isNotNull()

    }


}
