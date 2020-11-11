package me.proton.android.calendar.common

import assertk.assertThat
import assertk.assertions.*
import biweekly.property.RecurrenceRule
import biweekly.util.*
import me.proton.android.calendar.common.ICalUtils.adjustRRuleToStartDate
import me.proton.android.calendar.common.ICalUtils.adjustToWeekStart
import me.proton.android.calendar.common.ICalUtils.clone
import me.proton.android.calendar.common.ICalUtils.createNewEvent
import me.proton.android.calendar.common.ICalUtils.eventStartZonedDateTimeToDate
import me.proton.android.calendar.common.ICalUtils.filterOutOccurrencesByExdates
import me.proton.android.calendar.common.ICalUtils.isDateTimeTheSame
import me.proton.android.calendar.common.ICalUtils.sanitise
import me.proton.android.calendar.domain.model.Event
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*


internal class ICalUtilsTest {

    @Test
    fun `is date-time-timezone the same between two ICalendars`() {

        val iCalendar1 = ICalUtils.createNewEvent().apply {
            setDateStart(Date.from(ZonedDateTime.of(2020, 1, 10, 12, 0, 0, 0, ZoneId.of("Europe/Vilnius")).toInstant()), true)
            setDateEnd(Date.from(ZonedDateTime.of(2020, 1, 10, 12, 0, 0, 0, ZoneId.of("Europe/Vilnius")).toInstant()), true)
        }.wrapInICalendar()
        iCalendar1.setStartTimeZone("Europe/Vilnius")
        iCalendar1.setEndTimeZone("Europe/Vilnius")

        val iCalendar2 = ICalUtils.createNewEvent().apply {
            setDateStart(Date.from(ZonedDateTime.of(2020, 1, 10, 12, 0, 0, 0, ZoneId.of("Europe/Vilnius")).toInstant()), true)
            setDateEnd(Date.from(ZonedDateTime.of(2020, 1, 10, 13, 0, 0, 0, ZoneId.of("Europe/Vilnius")).toInstant()), true)
        }.wrapInICalendar()
        iCalendar2.setStartTimeZone("Europe/Vilnius")
        iCalendar2.setEndTimeZone("Europe/Vilnius")

        val iCalendar3 = ICalUtils.createNewEvent().apply {
            setDateStart(Date.from(ZonedDateTime.of(2020, 1, 10, 12, 0, 0, 0, ZoneId.of("Europe/Zurich")).toInstant()), true)
            setDateEnd(Date.from(ZonedDateTime.of(2020, 1, 10, 13, 0, 0, 0, ZoneId.of("Europe/Zurich")).toInstant()), true)
        }.wrapInICalendar()
        iCalendar3.setStartTimeZone("Europe/Zurich")
        iCalendar3.setEndTimeZone("Europe/Zurich")

        val iCalendar4 = ICalUtils.createNewEvent().apply {
            setDateStart(Date.from(ZonedDateTime.of(2020, 1, 10, 13, 0, 0, 0, ZoneId.of("Europe/Vilnius")).toInstant()), true)
            setDateEnd(Date.from(ZonedDateTime.of(2020, 1, 10, 14, 0, 0, 0, ZoneId.of("Europe/Vilnius")).toInstant()), true)
        }.wrapInICalendar()
        iCalendar4.setStartTimeZone("Europe/Vilnius")
        iCalendar4.setEndTimeZone("Europe/Vilnius")

        val iCalendar5 = ICalUtils.createNewEvent().apply {
            setDateStart(Date.from(ZonedDateTime.of(2020, 1, 10, 12, 0, 0, 0, ZoneId.of("Europe/Zurich")).toInstant()), true)
            setDateEnd(Date.from(ZonedDateTime.of(2020, 1, 10, 13, 0, 0, 0, ZoneId.of("Europe/Zurich")).toInstant()), true)
        }.wrapInICalendar()
        iCalendar5.setStartTimeZone("Europe/Vilnius")
        iCalendar5.setEndTimeZone("Europe/Vilnius")

        assertThat(iCalendar1.isDateTimeTheSame(iCalendar2)).isFalse()
        assertThat(iCalendar2.isDateTimeTheSame(iCalendar3)).isFalse()
        assertThat(iCalendar3.isDateTimeTheSame(iCalendar4)).isFalse()
        assertThat(iCalendar4.isDateTimeTheSame(iCalendar5)).isTrue()
        assertThat(iCalendar5.isDateTimeTheSame(iCalendar5)).isTrue()
        assertThat(iCalendar5.isDateTimeTheSame(null)).isFalse()

        val iCalendar6 = ICalUtils.createNewEvent().apply {
            setDateStart(Date.from(LocalDate.of(2020, 1, 10).atStartOfDay(ZoneId.of("Europe/Vilnius")).toInstant()), false)
            setDateEnd(Date.from(LocalDate.of(2020, 1, 11).atStartOfDay(ZoneId.of("Europe/Vilnius")).toInstant()), false)
        }.wrapInICalendar()
        val iCalendar7 = ICalUtils.createNewEvent().apply {
            setDateStart(Date.from(LocalDate.of(2020, 1, 10).atStartOfDay(ZoneId.of("Europe/Vilnius")).toInstant()), false)
            setDateEnd(Date.from(LocalDate.of(2020, 1, 11).atStartOfDay(ZoneId.of("Europe/Vilnius")).toInstant()), false)
        }.wrapInICalendar()
        val iCalendar8 = ICalUtils.createNewEvent().apply {
            setDateStart(Date.from(LocalDate.of(2020, 1, 10).atStartOfDay(ZoneId.of("Europe/Vilnius")).toInstant()), false)
            setDateEnd(Date.from(LocalDate.of(2020, 1, 12).atStartOfDay(ZoneId.of("Europe/Vilnius")).toInstant()), false)
        }.wrapInICalendar()

        assertThat(iCalendar6.isDateTimeTheSame(iCalendar7)).isTrue()
        assertThat(iCalendar7.isDateTimeTheSame(iCalendar8)).isFalse()
        assertThat(iCalendar7.isDateTimeTheSame(null)).isFalse()

    }

    // TODO check out how we can fallback for timeznes that are not handled on the device
    //  this test fails on the CI which means it will fail on random devices as well
    @Disabled
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
    fun `set only time of event's start and end`() {

        val event = ICalUtils.createNewEvent()
        event.setStart(LocalDate.of(2020, 1, 20), LocalTime.of(10, 0), "Europe/Vilnius")
        event.setEnd(LocalDate.of(2020, 2, 20), LocalTime.of(11, 0), "Europe/Vilnius")

        event.setStart(LocalTime.of(15, 10, 40), "Europe/Vilnius")
        event.setEnd(LocalTime.of(16, 20, 50), "Europe/Vilnius")

        assertThat(event.dateStart.value.hasTime()).isTrue()
        assertThat(event.dateEnd.value.hasTime()).isTrue()

        assertThat(event.getStart("Europe/Vilnius")!!.toLocalDate()).isEqualTo(LocalDate.of(2020, 1, 20))
        assertThat(event.getEnd("Europe/Vilnius")!!.toLocalDate()).isEqualTo(LocalDate.of(2020, 2, 20))

        assertThat(event.getStart("Europe/Vilnius")!!.toLocalTime()).isEqualTo(LocalTime.of(15, 10, 40))
        assertThat(event.getEnd("Europe/Vilnius")!!.toLocalTime()).isEqualTo(LocalTime.of(16, 20, 50))

    }

    @Test
    fun `adjust WEEKLY RRULE to start date`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Proton Technologies//AndroidCalendar 0.2.3//EN
    BEGIN:VEVENT
    DTSTART;TZID=Europe/Zurich:20200728T130000
    DTEND;TZID=Europe/Zurich:20200728T133000
    RRULE:FREQ=WEEKLY;BYDAY=TU,WE,TH,FR
    SEQUENCE:0
    SUMMARY:weekly on Tu, We, Th, Fr
    STATUS:CONFIRMED
    DTSTAMP:20200728T103442Z
    UID:TaDoa1gh-CVheCqJbaIc86Up96cX@proton.me
    BEGIN:VALARM
    ACTION:DISPLAY
    TRIGGER;RELATED=START:-PT15M
    END:VALARM
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val iCal = ICalUtils.parseICalString(iCalString)!!
        val displayTimeZoneId = "Europe/Vilnius"
        val event = Event("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "",
            true,
            true
        ), iCal, null)

        var oldStartDate = event.iCalEvent.getStart(displayTimeZoneId)
        event.iCalEvent.setStart(LocalDate.of(2020, 7, 30), LocalTime.of(13, 0), "Europe/Zurich")
        event.iCalendar.setStartTimeZone("Europe/Zurich")
        event.iCalendar.adjustRRuleToStartDate(oldStartDate)
        assertThat(event.iCalEvent.recurrenceRule.value.byDay.size).isEqualTo(3)
        assertThat(event.iCalEvent.recurrenceRule.value.byDay.contains(ByDay(DayOfWeek.FRIDAY))).isTrue()

        oldStartDate = event.iCalEvent.getStart(displayTimeZoneId)
        event.iCalEvent.setStart(LocalDate.of(2020, 7, 27), LocalTime.of(13, 0), "Europe/Zurich")
        event.iCalendar.setStartTimeZone("Europe/Zurich")
        event.iCalendar.adjustRRuleToStartDate(oldStartDate)
        assertThat(event.iCalEvent.recurrenceRule.value.byDay.size).isEqualTo(3)
        assertThat(event.iCalEvent.recurrenceRule.value.byDay.contains(ByDay(DayOfWeek.MONDAY))).isTrue()

    }

    @Test
    fun `adjust MONTHLY RRULE to start date`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Proton Technologies//AndroidCalendar 0.2.3//EN
    BEGIN:VEVENT
    DTSTART;TZID=Europe/Zurich:20200728T130000
    DTEND;TZID=Europe/Zurich:20200728T133000
    RRULE:FREQ=MONTHLY;BYDAY=TU;BYSETPOS=-1
    SEQUENCE:0
    SUMMARY:monthly on last Tuesday
    STATUS:CONFIRMED
    DTSTAMP:20200728T103442Z
    UID:TaDoa1gh-CVheCqJbaIc86Up96cX@proton.me
    BEGIN:VALARM
    ACTION:DISPLAY
    TRIGGER;RELATED=START:-PT15M
    END:VALARM
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val iCal = ICalUtils.parseICalString(iCalString)!!
        val event = Event("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "",
            true,
            true
        ), iCal, null)

        // original event should occur on 4th Tuesday every month

        // last Wednesday -- 2020-07-29
        event.iCalEvent.setStart(LocalDate.of(2020, 7, 29), LocalTime.of(13, 0), "Europe/Zurich")
        event.iCalendar.setStartTimeZone("Europe/Zurich")
        event.iCalendar.adjustRRuleToStartDate()
        assertThat(event.iCalEvent.recurrenceRule.value.byDay.size).isEqualTo(1)
        assertThat(event.iCalEvent.recurrenceRule.value.byDay.contains(ByDay(DayOfWeek.WEDNESDAY))).isTrue()
        assertThat(event.iCalEvent.recurrenceRule.value.bySetPos[0]).isEqualTo(-1)

        // 1st Wednesday -- 2020-07-01
        event.iCalEvent.setStart(LocalDate.of(2020, 7, 1), LocalTime.of(13, 0), "Europe/Zurich")
        event.iCalendar.setStartTimeZone("Europe/Zurich")
        event.iCalendar.adjustRRuleToStartDate()
        assertThat(event.iCalEvent.recurrenceRule.value.byDay.size).isEqualTo(1)
        assertThat(event.iCalEvent.recurrenceRule.value.byDay.contains(ByDay(DayOfWeek.WEDNESDAY))).isTrue()
        assertThat(event.iCalEvent.recurrenceRule.value.bySetPos[0]).isEqualTo(1)

        // 2nd Friday -- 2020-07-10
        event.iCalEvent.setStart(LocalDate.of(2020, 7, 10), LocalTime.of(13, 0), "Europe/Zurich")
        event.iCalendar.setStartTimeZone("Europe/Zurich")
        event.iCalendar.adjustRRuleToStartDate()
        assertThat(event.iCalEvent.recurrenceRule.value.byDay.size).isEqualTo(1)
        assertThat(event.iCalEvent.recurrenceRule.value.byDay.contains(ByDay(DayOfWeek.FRIDAY))).isTrue()
        assertThat(event.iCalEvent.recurrenceRule.value.bySetPos[0]).isEqualTo(2)

        // fifth Wednesday -- 2020-07-29
        event.iCalEvent.setStart(LocalDate.of(2020, 7, 29), LocalTime.of(13, 0), "Europe/Zurich")
        event.iCalendar.setStartTimeZone("Europe/Zurich")
        event.iCalendar.adjustRRuleToStartDate()
        assertThat(event.iCalEvent.recurrenceRule.value.byDay.size).isEqualTo(1)
        assertThat(event.iCalEvent.recurrenceRule.value.byDay.contains(ByDay(DayOfWeek.WEDNESDAY))).isTrue()
        assertThat(event.iCalEvent.recurrenceRule.value.bySetPos[0]).isEqualTo(5)

    }

    @Test
    fun `adjust UNTIL RRULE to start date for part-day event`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Proton Technologies//AndroidCalendar 0.2.3//EN
    BEGIN:VEVENT
    DTSTART;TZID=Europe/Zurich:20200728T130000
    DTEND;TZID=Europe/Zurich:20200728T133000
    RRULE:FREQ=DAILY;UNTIL=20200711
    SEQUENCE:0
    SUMMARY:blah
    STATUS:CONFIRMED
    DTSTAMP:20200728T103442Z
    UID:TaDoa1gh-CVheCqJbaIc86Up96cX@proton.me
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val iCal = ICalUtils.parseICalString(iCalString)!!
        val displayTimeZoneId = "Europe/Vilnius"
        val event = Event("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "",
            true,
            true
        ), iCal, null)

        event.iCalendar.adjustRRuleToStartDate()
        assertThat(event.iCalEvent.recurrenceRule.value.until.hasTime()).isTrue()
        assertThat(event.iCalEvent.recurrenceRule.value.until).isEqualTo(ICalDate.from(ZonedDateTime.of(2020, 7, 28, 21, 59, 59, 0, ZoneId.of("UTC")).toInstant()))

    }

    @Test
    fun `adjust UNTIL RRULE to start date for all-day event`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Proton Technologies//AndroidCalendar 0.2.3//EN
    BEGIN:VEVENT
    DTSTART;TZID=Europe/Zurich:20200728
    DTEND;TZID=Europe/Zurich:20200728
    RRULE:FREQ=DAILY;UNTIL=20200711T215959Z
    SEQUENCE:0
    SUMMARY:blah
    STATUS:CONFIRMED
    DTSTAMP:20200728T103442Z
    UID:TaDoa1gh-CVheCqJbaIc86Up96cX@proton.me
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val iCal = ICalUtils.parseICalString(iCalString)!!
        val displayTimeZoneId = "Europe/Vilnius"
        val event = Event("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "",
            true,
            true
        ), iCal, null)

        event.iCalendar.adjustRRuleToStartDate()
        assertThat(event.iCalEvent.recurrenceRule.value.until.hasTime()).isFalse()
        assertThat(event.iCalEvent.recurrenceRule.value.until).isEqualTo(ICalDate.from(LocalDate.of(2020, 7, 28).atStartOfDay(ZoneId.systemDefault()).withZoneSameInstant(
            ZoneId.of("UTC")).toInstant()))

    }

    @Test
    fun `adjust RRULE to WEEK START`() {

        val eventNoAdjustment = createNewEvent()
        eventNoAdjustment.recurrenceRule = RecurrenceRule(Recurrence.Builder(Frequency.WEEKLY).interval(1).byDay(DayOfWeek.FRIDAY).build())

        assertThat(eventNoAdjustment.recurrenceRule.value.workweekStarts).isNull()

        val eventWeekly = createNewEvent()
        eventWeekly.recurrenceRule = RecurrenceRule(Recurrence.Builder(Frequency.WEEKLY).interval(2).byDay(DayOfWeek.FRIDAY).build())
        eventWeekly.recurrenceRule.adjustToWeekStart(java.time.DayOfWeek.SUNDAY)

        TestsLogger.d("${eventWeekly.wrapInICalendar().printToString()}")

        assertThat(eventWeekly.recurrenceRule.value.workweekStarts).isEqualTo(DayOfWeek.SUNDAY)

        val eventYearly = createNewEvent()
        eventYearly.recurrenceRule = RecurrenceRule(Recurrence.Builder(Frequency.YEARLY).byWeekNo(10).build())
        eventYearly.recurrenceRule.adjustToWeekStart(java.time.DayOfWeek.MONDAY)

        TestsLogger.d("${eventYearly.wrapInICalendar().printToString()}")

        assertThat(eventYearly.recurrenceRule.value.workweekStarts).isEqualTo(DayOfWeek.MONDAY)


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
        event.setEnd(LocalDate.of(2020, 1, 22), LocalTime.of(2, 0), timeZoneId)

        val untilDate = Date.from(ZonedDateTime.of(LocalDate.of(2020, 1, 25), LocalTime.of(23, 59, 59), ZoneId.of("UTC")).withZoneSameInstant(
            ZoneId.of(timeZoneId)).toInstant())
        event.recurrenceRule = RecurrenceRule(Recurrence.Builder(Frequency.DAILY).until(untilDate).build())

        val calendar = event.wrapInICalendar()
        calendar.setStartTimeZone(timeZoneId)
        calendar.setEndTimeZone(timeZoneId)

        calendar.adjustOutgoingAllDayEvent(timeZoneId)

        assertThat(calendar.events.first().dateStart.value.hasTime()).isFalse()
        assertThat(calendar.events.first().dateEnd.value.hasTime()).isFalse()

        assertThat(calendar.timezoneInfo.timezones).isEmpty()
        assertThat(calendar.timezoneInfo.getTimezone(event.dateStart)).isNull()
        assertThat(calendar.timezoneInfo.getTimezone(event.dateEnd)).isNull()

        assertThat(ZonedDateTime.ofInstant(event.dateStart.value.toInstant(), ZoneId.systemDefault()).dayOfMonth).isEqualTo(20)
        assertThat(ZonedDateTime.ofInstant(event.dateEnd.value.toInstant(), ZoneId.systemDefault()).dayOfMonth).isEqualTo(23)

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
        val event = Event("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "",
            true,
            true
        ), iCal, null)
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
    fun `multi-day all-day event overlaps with full day range`() {

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
        val event = Event("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "",
            true,
            true
        ), iCal, null)
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
        val event = Event("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "",
            true,
            true
        ), iCal, null)
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
        val event = Event("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "",
            true,
            true
        ), iCal, null)
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
        val event = Event("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "",
            true,
            true
        ), iCal, null)

        val displayRangeFrom = LocalDate.of(2020, 7, 1)
        val displayRangeTo = LocalDate.of(2020, 7, 1)

        val occurrences = event.generateOccurrencesInFullDayRange(displayRangeFrom, displayRangeTo, displayTimeZoneId)

        assertThat(occurrences!!.size).isEqualTo(1)
        assertThat(occurrences.first().occurrenceNumber).isEqualTo(7)
        assertThat(occurrences.first().startDateTime).isEqualTo(ZonedDateTime.of(2020, 7, 1, 17, 0, 0, 0, ZoneId.of(displayTimeZoneId)))
        assertThat(occurrences.first().endDateTime).isEqualTo(ZonedDateTime.of(2020, 7, 1, 17, 30, 0, 0, ZoneId.of(displayTimeZoneId)))

    }

    @Test
    fun `generate occurrences of all-day event with BYDAY within full-day range`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    DTSTART;VALUE=DATE:20200501
    RRULE:FREQ=MONTHLY;BYDAY=1FR
    SEQUENCE:0
    UID:59rnikoltd7srebautub9lmhv2@google.com
    DTSTAMP:20200514T130648Z
    SUMMARY:Monthly 1st Friday
    DTEND;VALUE=DATE:20200502
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val iCal = ICalUtils.parseICalString(iCalString)!!
        val displayTimeZoneId = "Europe/Paris"
        val event = Event("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "",
            true,
            true
        ), iCal, null)

        val displayRangeTo = LocalDate.of(2020, 12, 31)

        val occurrence5 = event.generateOccurrence(5, displayTimeZoneId)

        assertThat(occurrence5!!).isNotNull()

        assertThat(occurrence5.occurrenceNumber).isEqualTo(5)
        assertThat(occurrence5.startDateTime).isEqualTo(ZonedDateTime.of(2020, 9, 4, 0, 0, 0, 0, ZoneId.of(displayTimeZoneId)))
        assertThat(occurrence5.endDateTime).isEqualTo(ZonedDateTime.of(2020, 9, 5, 0, 0, 0, 0, ZoneId.of(displayTimeZoneId)))

    }

    @Test
    fun `generate occurrences of part-day event with BYSETPOS within full-day range`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Proton Technologies//AndroidCalendar 0.2.3//EN
    BEGIN:VEVENT
    DTSTART;TZID=Europe/Zurich:20200728T130000
    DTEND;TZID=Europe/Zurich:20200728T133000
    RRULE:FREQ=MONTHLY;BYDAY=TU;BYSETPOS=4
    SEQUENCE:0
    SUMMARY:monthly on fourth Tuesday
    STATUS:CONFIRMED
    DTSTAMP:20200728T103442Z
    UID:TaDoa1gh-CVheCqJbaIc86Up96cX@proton.me
    BEGIN:VALARM
    ACTION:DISPLAY
    TRIGGER;RELATED=START:-PT15M
    END:VALARM
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val iCal = ICalUtils.parseICalString(iCalString)!!
        val displayTimeZoneId = "Europe/Vilnius"
        val event = Event("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "",
            true,
            true
        ), iCal, null)

        val displayRangeTo = LocalDate.of(2020, 10, 1)

        val occurrences = event.generateOccurrencesUntil(displayRangeTo, displayTimeZoneId)

        assertThat(occurrences!!.size).isEqualTo(3)

        assertThat(occurrences.first().occurrenceNumber).isEqualTo(1)
        assertThat(occurrences.first().startDateTime).isEqualTo(ZonedDateTime.of(2020, 7, 28, 14, 0, 0, 0, ZoneId.of(displayTimeZoneId)))
        assertThat(occurrences.first().endDateTime).isEqualTo(ZonedDateTime.of(2020, 7, 28, 14, 30, 0, 0, ZoneId.of(displayTimeZoneId)))

        assertThat(occurrences.last().occurrenceNumber).isEqualTo(3)
        assertThat(occurrences.last().startDateTime).isEqualTo(ZonedDateTime.of(2020, 9, 22, 14, 0, 0, 0, ZoneId.of(displayTimeZoneId)))
        assertThat(occurrences.last().endDateTime).isEqualTo(ZonedDateTime.of(2020, 9,  22, 14, 30, 0, 0, ZoneId.of(displayTimeZoneId)))

    }

    @Test
    fun `generate occurrences of part-day event with BYSETPOS within full-day range, different than system timezone`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    DTSTART;TZID=Europe/Vilnius:20200904T073000
    DTEND;TZID=Europe/Vilnius:20200904T080000
    RRULE:FREQ=MONTHLY;BYDAY=FR;BYSETPOS=1
    UID:rQ2fLVjx407UjHiYF272uJMxBerr@proton.me
    DTSTAMP:20200925T070905Z
    SUMMARY:monthly on first Friday
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val iCal = ICalUtils.parseICalString(iCalString)!!
        val displayTimeZoneId = "Europe/Vilnius"
        val event = Event("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "",
            true,
            true
        ), iCal, null)

        val displayRangeTo = LocalDate.of(2020, 12, 31)

        val occurrences = event.generateOccurrencesUntil(displayRangeTo, displayTimeZoneId)

        assertThat(occurrences!!.size).isEqualTo(4)

        assertThat(occurrences.first().occurrenceNumber).isEqualTo(1)
        assertThat(occurrences.first().startDateTime).isEqualTo(ZonedDateTime.of(2020, 9, 4, 7, 30, 0, 0, ZoneId.of(displayTimeZoneId)))
        assertThat(occurrences.first().endDateTime).isEqualTo(ZonedDateTime.of(2020, 9, 4, 8, 0, 0, 0, ZoneId.of(displayTimeZoneId)))

        assertThat(occurrences.last().occurrenceNumber).isEqualTo(4)
        assertThat(occurrences.last().startDateTime).isEqualTo(ZonedDateTime.of(2020, 12, 4, 7, 30, 0, 0, ZoneId.of(displayTimeZoneId)))
        assertThat(occurrences.last().endDateTime).isEqualTo(ZonedDateTime.of(2020, 12, 4, 8, 0, 0, 0, ZoneId.of(displayTimeZoneId)))

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
        val event = Event("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "",
            true,
            true
        ), iCal, null)

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
        val event = Event("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "",
            true,
            true
        ), iCal, null)

        val displayRangeTo = LocalDate.of(2020, 7, 30)

        event.addExceptionDate(2)
        event.addExceptionDate(3)
        event.addExceptionDate(5)
        event.addExceptionDate(10)
        event.addExceptionDate(40) // non-existing occurrence

        val occurrences = event.generateExdateFilteredOccurrencesUntil(displayRangeTo, displayTimeZoneId) ?: emptyList()

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
        val event = Event("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "",
            true,
            true
        ), iCal, null)

        val displayRangeTo = LocalDate.of(2020, 7, 30)

        event.addExceptionDate(2)
        event.addExceptionDate(3)
        event.addExceptionDate(5)
        event.addExceptionDate(10)
        event.addExceptionDate(40) // non-existing occurrence

        val occurrences = event.generateExdateFilteredOccurrencesUntil(displayRangeTo, displayTimeZoneId) ?: emptyList() //event.filterOutOccurrences(event.generateOccurrencesUntil(displayRangeTo, ZoneId.systemDefault().id) ?: emptyList())

        assertThat(occurrences.size).isEqualTo(16)
        assertThat(occurrences.none { it.occurrenceNumber == 2 }).isTrue()
        assertThat(occurrences.none { it.occurrenceNumber == 3 }).isTrue()
        assertThat(occurrences.none { it.occurrenceNumber == 5 }).isTrue()
        assertThat(occurrences.none { it.occurrenceNumber == 10 }).isTrue()
        assertThat(occurrences.all { it.startDateTime.zone.id == displayTimeZoneId }).isTrue()

    }

    @Test
    fun `filter out occurrences by RECURRENCE-ID`() {

        val iCals = listOf(
            """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    DTSTART;VALUE=DATE:20200710
    RECURRENCE-ID;VALUE=DATE:20200710
    SUMMARY:full day reccur\, single edit
    UID:VrLeK2GFu96clzUzTLofDf0_hSDy@proton.me
    DTSTAMP:20200706T161209Z
    DTEND;VALUE=DATE:20200711
    BEGIN:VALARM
    TRIGGER:-PT15H
    ACTION:DISPLAY
    END:VALARM
    END:VEVENT
    END:VCALENDAR
            """.trimIndent(),
            """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    DTSTART;VALUE=DATE:20200708
    RRULE:FREQ=DAILY;UNTIL=20200710
    SUMMARY:full day reccur
    UID:VrLeK2GFu96clzUzTLofDf0_hSDy@proton.me
    DTSTAMP:20200706T161209Z
    DTEND;VALUE=DATE:20200709
    BEGIN:VALARM
    TRIGGER:-PT15H
    ACTION:DISPLAY
    END:VALARM
    END:VEVENT
    END:VCALENDAR    
            """.trimIndent(),
            """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    DTSTART;TZID=Europe/Vilnius:20200707T190000
    DTEND;TZID=Europe/Vilnius:20200707T193000
    RRULE:FREQ=DAILY;UNTIL=20200711T205959Z
    SUMMARY:part-day\, Vilnius\, every day without stop
    UID:yb2puCFfwOqo40SypyVBBCUsnQ2I@proton.me
    DTSTAMP:20200706T154349Z
    BEGIN:VALARM
    TRIGGER:-PT15M
    ACTION:DISPLAY
    END:VALARM
    END:VEVENT
    END:VCALENDAR
            """.trimIndent()
        )

        val events = iCals.mapIndexed { index, iCal ->
            Event("eventId-${index}", me.proton.android.calendar.domain.model.Calendar(
                "id",
                "calendar",
                "",
                true,
                true
            ), ICalUtils.parseICalString(iCal)!!, null)
        }

        val filtered = events.filterOccurencesByRecurrenceId()

        assertThat(filtered.size).isEqualTo(2)
        assertThat(filtered.map { it.summary }.containsAll(listOf("full day reccur, single edit", "part-day, Vilnius, every day without stop")))

    }

    @Test
    fun `filter and generate occurrences with single edits`() {

        val iCals = listOf( // original event on 3rd
            """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    DTSTART;TZID=Europe/Zurich:20200803T120000
    DTEND;TZID=Europe/Zurich:20200803T123000
    RRULE:FREQ=DAILY
    EXDATE;TZID=Europe/Zurich:20200807T120000
    SEQUENCE:0
    SUMMARY:d2
    UID:ahaBeeTYIPTisogZGV7ASf1htS0T@proton.me
    DTSTAMP:20200803T150732Z
    BEGIN:VALARM
    TRIGGER:-PT15M
    ACTION:DISPLAY
    END:VALARM
    END:VEVENT
    END:VCALENDAR
            """.trimIndent(), // event on 4th, changed only time
            """
        BEGIN:VCALENDAR
	    VERSION:2.0
	    BEGIN:VEVENT
	    DTSTART;TZID=Europe/Zurich:20200804T133000
	    DTEND;TZID=Europe/Zurich:20200804T140000
	    RECURRENCE-ID;TZID=Europe/Zurich:20200804T120000
	    SEQUENCE:1
	    SUMMARY:d2
	    UID:ahaBeeTYIPTisogZGV7ASf1htS0T@proton.me
	    DTSTAMP:20200803T150732Z
	    BEGIN:VALARM
	    TRIGGER:-PT15M
	    ACTION:DISPLAY
	    END:VALARM
	    END:VEVENT
	    END:VCALENDAR
            """.trimIndent(), // event on 6th, changed only summary, not time
            """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    DTSTART;TZID=Europe/Zurich:20200806T120000
    DTEND;TZID=Europe/Zurich:20200806T123000
    RECURRENCE-ID;TZID=Europe/Zurich:20200806T120000
    SEQUENCE:1
    SUMMARY:d2 only summary edit
    UID:ahaBeeTYIPTisogZGV7ASf1htS0T@proton.me
    DTSTAMP:20200803T150732Z
    BEGIN:VALARM
    TRIGGER:-PT15M
    ACTION:DISPLAY
    END:VALARM
    END:VEVENT
    END:VCALENDAR
            """.trimIndent(), // event on 8th moved to 9th on different time & edited summary
            """
        BEGIN:VCALENDAR
	    VERSION:2.0
	    BEGIN:VEVENT
	    DTSTART;TZID=Europe/Zurich:20200809T133000
	    DTEND;TZID=Europe/Zurich:20200809T140000
	    RECURRENCE-ID;TZID=Europe/Zurich:20200808T120000
	    SEQUENCE:1
	    SUMMARY:d2 moved from 8th to 9th
	    UID:ahaBeeTYIPTisogZGV7ASf1htS0T@proton.me
	    DTSTAMP:20200803T150732Z
	    BEGIN:VALARM
	    TRIGGER:-PT15M
	    ACTION:DISPLAY
	    END:VALARM
	    END:VEVENT
	    END:VCALENDAR
            """.trimIndent(), // event on 2nd moved from 3rd by "this" editing original event
            """
            BEGIN:VCALENDAR
		    VERSION:2.0
		    BEGIN:VEVENT
		    DTSTART;TZID=Europe/Zurich:20200802T120000
		    DTEND;TZID=Europe/Zurich:20200802T123000
		    RECURRENCE-ID;TZID=Europe/Zurich:20200803T120000
		    SEQUENCE:1
		    SUMMARY:d2
		    UID:ahaBeeTYIPTisogZGV7ASf1htS0T@proton.me
		    DTSTAMP:20200803T150732Z
		    BEGIN:VALARM
		    TRIGGER:-PT15M
		    ACTION:DISPLAY
		    END:VALARM
		    END:VEVENT
		    END:VCALENDAR
            """.trimIndent(),
            """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    DTSTART;TZID=Europe/Zurich:20200809T183000
    DTEND;TZID=Europe/Zurich:20200809T190000
    RECURRENCE-ID;TZID=Europe/Zurich:20200810T120000
    SEQUENCE:2
    SUMMARY:d2 moved from 10th to 9th
    UID:ahaBeeTYIPTisogZGV7ASf1htS0T@proton.me
    DTSTAMP:20200803T150732Z
    BEGIN:VALARM
    TRIGGER:-PT15M
    ACTION:DISPLAY
    END:VALARM
    END:VEVENT
    END:VCALENDAR
            """.trimIndent()
        )

        val displayRangeTo = LocalDate.of(2020, 8, 9)
        val displayTimeZoneId = "Europe/Zurich"

        val events = iCals.mapIndexed { index, iCal ->
            Event("eventId-${index}", me.proton.android.calendar.domain.model.Calendar(
                "id",
                "calendar",
                "",
                true,
                true
            ), ICalUtils.parseICalString(iCal)!!, null)
        }

        // 2: 12:00-12:30 [occ 1]
        // 3: nothing visible, it was moved to 2nd
        // 4: 13:30-14:00  [occ 2]
        // 5: 12:00-12:30  [occ 3]
        // 6: 12:00-12:30 edited summary [occ 4]
        // 7: nothing visible because of exdate [occ 5]
        // 8: nothing visible, it was moved to 9th and changed time
        // 9: 12:00-12:30 [occ 7], 13:30-14:00 [occ 6], 18:30-19:00 [occ8]
        // last "ghost occurrence" is on 10th but it was moved to 9th

        val mapped = ICalUtils.expandOccurrencesWithSingleEdits(events.first(), events, displayRangeTo, displayTimeZoneId)!!

        // there are 7 occurrences until 2020-08-09 and one additional that was moved from 2020-08-10 to 2020-08-09
        assertThat(mapped.size).isEqualTo(8)

        val filteredByExdates = mapped.filterOutOccurrencesByExdates(events.first())

        // one of the occurrences should be filtered out by exdate
        assertThat(filteredByExdates.size).isEqualTo(7)
        assertThat(filteredByExdates.find { it.occurrence!!.occurrenceNumber == 5 }).isNull()


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
        val event = Event("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "",
            true,
            true
        ), iCal, null)

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

        val event = Event("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "",
            true,
            true
        ), calendar, null)

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
        val event = Event("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "",
            true,
            true
        ), iCal, null)

        event.addExceptionDate(1)
        event.addExceptionDate(3)
        event.addExceptionDate(10)

        val exceptionDates = event.getExceptionDates()!!

        TestsLogger.d("exception dates: $exceptionDates")
        TestsLogger.d("cal=${event.iCalendar.printToString()}")

        assertThat(exceptionDates.size).isEqualTo(3)
        assertThat(exceptionDates[0].toLocalDate()).isEqualTo(LocalDate.of(2020, 6, 26))

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

        val event = Event("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "",
            true,
            true
        ), calendar, null)

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
    fun `handle 'delete this and future' for part-day event with COUNT`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    DTSTART;TZID=/Europe/Vilnius:20200706T130000
    DTEND;TZID=/Europe/Vilnius:20200706T133000
    RRULE:FREQ=DAILY;COUNT=5
    SUMMARY:part-day every day 5 times\, Vilnius
    UID:b87agM1DYlWT_cNwG1DeMN90qxw5@proton.me
    DTSTAMP:20200706T100055Z
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val iCal = ICalUtils.parseICalString(iCalString)!!
        val event = Event("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "",
            true,
            true
        ), iCal, null)

        event.handleDeleteThisAndFuture(4)
        assertThat(event.iCalEvent.recurrenceRule.value.count).isEqualTo(3)

        event.handleDeleteThisAndFuture(2)
        assertThat(event.iCalEvent.recurrenceRule.value.count).isEqualTo(1)

    }

    @Test
    fun `handle 'delete this and future' for part-day event without COUNT & UNTIL`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    DTSTART;TZID=Europe/Vilnius:20200707T190000
    DTEND;TZID=Europe/Vilnius:20200707T193000
    RRULE:FREQ=DAILY
    SUMMARY:part-day\, Vilnius\, every day without stop
    UID:yb2puCFfwOqo40SypyVBBCUsnQ2I@proton.me
    DTSTAMP:20200706T154349Z
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val iCal = ICalUtils.parseICalString(iCalString)!!
        val event = Event("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "",
            true,
            true
        ), iCal, null)

        event.handleDeleteThisAndFuture(6)

        // we delete "6th and future occurrences"
        // 6th occurrence happens on 2020-07-12
        // so we should set UNTIL to 1 second before midnight of the previous day

        assertThat(event.iCalEvent.recurrenceRule.value.frequency).isEqualTo(Frequency.DAILY)
        assertThat(event.iCalEvent.recurrenceRule.value.until.hasTime()).isTrue()
        assertThat(ZonedDateTime.ofInstant(event.iCalEvent.recurrenceRule.value.until.toInstant(), ZoneId.of("Europe/Vilnius"))).isEqualTo(ZonedDateTime.of(2020, 7, 11, 23, 59, 59, 0, ZoneId.of("Europe/Vilnius")))

    }

    @Test
    fun `handle 'delete this and future' for all-day event without COUNT`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    DTSTART;VALUE=DATE:20200708
    RRULE:FREQ=DAILY;UNTIL=20200712
    SUMMARY:full day reccur
    UID:VrLeK2GFu96clzUzTLofDf0_hSDy@proton.me
    DTSTAMP:20200706T161209Z
    DTEND;VALUE=DATE:20200709
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val iCal = ICalUtils.parseICalString(iCalString)!!
        val event = Event("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "",
            true,
            true
        ), iCal, null)

        event.handleDeleteThisAndFuture(4)

        // we delete "4th and future occurrences"
        // 4th occurrence happens on 2020-07-11
        // so we should set UNTIL to previous day without TIME part

        TestsLogger.d("${ZonedDateTime.ofInstant(event.iCalEvent.recurrenceRule.value.until.toInstant(), ZoneId.systemDefault()).toLocalDate()}")

        //RRULE:FREQ=DAILY;UNTIL=20200710

        assertThat(event.iCalEvent.recurrenceRule.value.frequency).isEqualTo(Frequency.DAILY)
        assertThat(event.iCalEvent.recurrenceRule.value.until.hasTime()).isFalse()
        assertThat(ZonedDateTime.ofInstant(event.iCalEvent.recurrenceRule.value.until.toInstant(), ZoneId.systemDefault()).toLocalDate()).isEqualTo(LocalDate.of(2020, 7, 10))

    }

    @Test
    fun `handle 'delete this and future' for all-day event with UNTIL`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    DTSTART;VALUE=DATE:20200708
    RRULE:FREQ=DAILY;UNTIL=20200712
    SUMMARY:full day reccur
    UID:VrLeK2GFu96clzUzTLofDf0_hSDy@proton.me
    DTSTAMP:20200706T161209Z
    DTEND;VALUE=DATE:20200709
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val iCal = ICalUtils.parseICalString(iCalString)!!
        val event = Event("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "",
            true,
            true
        ), iCal, null)

        event.handleDeleteThisAndFuture(4)

        // we delete "4th and future occurrences"
        // 4th occurrence happens on 2020-07-11
        // so we should set UNTIL to previous day without TIME part

        TestsLogger.d("${ZonedDateTime.ofInstant(event.iCalEvent.recurrenceRule.value.until.toInstant(), ZoneId.systemDefault()).toLocalDate()}")

        //RRULE:FREQ=DAILY;UNTIL=20200710

        assertThat(event.iCalEvent.recurrenceRule.value.frequency).isEqualTo(Frequency.DAILY)
        assertThat(event.iCalEvent.recurrenceRule.value.until.hasTime()).isFalse()
        assertThat(ZonedDateTime.ofInstant(event.iCalEvent.recurrenceRule.value.until.toInstant(), ZoneId.systemDefault()).toLocalDate()).isEqualTo(LocalDate.of(2020, 7, 10))

    }

    @Test
    fun `generate occurrences of partial-day event with UNTIL`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    DTSTART;TZID=Europe/Zurich:20201001T163000
    DTEND;TZID=Europe/Zurich:20201001T170000
    RRULE:FREQ=DAILY;UNTIL=20201004T215959Z
    UID:wK6IfGtwq4PX1x1qo9WY4dXxtDEU@proton.me
    DTSTAMP:20200928T141234Z
    SUMMARY:recur zh UNTIL Oct 4\, 16:30
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        // this should be happening on 1st, 2nd, 3rd and 4th in Zurich timezone

        val iCal = ICalUtils.parseICalString(iCalString)!!
        val displayTimeZoneId = "Europe/Zurich"
        val event = Event("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "",
            true,
            true
        ), iCal, null)

        val occurrences = event.generateOccurrencesUntilOrCount(displayTimeZoneId, null, 10)

        assertThat(occurrences!!.size).isEqualTo(4)

    }

    @Test
    fun `generate occurrences of partial-day event with UNTIL, GMT+11`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    DTSTART;TZID=Antarctica/Macquarie:20201002T020000
    DTEND;TZID=Antarctica/Macquarie:20201002T023000
    RRULE:FREQ=DAILY;UNTIL=20201004T125959Z
    UID:k8EOBVhcHvW73TBNBIbKxxbNf16T@proton.me
    DTSTAMP:20200928T145841Z
    SUMMARY:gmt+11 UNTIL Oct 4
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        // this should be happening on 1st, 2nd and 3rd in Vilnius timezone

        val iCal = ICalUtils.parseICalString(iCalString)!!
        val displayTimeZoneId = "Europe/Vilnius"
        val event = Event("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "",
            true,
            true
        ), iCal, null)

        val occurrences = event.generateOccurrencesUntilOrCount(displayTimeZoneId, null, 10)!!

        assertThat(occurrences.first()).isEqualTo(Event.Occurrence(
            ZonedDateTime.of(2020, 10, 1, 18, 0, 0, 0, ZoneId.of(displayTimeZoneId)),
            ZonedDateTime.of(2020, 10, 1, 18, 30, 0, 0, ZoneId.of(displayTimeZoneId)),
            1))

        assertThat(occurrences.last()).isEqualTo(Event.Occurrence(
            ZonedDateTime.of(2020, 10, 3, 18, 0, 0, 0, ZoneId.of(displayTimeZoneId)),
            ZonedDateTime.of(2020, 10, 3, 18, 30, 0, 0, ZoneId.of(displayTimeZoneId)),
            3))

    }

    @Test
    fun `generate occurrences of partial-day event with UNTIL, GMT+11, displayed in GMT+12`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    DTSTART;TZID=Antarctica/Macquarie:20201002T020000
    DTEND;TZID=Antarctica/Macquarie:20201002T023000
    RRULE:FREQ=DAILY;UNTIL=20201004T125959Z
    UID:k8EOBVhcHvW73TBNBIbKxxbNf16T@proton.me
    DTSTAMP:20200928T145841Z
    SUMMARY:gmt+11 UNTIL Oct 4
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        // this should be happening on 2nd, 3rd and 4th in Pacific/Fiji timezone

        val iCal = ICalUtils.parseICalString(iCalString)!!
        val displayTimeZoneId = "Pacific/Fiji"
        val event = Event("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "",
            true,
            true
        ), iCal, null)

        val occurrences = event.generateOccurrencesUntilOrCount(displayTimeZoneId, null, 10)!!

        assertThat(occurrences.first()).isEqualTo(Event.Occurrence(
            ZonedDateTime.of(2020, 10, 2, 3, 0, 0, 0, ZoneId.of(displayTimeZoneId)),
            ZonedDateTime.of(2020, 10, 2, 3, 30, 0, 0, ZoneId.of(displayTimeZoneId)),
            1))

        assertThat(occurrences.last()).isEqualTo(Event.Occurrence(
            ZonedDateTime.of(2020, 10, 4, 3, 0, 0, 0, ZoneId.of(displayTimeZoneId)),
            ZonedDateTime.of(2020, 10, 4, 3, 30, 0, 0, ZoneId.of(displayTimeZoneId)),
            3))

    }

    @Test
    fun `generate occurrences of partial-day event with UNTIL, GMT-11`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    DTSTART;TZID=Pacific/Pago_Pago:20201001T040000
    DTEND;TZID=Pacific/Pago_Pago:20201001T043000
    RRULE:FREQ=DAILY;UNTIL=20201005T105959Z
    UID:VU06MoYMt_mNWMlWuN5cfRuC3ZAl@proton.me
    DTSTAMP:20200928T145535Z
    SUMMARY:gmt-11 recur daily UNTIL Oct 4\, 8:30
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        // this should be happening on 1st, 2nd, 3rd and 4th in Vilnius timezone

        val iCal = ICalUtils.parseICalString(iCalString)!!
        val displayTimeZoneId = "Europe/Vilnius"
        val event = Event("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "",
            true,
            true
        ), iCal, null)

        val occurrences = event.generateOccurrencesUntilOrCount(displayTimeZoneId, null, 10)

        assertThat(occurrences!!.size).isEqualTo(4)

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
        val event = Event("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "",
            true,
            true
        ), iCal, null)

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
    fun `generate n-th occurrence of partial-day recurring event with byday, display in a timezone that make it happen the next day`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    DTSTART;TZID=America/Noronha:20201004T230000
    DTEND;TZID=America/Noronha:20201004T233000
    RRULE:FREQ=WEEKLY;BYDAY=SU
    SEQUENCE:1
    SUMMARY:Test event
    STATUS:CONFIRMED
    DTSTAMP:20201029T152822Z
    UID:ph5aTJJKGwKURIwnXh9CN0jW4isD@proton.me
    BEGIN:VALARM
    ACTION:DISPLAY
    TRIGGER;RELATED=START:-PT5H
    """.trimIndent()

        val iCal = ICalUtils.parseICalString(iCalString)!!
        val displayTimeZoneId = "Europe/Paris"
        val event = Event("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "",
            true,
            true
        ), iCal, null)

        val occurrence1 = event.generateOccurrence(1, displayTimeZoneId)
        val occurrence3 = event.generateOccurrence(3, displayTimeZoneId)
        val occurrence4 = event.generateOccurrence(4, displayTimeZoneId)

        assertThat(occurrence1).isEqualTo(Event.Occurrence(
            ZonedDateTime.of(2020, 10, 5, 3, 0, 0, 0, ZoneId.of(displayTimeZoneId)),
            ZonedDateTime.of(2020, 10, 5, 3, 30, 0, 0, ZoneId.of(displayTimeZoneId)),
            1))

        assertThat(occurrence3).isEqualTo(Event.Occurrence(
            ZonedDateTime.of(2020, 10, 19, 3, 0, 0, 0, ZoneId.of(displayTimeZoneId)),
            ZonedDateTime.of(2020, 10, 19, 3, 30, 0, 0, ZoneId.of(displayTimeZoneId)),
            3))

        assertThat(occurrence4).isEqualTo(Event.Occurrence(
            ZonedDateTime.of(2020, 10, 26, 2, 0, 0, 0, ZoneId.of(displayTimeZoneId)),
            ZonedDateTime.of(2020, 10, 26, 2, 30, 0, 0, ZoneId.of(displayTimeZoneId)),
            4))
    }

    @Test
    fun `generate n-th occurrence of partial-day multi-day event with BYDAY, display in different timezone`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    DTSTART;TZID=Europe/Zurich:20200708T154500
    DTEND;TZID=Europe/Zurich:20200709T161500
    RRULE:FREQ=WEEKLY;INTERVAL=1;BYDAY=WE
    SUMMARY:recurring every week 5 times
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
        val event = Event("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "",
            true,
            true
        ), iCal, null)

        val occurrence1 = event.generateOccurrence(1, displayTimeZoneId)
        val occurrence2 = event.generateOccurrence(2, displayTimeZoneId)

        TestsLogger.d("$occurrence1")
        TestsLogger.d("$occurrence2")

        assertThat(occurrence1).isEqualTo(Event.Occurrence(
            ZonedDateTime.of(2020, 7, 8, 16, 45, 0, 0, ZoneId.of(displayTimeZoneId)),
            ZonedDateTime.of(2020, 7, 9, 17, 15, 0, 0, ZoneId.of(displayTimeZoneId)),
            1))

        assertThat(occurrence2).isEqualTo(Event.Occurrence(
            ZonedDateTime.of(2020, 7, 15, 16, 45, 0, 0, ZoneId.of(displayTimeZoneId)),
            ZonedDateTime.of(2020, 7, 16, 17, 15, 0, 0, ZoneId.of(displayTimeZoneId)),
            2))

    }

    @Test
    fun `generate entire Event model with datetime overwritten by n-th occurrence`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    DTSTART;TZID=Europe/Budapest:20200708T140000
    DTEND;TZID=Europe/Budapest:20200709T143000
    RRULE:FREQ=WEEKLY
    SUMMARY:weekly\, 2-day part-time
    UID:iUsRIL4N3Wq6LN9MgvYdV42m2FIY@proton.me
    DTSTAMP:20200716T113118Z
    BEGIN:VALARM
    TRIGGER:-PT15M
    ACTION:DISPLAY
    END:VALARM
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val iCal = ICalUtils.parseICalString(iCalString)!!
        val displayTimeZoneId = "Europe/Vilnius"
        val event = Event("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "",
            true,
            true
        ), iCal, null)

        val eventWithOccurrence1 = event.withOccurrence(1, displayTimeZoneId)!!
        val eventWithOccurrence2 = event.withOccurrence(2, displayTimeZoneId)!!
        val eventWithOccurrence5 = event.withOccurrence(5, displayTimeZoneId)!!

        assertThat(event.getStart(displayTimeZoneId)).isEqualTo(ZonedDateTime.of(2020, 7, 8, 15, 0, 0, 0, ZoneId.of(displayTimeZoneId)))
        assertThat(event.getEnd(displayTimeZoneId)).isEqualTo(ZonedDateTime.of(2020, 7, 9, 15, 30, 0, 0, ZoneId.of(displayTimeZoneId)))

        assertThat(eventWithOccurrence1.getStart(displayTimeZoneId)).isEqualTo(ZonedDateTime.of(2020, 7, 8, 15, 0, 0, 0, ZoneId.of(displayTimeZoneId)))
        assertThat(eventWithOccurrence1.getEnd(displayTimeZoneId)).isEqualTo(ZonedDateTime.of(2020, 7, 9, 15, 30, 0, 0, ZoneId.of(displayTimeZoneId)))
        assertThat(eventWithOccurrence1.occurrence!!.occurrenceNumber).isEqualTo(1)

        assertThat(eventWithOccurrence2.getStart(displayTimeZoneId)).isEqualTo(ZonedDateTime.of(2020, 7, 15, 15, 0, 0, 0, ZoneId.of(displayTimeZoneId)))
        assertThat(eventWithOccurrence2.getEnd(displayTimeZoneId)).isEqualTo(ZonedDateTime.of(2020, 7, 16, 15, 30, 0, 0, ZoneId.of(displayTimeZoneId)))
        assertThat(eventWithOccurrence2.occurrence!!.occurrenceNumber).isEqualTo(2)

        assertThat(eventWithOccurrence5.getStart(displayTimeZoneId)).isEqualTo(ZonedDateTime.of(2020, 8, 5, 15, 0, 0, 0, ZoneId.of(displayTimeZoneId)))
        assertThat(eventWithOccurrence5.getEnd(displayTimeZoneId)).isEqualTo(ZonedDateTime.of(2020, 8, 6, 15, 30, 0, 0, ZoneId.of(displayTimeZoneId)))
        assertThat(eventWithOccurrence5.occurrence!!.occurrenceNumber).isEqualTo(5)

    }

    @Test
    fun `correctly sanitise partial-day Event without DTEND`() {

        val event = ICalUtils.createNewEvent()
        event.setStart(LocalDate.of(2020, 1, 20), LocalTime.of(10, 0), "Europe/Zurich")

        assertThat(event.sanitise()).isTrue()

        assertThat(event.dateStart.value.hasTime()).isTrue()
        assertThat(event.dateEnd.value.hasTime()).isTrue()

        assertThat(event.getEnd("Europe/Zurich")!!.toLocalDate()).isEqualTo(LocalDate.of(2020, 1, 20))
        assertThat(event.getEnd("Europe/Zurich")!!.toLocalTime()).isEqualTo(LocalTime.of(10, 0))

    }

    @Test
    fun `correctly sanitise all-day Event without DTEND`() {

        val event = ICalUtils.createNewEvent()
        event.setStart(LocalDate.of(2020, 1, 20))

        assertThat(event.sanitise()).isTrue()

        assertThat(event.dateStart.value).isNotNull()
        assertThat(event.dateEnd.value).isNotNull()

        assertThat(event.getEnd("Europe/Zurich")!!.toLocalDate()).isEqualTo(LocalDate.of(2020, 1, 21))

    }

    @Test
    fun `clone iCalendar`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Proton Technologies//AndroidCalendar 0.2.3//EN
    BEGIN:VEVENT
    DTSTART;TZID=Europe/Zurich:20200728T130000
    DTEND;TZID=Europe/Zurich:20200728T133000
    RRULE:FREQ=MONTHLY;BYDAY=TU;BYSETPOS=-1
    SEQUENCE:0
    SUMMARY:monthly on last Tuesday
    STATUS:CONFIRMED
    DTSTAMP:20200728T103442Z
    UID:TaDoa1gh-CVheCqJbaIc86Up96cX@proton.me
    BEGIN:VALARM
    ACTION:DISPLAY
    TRIGGER;RELATED=START:-PT15M
    END:VALARM
    BEGIN:VALARM
    ACTION:DISPLAY
    TRIGGER;RELATED=START:-PT15M
    END:VALARM
    BEGIN:VALARM
    ACTION:DISPLAY
    TRIGGER;RELATED=START:-PT30M
    END:VALARM
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val original = ICalUtils.parseICalString(iCalString)!!
        val cloned = original.clone()

        original.events.first().summary.value = "edited summary of original event"

        assertThat(cloned.events.first().summary.value).isEqualTo("monthly on last Tuesday")
        assertThat(cloned.events.first().alarms.size).isEqualTo(3)
        assertThat(cloned.timezoneInfo.getTimezone(cloned.events.first().dateStart).globalId).isEqualTo("Europe/Zurich")

    }

    @Test
    fun `recurring event ends after one occurrence`() {
        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    DTSTAMP:20201014T164542Z
    UID:MPguUggfUmij1uQgo5SHDWa0I492@proton.me
    STATUS:CONFIRMED
    SEQUENCE:0
    DTSTART;TZID=Europe/Paris:20210103T190000
    DTEND;TZID=Europe/Paris:20210103T193000
    RRULE:FREQ=DAILY;COUNT=1
    SUMMARY:Single
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val timeZoneId = "Europe/Paris"

        val iCal = ICalUtils.parseICalString(iCalString)!!
        val event = Event("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "",
            true,
            true
        ), iCal, null)

        assertThat(event.isSingleOccurrenceRecurring(timeZoneId)).isTrue()
    }

    @Test
    fun `recurring event ends after two occurrences`() {
        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    DTSTAMP:20201014T164542Z
    UID:MPguUggfUmij1uQgo5SHDWa0I492@proton.me
    STATUS:CONFIRMED
    SEQUENCE:0
    DTSTART;TZID=Europe/Paris:20210103T190000
    DTEND;TZID=Europe/Paris:20210103T193000
    RRULE:FREQ=DAILY;COUNT=2
    SUMMARY:Single
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val timeZoneId = "Europe/Paris"

        val iCal = ICalUtils.parseICalString(iCalString)!!
        val event = Event("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "",
            true,
            true
        ), iCal, null)

        assertThat(event.isSingleOccurrenceRecurring(timeZoneId)).isFalse()
    }

    @Test
    fun `recurring event ends on the same day`() {
        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    DTSTAMP:20201014T164542Z
    UID:MPguUggfUmij1uQgo5SHDWa0I492@proton.me
    STATUS:CONFIRMED
    SEQUENCE:0
    DTSTART;TZID=Europe/Paris:20210103T190000
    DTEND;TZID=Europe/Paris:20210103T193000
    RRULE:FREQ=DAILY;UNTIL=20210103T225959Z
    SUMMARY:Single
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val timeZoneId = "Europe/Paris"

        val iCal = ICalUtils.parseICalString(iCalString)!!
        val event = Event("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "",
            true,
            true
        ), iCal, null)

        assertThat(event.isRecurringUntilSameDay(timeZoneId)).isTrue()
    }

    @Test
    fun `recurring event ends on the next day`() {
        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    SEQUENCE:1
    DTSTAMP:20201014T170339Z
    UID:oT8hagmb4v0xNIo8xC0Fk8LLllsN@proton.me
    STATUS:CONFIRMED
    RRULE:FREQ=DAILY;UNTIL=20210104T225959Z
    SUMMARY:Double
    DTEND;TZID=Europe/Paris:20210103T190000
    DTSTART;TZID=Europe/Paris:20210103T183000
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val timeZoneId = "Europe/Paris"

        val iCal = ICalUtils.parseICalString(iCalString)!!
        val event = Event("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "",
            true,
            true
        ), iCal, null)

        assertThat(event.isRecurringUntilSameDay(timeZoneId)).isFalse()
    }

    @Test
    fun `check partial day single edit recurrence id date when original event was all day`() {
        val iCals = listOf(
            """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    DTSTART;VALUE=DATE:20201104
    DTEND;VALUE=DATE:20201105
    RRULE:FREQ=WEEKLY;COUNT=2;BYDAY=WE
    SEQUENCE:2
    SUMMARY:Weekly recurring
    STATUS:CONFIRMED
    DTSTAMP:20201105T135134Z
    UID:c2vDbiFQVRp847bbZRNQKLeZ6pK7@proton.me
    END:VEVENT
    END:VCALENDAR
    """.trimIndent(),
            """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    DTSTART;TZID=Europe/Paris:20201104T135100
    DTEND;TZID=Europe/Paris:20201104T142100
    RECURRENCE-ID;VALUE=DATE:20201104
    SEQUENCE:3
    SUMMARY:Weekly recurring
    STATUS:CONFIRMED
    DTSTAMP:20201105T135151Z
    UID:c2vDbiFQVRp847bbZRNQKLeZ6pK7@proton.me
    END:VEVENT
    END:VCALENDAR
    """.trimIndent())

        val displayTimeZoneId = "Europe/Paris"

        val events = iCals.mapIndexed { index, iCal ->
            Event("eventId-${index}", me.proton.android.calendar.domain.model.Calendar(
                "id",
                "calendar",
                "",
                true,
                true
            ), ICalUtils.parseICalString(iCal)!!, null)
        }

        val displayRangeTo = LocalDate.of(2020, 12, 31)

        val originalEvent = events.first()
        val singleEdit = events[1]
        val mapped = ICalUtils.expandOccurrencesWithSingleEdits(originalEvent, events, displayRangeTo, displayTimeZoneId)!!

        assertThat(eventStartZonedDateTimeToDate(originalEvent.iCalEvent.getStart(displayTimeZoneId)!!, originalEvent.isAllDay())).isEqualTo(eventStartZonedDateTimeToDate(singleEdit.iCalEvent.getStart(displayTimeZoneId)!!, originalEvent.isAllDay()))
        assertThat(mapped[0].iCalEvent.getStart(displayTimeZoneId)).isEqualTo(singleEdit.iCalEvent.getStart(displayTimeZoneId))
    }

    @Test
    fun `reproduce event form all day event date formatted`() {
        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VTIMEZONE
    TZID:UTC
    END:VTIMEZONE
    BEGIN:VEVENT
    SEQUENCE:0
    SUMMARY:All day
    UID:GAqLNyLWPaEIHKd-QYdNTUWlsxEZ@proton.me
    DTSTAMP:20201111T123938Z
    DTSTART;VALUE=DATE:20201117
    DTEND;VALUE=DATE:20201117
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val iCal = ICalUtils.parseICalString(iCalString)!!
        val event = Event("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "",
            true,
            true
        ), iCal, null)

        val timeZoneId = "UTC"
        // Should return "Tuesday, November 17, 2020" if in English so we just check if day was calculated right
        val formattedStart = event.formatDateOrDateTimeProperty(event.iCalEvent.dateStart, timeZoneId, true, event.isAllDay())
        assertThat(formattedStart.first).isNotNull()
        assertThat(formattedStart.first!!.contains("17")).isTrue()
        assertThat(formattedStart.second).isNull()
    }
}
