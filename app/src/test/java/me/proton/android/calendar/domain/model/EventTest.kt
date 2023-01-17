package me.proton.android.calendar.domain.model

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import biweekly.component.VEvent
import biweekly.util.DayOfWeek
import biweekly.util.Frequency
import biweekly.util.Recurrence
import me.proton.android.calendar.common.logger.TestsLogger
import me.proton.android.calendar.common.utils.EventUtilsImpl.generateOccurrence
import me.proton.android.calendar.common.utils.ICalUtilsImpl
import me.proton.android.calendar.common.utils.ICalUtilsImpl.printToString
import me.proton.android.calendar.common.utils.ICalUtilsImpl.setStart
import me.proton.android.calendar.common.utils.ICalUtilsImpl.wrapInICalendar
import org.junit.jupiter.api.Test
import java.sql.Date
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue

internal class EventTest {


    @Test
    fun `empty event sanity check`() {
        val calendarWithEmptyEvent = VEvent().wrapInICalendar()
        val event = Event.from("id", Calendar("id", "name", "email", "color", 1, true, 0, 127, emptyList(), emptyList()), calendarWithEmptyEvent, 0)

        assertNull(event)
    }

    @Test
    fun `is all day event`() {
        val event = Event.from("id", Calendar("id", "name", "email", "color", 1, true, 0, 127, emptyList(), emptyList()), calendarAllDaySingleDay!!, 0)!!
        val eventRecurring = Event.from("id", Calendar("id", "name", "email", "color", 1, true, 0, 127, emptyList(), emptyList()), calendarAllDaySingleDayRecurring!!, 0)!!
        assertTrue(event.isAllDay())
        assertTrue(eventRecurring.isAllDay())
    }

    @Test
    fun `is all day event without DTEND`() {
        val event = Event.from("id", Calendar("id", "name", "email", "color", 1, true, 0, 127, emptyList(), emptyList()), calendarAllDaySingleDayWithoutDTEnd!!, 0)!!
        assertTrue(event.isAllDay())
    }

    @Test
    fun `is not all day event`() {
        val event = Event.from("id", Calendar("id", "name", "email", "color", 1, true, 0, 127, emptyList(), emptyList()), calendarStartEndTimeDifferentDays!!, 0)!!
        assertFalse(event.isAllDay())
    }

    @Test
    fun `all-day event is on single day`() {
        val event = Event.from("id", Calendar("id", "name", "email", "color", 1, true, 0, 127, emptyList(), emptyList()), calendarAllDaySingleDay!!, 0)!!
        val eventRecurring = Event.from("id", Calendar("id", "name", "email", "color", 1, true, 0, 127, emptyList(), emptyList()), calendarAllDaySingleDayRecurring!!, 0)!!
        assertTrue(event.spansSingleDay())
        assertTrue(eventRecurring.spansSingleDay())
    }

    @Test
    fun `all-day event with actual end date is on single day`() {
        val event = Event.from("id", Calendar("id", "name", "email", "color", 1, true, 0, 127, emptyList(), emptyList()), calendarAllDaySingleDayActualEndDate!!, 0)!!
        assertTrue(event.spansSingleDay(actualEndDate = true))
    }

    @Test
    fun `all-day event without DTEND is on single day`() {
        val event = Event.from("id", Calendar("id", "name", "email", "color", 1, true, 0, 127, emptyList(), emptyList()), calendarAllDaySingleDayWithoutDTEnd!!, 0)!!
        assertTrue(event.spansSingleDay())
    }

    @Test
    fun `all-day event spans many days`() {
        val event = Event.from("id", Calendar("id", "name", "email", "color", 1, true, 0, 127, emptyList(), emptyList()), calendarAllDay3Days!!, 0)!!
        assertFalse(event.spansSingleDay())
    }

    @Test
    fun `event with start-end time spans one day`() {
        val event = Event.from("id", Calendar("id", "name", "email", "color", 1, true, 0, 127, emptyList(), emptyList()), calendar1HourSingleDay!!, 0)!!
        assertTrue(event.spansSingleDay())
    }

    @Test
    fun `event with start-end time spans many days`() {
        val event = Event.from("id", Calendar("id", "name", "email", "color", 1, true, 0, 127, emptyList(), emptyList()), calendarStartEndTimeDifferentDays!!, 0)!!
        assertFalse(event.spansSingleDay())
    }

    @Test
    fun `event with start-end time spans many days in different timezones`() {
        val event = Event.from("id", Calendar("id", "name", "email", "color", 1, true, 0, 127, emptyList(), emptyList()), calendarManyDaysIn2MonthsDifferentEndTimezone!!, 0)!!
        assertFalse(event.spansSingleDay())
    }

    @Test
    fun `event with start-end time spans one day in different timezones`() {
        val event = Event.from("id", Calendar("id", "name", "email", "color", 1, true, 0, 127, emptyList(), emptyList()), calendar2HoursSingleDayDifferentEndTimezone!!, 0)!!
        assertTrue(event.spansSingleDay())
    }

    @Test
    fun `event has standard recurrence rule`() {
        val recurrenceRules = listOf(
            null,
            Recurrence.Builder(Frequency.DAILY).build(),
            Recurrence.Builder(Frequency.WEEKLY).build(),
            Recurrence.Builder(Frequency.MONTHLY).build(),
            Recurrence.Builder(Frequency.YEARLY).build()
        )

        recurrenceRules.forEach {
            val vEvent = VEvent().apply {
                setStart(LocalDate.now())
                setRecurrenceRule(it)
            }
            val calendar = vEvent.wrapInICalendar()
            val event = Event.from("id", Calendar("id", "name", "email", "color", 1, true, 0, 127, emptyList(), emptyList()), calendar, 0)!!

            assertThat(event.isCustomRecurring()).isFalse()
        }
    }

    @Test
    fun `event has custom recurrence rule`() {

        val recurrenceRules = listOf(
            Recurrence.Builder(Frequency.DAILY).interval(1).build(), // every 1 day
            Recurrence.Builder(Frequency.DAILY).count(5).build(), // 5 times, implicitly every 1 day
            Recurrence.Builder(Frequency.DAILY).until(Date.valueOf("2020-05-20")).build(), // until 20th May, implicitly every 1 day

            Recurrence.Builder(Frequency.WEEKLY).interval(1).byDay(DayOfWeek.MONDAY).build(), // every 1 week on Monday
            Recurrence.Builder(Frequency.WEEKLY).byDay(DayOfWeek.MONDAY).build(), // implicitly every 1 week, on Monday
            Recurrence.Builder(Frequency.WEEKLY).count(5).build(), // 5 times, implicitly every 1 week
            Recurrence.Builder(Frequency.WEEKLY).until(Date.valueOf("2020-05-20")).build(), // until 20th May, implicitly every 1 week

            Recurrence.Builder(Frequency.MONTHLY).interval(1).byMonthDay(1).build(), // on day 1
            Recurrence.Builder(Frequency.MONTHLY).byMonthDay(1).count(5).build(), // on day 1, 5 times, implicitly every 1 month
            Recurrence.Builder(Frequency.MONTHLY).byMonthDay(1).until(Date.valueOf("2020-05-20")).build(), // on day 1, until 20th May, implicitly every 1 month

            // omitting implicit interval here
            Recurrence.Builder(Frequency.MONTHLY).interval(1).byMonthDay(1).bySetPos(2).byDay(DayOfWeek.MONDAY).build(), // on second Monday
            Recurrence.Builder(Frequency.MONTHLY).interval(1).byMonthDay(1).bySetPos(2).byDay(DayOfWeek.MONDAY).count(5).build(), // on second Monday, 5 times
            Recurrence.Builder(Frequency.MONTHLY).interval(1).byMonthDay(1).bySetPos(2).byDay(DayOfWeek.MONDAY).until(Date.valueOf("2020-05-20")).build(), // on second Monday, until 20th May

            Recurrence.Builder(Frequency.YEARLY).interval(1).build()
            // TODO it looks like server doesn't support those YEARLY rules:
            // FREQ=YEARLY;BYMONTH=1;BYMONTHDAY=1;UNTIL=20200518T220000Z "yearly on JAN 1st"
            // FREQ=YEARLY;BYDAY=SU;BYSETPOS=1;BYMONTH=1;UNTIL=20200518T220000Z "on the first Sunday of Jan"
        )


        recurrenceRules.forEach {
            val vEvent = VEvent().apply {
                setStart(LocalDate.now())
                setRecurrenceRule(it)
            }
            val calendar = vEvent.wrapInICalendar()
            val event = Event.from("id", Calendar("id", "name", "email", "color", 1, true, 0, 127, emptyList(), emptyList()), calendar, 0)!!

            TestsLogger.d(calendar.printToString())

            assertThat(event.isCustomRecurring()).isTrue()
        }
    }

    @Test
    fun `calendar with timezone and event displayed in timezone that makes end date at midnight or on next day`() {
        val event = Event.from("id", Calendar("id", "name", "email", "color", 1, true, 0, 127, emptyList(), emptyList()), calendarTimezoneUtcEndOfDay!!, 0)!!

        // UTC makes event end at 10PM
        var displayTimeZoneId = "UTC"
        assertTrue(event.spansSingleDay(timeZoneId = displayTimeZoneId))

        // Europe/Vilnius makes event end at midnight, so counts as ending on the same day
        displayTimeZoneId = "Europe/Vilnius"
        assertTrue(event.spansSingleDay(timeZoneId = displayTimeZoneId))

        // Europe/Samara makes event start and end on next day
        displayTimeZoneId = "Europe/Samara"
        assertTrue(event.spansSingleDay(timeZoneId = displayTimeZoneId))
    }

    /**
     * Event happens weekly, starting in September at 18:00 in Zurich timezone.
     * We want to display it in Pacific/Auckland, but over the course of following weeks,
     * Auckland will switch to DST, and then Zurich will follow.
     *
     * This means that local time in Auckland will change twice, for those two DST switches.
     *
     * The local time in Zurich always stays the same.
     */
    @Test
    fun `local time for part-time recurring event in another timezone, when both original and display TZ changes due to DST`() {
        val event = Event.from("id", Calendar("id", "name", "email", "color", 1, true, 0, 127, emptyList(), emptyList()), calendarRecurringForCheckingDST!!, 0)!!

        val displayTimeZone = ZoneId.of("Pacific/Auckland")

        // before both DST changes
        with (event.generateOccurrence(1, displayTimeZone.id)!!) {
            assertThat(startDateTime).isEqualTo(
                ZonedDateTime.of(
                    LocalDate.of(2021, 9, 2),
                    LocalTime.of(4, 30),
                    displayTimeZone
                )
            )
        }

        with (event.generateOccurrence(2, displayTimeZone.id)!!) {
            assertThat(startDateTime).isEqualTo(
                ZonedDateTime.of(
                    LocalDate.of(2021, 9, 16),
                    LocalTime.of(4, 30),
                    displayTimeZone
                )
            )
        }

        // after Auckland DST change
        with (event.generateOccurrence(3, displayTimeZone.id)!!) {
            assertThat(startDateTime).isEqualTo(
                ZonedDateTime.of(
                    LocalDate.of(2021, 9, 30),
                    LocalTime.of(5, 30),
                    displayTimeZone
                )
            )
        }

        // after Zurich DST change
        with (event.generateOccurrence(6, displayTimeZone.id)!!) {
            assertThat(startDateTime).isEqualTo(
                ZonedDateTime.of(
                    LocalDate.of(2021, 11, 11),
                    LocalTime.of(6, 30),
                    displayTimeZone
                )
            )
        }

        // now Zurich and Auckland are in DST
        with (event.generateOccurrence(15, displayTimeZone.id)!!) {
            assertThat(startDateTime).isEqualTo(
                ZonedDateTime.of(
                    LocalDate.of(2022, 3, 17),
                    LocalTime.of(6, 30),
                    displayTimeZone
                )
            )
        }

        // after Zurich goes back to Standard
        with (event.generateOccurrence(16, displayTimeZone.id)!!) {
            assertThat(startDateTime).isEqualTo(
                ZonedDateTime.of(
                    LocalDate.of(2022, 3, 31),
                    LocalTime.of(5, 30),
                    displayTimeZone
                )
            )
        }

        // after Auckland goes back to Standard
        with (event.generateOccurrence(17, displayTimeZone.id)!!) {
            assertThat(startDateTime).isEqualTo(
                ZonedDateTime.of(
                    LocalDate.of(2022, 4, 14),
                    LocalTime.of(4, 30),
                    displayTimeZone
                )
            )
        }

    }

    val calendarStartEndTimeDifferentDays = ICalUtilsImpl.parseICalString("""
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Michael Angstadt//biweekly 0.6.3//EN
    BEGIN:VEVENT
    DTSTART:20200331T080000Z
    DTEND:20200401T160000Z
    SUMMARY:Start on 31st March 10:00\, end on 1st April 18:00
    UID:proton-calendar-5a1e31ee-0ba7-7094-93d8-26dd58ca8b37
    DTSTAMP:20200330T155311Z
    BEGIN:VALARM
    TRIGGER:-PT15M
    ACTION:DISPLAY
    END:VALARM
    END:VEVENT
    END:VCALENDAR
    """.trimIndent())

    val calendarAllDaySingleDay = ICalUtilsImpl.parseICalString("""
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Michael Angstadt//biweekly 0.6.3//EN
    BEGIN:VEVENT
    DTSTART;VALUE=DATE:20200402
    DTEND;VALUE=DATE:20200403
    SUMMARY:All-day event on 2nd April
    UID:proton-calendar-11667b13-2041-adde-3bc8-34952f4c0578
    DTSTAMP:20200330T155327Z
    BEGIN:VALARM
    TRIGGER:-PT15H
    ACTION:DISPLAY
    END:VALARM
    END:VEVENT
    END:VCALENDAR
    """.trimIndent())

    val calendarAllDaySingleDayActualEndDate = ICalUtilsImpl.parseICalString("""
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Michael Angstadt//biweekly 0.6.3//EN
    BEGIN:VEVENT
    DTSTART;VALUE=DATE:20200402
    DTEND;VALUE=DATE:20200402
    SUMMARY:All-day event on 2nd April, actual end date
    UID:proton-calendar-11667b13-2041-adde-3bc8-34952f4c0578
    DTSTAMP:20200330T155327Z
    BEGIN:VALARM
    TRIGGER:-PT15H
    ACTION:DISPLAY
    END:VALARM
    END:VEVENT
    END:VCALENDAR
    """.trimIndent())

    val calendarAllDaySingleDayRecurring = ICalUtilsImpl.parseICalString("""
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    DTSTART;VALUE=DATE:20200708
    RRULE:FREQ=DAILY
    SUMMARY:all-day recurring
    UID:0E5ZIn1likmkmo7BIkX8vaGswv1D@proton.me
    DTSTAMP:20200709T085114Z
    DTEND;VALUE=DATE:20200709
    BEGIN:VALARM
    TRIGGER:-PT15H
    ACTION:DISPLAY
    END:VALARM
    END:VEVENT
    END:VCALENDAR
    """.trimIndent())

    val calendarAllDaySingleDayWithoutDTEnd = ICalUtilsImpl.parseICalString("""
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Michael Angstadt//biweekly 0.6.3//EN
    BEGIN:VEVENT
    DTSTART;VALUE=DATE:20200402
    SUMMARY:All-day event on 2nd April
    UID:proton-calendar-11667b13-2041-adde-3bc8-34952f4c0578
    DTSTAMP:20200330T155327Z
    BEGIN:VALARM
    TRIGGER:-PT15H
    ACTION:DISPLAY
    END:VALARM
    END:VEVENT
    END:VCALENDAR
    """.trimIndent())

    val calendar1HourSingleDay = ICalUtilsImpl.parseICalString("""
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Michael Angstadt//biweekly 0.6.3//EN
    BEGIN:VEVENT
    DTSTART:20200403T093000Z
    DTEND:20200403T103000Z
    SUMMARY:1 hour event on 3rd April\, 11:30-12:30
    UID:proton-calendar-b52354c7-7643-6d39-faa7-687a1bf0150b
    DTSTAMP:20200330T155404Z
    BEGIN:VALARM
    TRIGGER:-PT15M
    ACTION:DISPLAY
    END:VALARM
    END:VEVENT
    END:VCALENDAR
    """.trimIndent())

    val calendarAllDay3Days = ICalUtilsImpl.parseICalString("""
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Michael Angstadt//biweekly 0.6.3//EN
    BEGIN:VEVENT
    DTSTART;VALUE=DATE:20200328
    DTEND;VALUE=DATE:20200331
    SUMMARY:3-day-all-day event spanning 2 full weeks\, from Sat to Mon
    UID:proton-calendar-39dffda4-8213-a59e-0bba-72b204da166a
    DTSTAMP:20200330T155505Z
    BEGIN:VALARM
    TRIGGER:-PT15H
    ACTION:DISPLAY
    END:VALARM
    END:VEVENT
    END:VCALENDAR
    """.trimIndent())

    val calendar2HoursSingleDayDifferentEndTimezone = ICalUtilsImpl.parseICalString("""
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Michael Angstadt//biweekly 0.6.3//EN
    BEGIN:VEVENT
    DTSTART;TZID=Europe/Budapest:20200403T100000
    DTEND;TZID=Europe/Vilnius:20200403T130000
    SUMMARY:Custom timezones: Apr 3\, 10:00 GMT+2 until 13:00 GMT+3
    DTSTAMP:20200331T153912Z
    UID:proton-calendar-58afaf9c-51e9-72d4-5e23-44170a9a4cad
    BEGIN:VALARM
    TRIGGER:-PT15M
    ACTION:DISPLAY
    END:VALARM
    END:VEVENT
    END:VCALENDAR
    """.trimIndent())

    val calendarManyDaysIn2MonthsDifferentEndTimezone = ICalUtilsImpl.parseICalString("""
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Michael Angstadt//biweekly 0.6.3//EN
    BEGIN:VEVENT
    DTSTART;TZID=Europe/Budapest:20200330T180000
    DTEND;TZID=America/Belize:20200403T180000
    SUMMARY:Custom timezones: Mar 30\, 18:00 (GMT+2) until April 3\, 18:00 (GMT
     -6)
    UID:proton-calendar-5e48d839-f2e5-9832-33a1-549c109bba61
    DTSTAMP:20200331T153546Z
    BEGIN:VALARM
    TRIGGER:-PT15M
    ACTION:DISPLAY
    END:VALARM
    END:VEVENT
    END:VCALENDAR
    """.trimIndent())

    val calendarTimezoneUtcEndOfDay = ICalUtilsImpl.parseICalString("""
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VTIMEZONE
    TZID:UTC
    END:VTIMEZONE
    BEGIN:VEVENT
    DTSTART;TZID=UTC:20201110T210000
    DTEND;TZID=UTC:20201110T220000
    SEQUENCE:1
    SUMMARY:Event appear as all day
    UID:DF6OBi2q7A7KV5qbO70j7uKRd0KJ@proton.me
    DTSTAMP:20201110T092019Z
    STATUS:CONFIRMED
    END:VEVENT
    END:VCALENDAR
    """.trimIndent())

    val calendarRecurringForCheckingDST = ICalUtilsImpl.parseICalString("""
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Proton Technologies//AndroidCalendar 0.28.0//EN
    BEGIN:VEVENT
    DTSTAMP:20211109T082541Z
    SEQUENCE:0
    SUMMARY:DST
    LOCATION:
    STATUS:CONFIRMED
    TRANSP:OPAQUE
    UID:30cp82qrfm0ukl22p6mj9p1cfv@google.com
    CREATED:20211109T082523Z
    LAST-MODIFIED:20211109T082540Z
    DTSTART;TZID=Europe/Zurich:20210901T183000
    DTEND;TZID=Europe/Zurich:20210901T193000
    RRULE:FREQ=WEEKLY;INTERVAL=2;BYDAY=WE;WKST=MO
    END:VEVENT
    END:VCALENDAR
    """.trimIndent())
}
