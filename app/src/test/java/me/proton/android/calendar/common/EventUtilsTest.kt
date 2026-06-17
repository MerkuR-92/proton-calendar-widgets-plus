package me.proton.android.calendar.common

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import me.proton.android.calendar.common.utils.EventUtilsImpl.calculateFullDayCounter
import me.proton.android.calendar.common.utils.EventUtilsImpl.generateOccurrencesUntil
import me.proton.android.calendar.common.utils.ICalUtilsImpl
import me.proton.android.calendar.domain.indicators.FastOccurrenceGenerator
import me.proton.android.calendar.domain.model.Calendar
import me.proton.android.calendar.domain.model.Event
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId


internal class EventUtilsTest {

    @Test
    fun `spans single day for part-time 2-hour ending at Midnight`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Proton Technologies//AndroidCalendar 0.25.4//EN
    BEGIN:VEVENT
    DTSTAMP:20210915T195051Z
    DTSTART;TZID=Europe/Zurich:20210915T220000
    DTEND;TZID=Europe/Zurich:20210916T000000
    SEQUENCE:0
    SUMMARY:Ends at Midnight
    STATUS:CONFIRMED
    UID:v1uZ9ssGTcP5Lc28MTWj-VjQSPv3@proton.me
    END:VEVENT
    END:VCALENDAR
            """.trimIndent()

        val timeZoneId = "Europe/Zurich"
        val event = Event.from(
            "id",
            Calendar("id", "name", "email", "ownerEmail", "description", DEFAULT_CALENDAR_COLOR,0, "addressId", "memberId", 1, true, 0, 127, 30, emptyList(), emptyList()),
            ICalUtilsImpl.parseICalString(iCalString)!!,
            0
        )!!

        assertThat(event.spansSingleDay(timeZoneId = timeZoneId)).isTrue()

    }

    @Test
    fun `spans single day for part-time zero-duration at Midnight`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Proton Technologies//AndroidCalendar 0.25.4//EN
    BEGIN:VEVENT
    DTSTAMP:20210915T195051Z
    DTSTART;TZID=Europe/Zurich:20210915T000000
    DTEND;TZID=Europe/Zurich:20210915T000000
    SEQUENCE:0
    SUMMARY:Zero-duration start/ends at Midnight
    STATUS:CONFIRMED
    UID:v1uZ9ssGTcP5Lc28MTWj-VjQSPv3@proton.me
    END:VEVENT
    END:VCALENDAR
            """.trimIndent()

        val timeZoneId = "Europe/Zurich"
        val event = Event.from(
            "id",
            Calendar("id", "name", "email", "ownerEmail", "description", DEFAULT_CALENDAR_COLOR,0, "addressId", "memberId", 1, true, 0, 127, 30, emptyList(), emptyList()),
            ICalUtilsImpl.parseICalString(iCalString)!!,
            0
        )!!

        assertThat(event.spansSingleDay(timeZoneId = timeZoneId)).isTrue()

    }

    @Test
    fun `spans single day for part-time 2-day ending at Midnight`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Proton Technologies//AndroidCalendar 0.25.4//EN
    BEGIN:VEVENT
    DTSTAMP:20210915T195051Z
    DTSTART;TZID=Europe/Zurich:20210915T220000
    DTEND;TZID=Europe/Zurich:20210917T000000
    SEQUENCE:0
    SUMMARY:Ends at Midnight, 2-day
    STATUS:CONFIRMED
    UID:v1uZ9ssGTcP5Lc28MTWj-VjQSPv3@proton.me
    END:VEVENT
    END:VCALENDAR
            """.trimIndent()

        val timeZoneId = "Europe/Zurich"
        val event = Event.from(
            "id",
            Calendar("id", "name", "email", "ownerEmail", "description", DEFAULT_CALENDAR_COLOR,0, "addressId", "memberId", 1, true, 0, 127, 30, emptyList(), emptyList()),
            ICalUtilsImpl.parseICalString(iCalString)!!,
            0
        )!!

        assertThat(event.spansSingleDay(timeZoneId = timeZoneId)).isFalse()

    }

    @Test
    fun `spans single day for 1-day all-day`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Proton Technologies//AndroidCalendar 0.25.4//EN
    BEGIN:VEVENT
    DTSTAMP:20210915T195051Z
    DTSTART;VALUE=DATE:20210107
    DTEND;VALUE=DATE:20210108
    SEQUENCE:0
    SUMMARY:1-day
    STATUS:CONFIRMED
    UID:v1uZ9ssGTcP5Lc28MTWj-VjQSPv3@proton.me
    END:VEVENT
    END:VCALENDAR
            """.trimIndent()

        val timeZoneId = "Europe/Zurich"
        val event = Event.from(
            "id",
            Calendar("id", "name", "email", "ownerEmail", "description", DEFAULT_CALENDAR_COLOR,0, "addressId", "memberId", 1, true, 0, 127, 30, emptyList(), emptyList()),
            ICalUtilsImpl.parseICalString(iCalString)!!,
            0
        )!!

        assertThat(event.spansSingleDay(timeZoneId = timeZoneId)).isTrue()

    }

    @Test
    fun `spans single day for 2-day all-day`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Proton Technologies//AndroidCalendar 0.25.4//EN
    BEGIN:VEVENT
    DTSTAMP:20210915T195051Z
    DTSTART;VALUE=DATE:20210107
    DTEND;VALUE=DATE:20210109
    SEQUENCE:0
    SUMMARY:2-day
    STATUS:CONFIRMED
    UID:v1uZ9ssGTcP5Lc28MTWj-VjQSPv3@proton.me
    END:VEVENT
    END:VCALENDAR
            """.trimIndent()

        val timeZoneId = "Europe/Zurich"
        val event = Event.from(
            "id",
            Calendar("id", "name", "email", "ownerEmail", "description", DEFAULT_CALENDAR_COLOR,0, "addressId", "memberId", 1, true, 0, 127, 30, emptyList(), emptyList()),
            ICalUtilsImpl.parseICalString(iCalString)!!,
            0
        )!!

        assertThat(event.spansSingleDay(timeZoneId = timeZoneId)).isFalse()

    }

    @Test
    fun `calculate full day counter for part-time 2-hour ending at Midnight`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Proton Technologies//AndroidCalendar 0.25.4//EN
    BEGIN:VEVENT
    DTSTAMP:20210915T195051Z
    DTSTART;TZID=Europe/Zurich:20210915T220000
    DTEND;TZID=Europe/Zurich:20210916T000000
    SEQUENCE:0
    SUMMARY:Ends at Midnight
    STATUS:CONFIRMED
    UID:v1uZ9ssGTcP5Lc28MTWj-VjQSPv3@proton.me
    END:VEVENT
    END:VCALENDAR
            """.trimIndent()

        val timeZoneId = "Europe/Zurich"
        val event = Event.from(
            "id",
            Calendar("id", "name", "email", "ownerEmail", "description", DEFAULT_CALENDAR_COLOR,0, "addressId", "memberId", 1, true, 0, 127, 30, emptyList(), emptyList()),
            ICalUtilsImpl.parseICalString(iCalString)!!,
            0
        )!!

        assertThat(event.calculateFullDayCounter(LocalDate.of(2021, 9, 15), timeZoneId)).isEqualTo(Pair(1, 1))

    }

    @Test
    fun `calculate full day counter for part-time 2-day ending at Midnight`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Proton Technologies//AndroidCalendar 0.25.4//EN
    BEGIN:VEVENT
    DTSTAMP:20210915T195051Z
    DTSTART;TZID=Europe/Zurich:20210915T220000
    DTEND;TZID=Europe/Zurich:20210917T000000
    SEQUENCE:0
    SUMMARY:Ends at Midnight
    STATUS:CONFIRMED
    UID:v1uZ9ssGTcP5Lc28MTWj-VjQSPv3@proton.me
    END:VEVENT
    END:VCALENDAR
            """.trimIndent()

        val timeZoneId = "Europe/Zurich"
        val event = Event.from(
            "id",
            Calendar("id", "name", "email", "ownerEmail", "description", DEFAULT_CALENDAR_COLOR,0, "addressId", "memberId", 1, true, 0, 127, 30, emptyList(), emptyList()),
            ICalUtilsImpl.parseICalString(iCalString)!!,
            0
        )!!

        assertThat(event.calculateFullDayCounter(LocalDate.of(2021, 9, 15), timeZoneId)).isEqualTo(Pair(1, 2))
        assertThat(event.calculateFullDayCounter(LocalDate.of(2021, 9, 16), timeZoneId)).isEqualTo(Pair(2, 2))

    }

    @Test
    fun `calculate full day counter for part-time zero-duration`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Proton Technologies//AndroidCalendar 0.25.4//EN
    BEGIN:VEVENT
    DTSTAMP:20210915T195051Z
    DTSTART;TZID=Europe/Zurich:20210915T000000
    DTEND;TZID=Europe/Zurich:20210915T000000
    SEQUENCE:0
    SUMMARY:Zero-duration starts/ends at Midnight
    STATUS:CONFIRMED
    UID:v1uZ9ssGTcP5Lc28MTWj-VjQSPv3@proton.me
    END:VEVENT
    END:VCALENDAR
            """.trimIndent()

        val timeZoneId = "Europe/Zurich"
        val event = Event.from(
            "id",
            Calendar("id", "name", "email", "ownerEmail", "description", DEFAULT_CALENDAR_COLOR,0, "addressId", "memberId", 1, true, 0, 127, 30, emptyList(), emptyList()),
            ICalUtilsImpl.parseICalString(iCalString)!!,
            0
        )!!

        assertThat(event.calculateFullDayCounter(LocalDate.of(2021, 9, 15), timeZoneId)).isEqualTo(Pair(1, 1))

    }

    @Test
    fun `calculate full day counter for all-day 1-day`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Proton Technologies//AndroidCalendar 0.25.4//EN
    BEGIN:VEVENT
    DTSTAMP:20210915T195051Z
    DTSTART;VALUE=DATE:20210107
    DTEND;VALUE=DATE:20210108
    SEQUENCE:0
    SUMMARY:1-day
    STATUS:CONFIRMED
    UID:v1uZ9ssGTcP5Lc28MTWj-VjQSPv3@proton.me
    END:VEVENT
    END:VCALENDAR
            """.trimIndent()

        val timeZoneId = "Europe/Zurich"
        val event = Event.from(
            "id",
            Calendar("id", "name", "email", "ownerEmail", "description", DEFAULT_CALENDAR_COLOR,0, "addressId", "memberId", 1, true, 0, 127, 30, emptyList(), emptyList()),
            ICalUtilsImpl.parseICalString(iCalString)!!,
            0
        )!!

        assertThat(event.calculateFullDayCounter(LocalDate.of(2021, 1, 7), timeZoneId)).isEqualTo(Pair(1, 1))

    }

    @Test
    fun `calculate full day counter for all-day 2-day`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Proton Technologies//AndroidCalendar 0.25.4//EN
    BEGIN:VEVENT
    DTSTAMP:20210915T195051Z
    DTSTART;VALUE=DATE:20210107
    DTEND;VALUE=DATE:20210109
    SEQUENCE:0
    SUMMARY:2-day
    STATUS:CONFIRMED
    UID:v1uZ9ssGTcP5Lc28MTWj-VjQSPv3@proton.me
    END:VEVENT
    END:VCALENDAR
            """.trimIndent()

        val timeZoneId = "Europe/Zurich"
        val event = Event.from(
            "id",
            Calendar("id", "name", "email", "ownerEmail", "description", DEFAULT_CALENDAR_COLOR,0, "addressId", "memberId", 1, true, 0, 127, 30, emptyList(), emptyList()),
            ICalUtilsImpl.parseICalString(iCalString)!!,
            0
        )!!

        assertThat(event.calculateFullDayCounter(LocalDate.of(2021, 1, 7), timeZoneId)).isEqualTo(Pair(1, 2))
        assertThat(event.calculateFullDayCounter(LocalDate.of(2021, 1, 8), timeZoneId)).isEqualTo(Pair(2, 2))

    }

    @Test
    fun `worst case expansion benchmark`() {
        val tzId = "Europe/Zurich"
        val toDate = LocalDate.of(2026, 6, 16)
        data class Case(val name: String, val dtStart: LocalDateTime, val rrule: String)
        val cases = listOf(
            Case("DAILY from 2016 (~10y)", LocalDateTime.of(2016, 1, 1, 9, 0, 0), "FREQ=DAILY"),
            Case("DAILY;BYHOUR=9..17 from 2016", LocalDateTime.of(2016, 1, 1, 9, 0, 0), "FREQ=DAILY;BYHOUR=9,10,11,12,13,14,15,16,17"),
            Case("HOURLY from 2020 (~6y)", LocalDateTime.of(2020, 1, 1, 9, 0, 0), "FREQ=HOURLY"),
            Case("HOURLY from 2006 (~20y)", LocalDateTime.of(2006, 1, 1, 9, 0, 0), "FREQ=HOURLY"),
            Case("MINUTELY from 2025-06 (~1y)", LocalDateTime.of(2025, 6, 1, 9, 0, 0), "FREQ=MINUTELY"),
        )
        for (c in cases) {
            val event = recurringEvent(
                """
                BEGIN:VCALENDAR
                VERSION:2.0
                PRODID:-//Proton//AndroidCalendar//EN
                BEGIN:VEVENT
                DTSTAMP:20200101T090000Z
                DTSTART;TZID=$tzId:${fmt(c.dtStart)}
                DTEND;TZID=$tzId:${fmt(c.dtStart.plusMinutes(30))}
                RRULE:${c.rrule}
                UID:worst-${c.name.hashCode()}@proton.me
                END:VEVENT
                END:VCALENDAR
                """
            )
            val t0 = System.nanoTime()
            val occ = event.generateOccurrencesUntil(toDate, tzId)
            val ms = (System.nanoTime() - t0) / 1_000_000
            println("WORSTCASE | ${c.name} | occurrences=${occ?.size} | ${ms}ms")
        }
    }

    private fun recurringEvent(iCalString: String): Event = Event.from(
        "id",
        Calendar("id", "name", "email", "ownerEmail", "description", DEFAULT_CALENDAR_COLOR, 0, "addressId", "memberId", 1, true, 0, 127, 30, emptyList(), emptyList()),
        ICalUtilsImpl.parseICalString(iCalString.trimIndent())!!,
        0
    )!!

    // mirrors the overlap filter used by callers of generateOccurrencesUntil
    private fun List<Event.Occurrence>.overlapping(fromDate: LocalDate, toDate: LocalDate, timeZoneId: String): List<Event.Occurrence> {
        val zone = ZoneId.of(timeZoneId)
        val fromInstant = fromDate.atStartOfDay(zone).toInstant()
        val toEndInstant = toDate.plusDays(1).atStartOfDay(zone).toInstant()
        return filter { occ ->
            val s = occ.startDateTime.toInstant()
            val e = occ.endDateTime.toInstant()
            e.isAfter(fromInstant) && s.isBefore(toEndInstant)
        }
    }

    private fun fmt(dt: LocalDateTime): String =
        "%04d%02d%02dT%02d%02d%02d".format(dt.year, dt.monthValue, dt.dayOfMonth, dt.hour, dt.minute, dt.second)

    // builds an equivalent timed event and asserts the arithmetic DAILY generator produces exactly the same
    // in-window occurrences (start, end AND DTSTART-relative number) as biweekly's expansion
    private fun assertDailyMatchesOracle(
        dtStart: LocalDateTime,
        durationSec: Long,
        interval: Int,
        tzId: String,
        from: LocalDate,
        to: LocalDate,
        expectNonEmpty: Boolean,
    ) {
        val zone = ZoneId.of(tzId)
        val rrule = if (interval == 1) "FREQ=DAILY" else "FREQ=DAILY;INTERVAL=$interval"
        val event = recurringEvent(
            """
            BEGIN:VCALENDAR
            VERSION:2.0
            PRODID:-//Proton//AndroidCalendar//EN
            BEGIN:VEVENT
            DTSTAMP:20200101T090000Z
            DTSTART;TZID=$tzId:${fmt(dtStart)}
            DTEND;TZID=$tzId:${fmt(dtStart.plusSeconds(durationSec))}
            RRULE:$rrule
            UID:daily-arith@proton.me
            END:VEVENT
            END:VCALENDAR
            """
        )
        val winStart = from.atStartOfDay(zone).toInstant()
        val winEnd = to.plusDays(1).atStartOfDay(zone).toInstant()
        val oracle = event.generateOccurrencesUntil(to, tzId)!!
            .overlapping(from, to, tzId)
            .map { Triple(it.startDateTime.toInstant(), it.endDateTime.toInstant(), it.occurrenceNumber) }
        val arithmetic = FastOccurrenceGenerator.dailyOccurrencesInWindow(
            dtStartInstant = dtStart.atZone(zone).toInstant(),
            durationSeconds = durationSec,
            intervalDays = interval,
            expansionZone = zone,
            windowStartInstant = winStart,
            windowEndInstant = winEnd,
        )
            .filter { it.end.isAfter(winStart) && it.start.isBefore(winEnd) }
            .map { Triple(it.start, it.end, it.number) }
        assertThat(arithmetic).isEqualTo(oracle)
        if (expectNonEmpty) assertThat(oracle.isNotEmpty()).isTrue()
    }

    // same as above for the arithmetic WEEKLY;BYDAY generator; dtStartDate must fall on a BYDAY weekday
    private fun assertWeeklyMatchesOracle(
        dtStartDate: LocalDate,
        time: LocalTime,
        durationSec: Long,
        byday: String,
        days: Set<DayOfWeek>,
        interval: Int,
        wkst: String?,
        wkstDay: DayOfWeek,
        tzId: String,
        from: LocalDate,
        to: LocalDate,
        expectNonEmpty: Boolean,
    ) {
        val zone = ZoneId.of(tzId)
        val dt = LocalDateTime.of(dtStartDate, time)
        val intervalPart = if (interval == 1) "" else ";INTERVAL=$interval"
        val wkstPart = wkst?.let { ";WKST=$it" } ?: ""
        val rrule = "FREQ=WEEKLY$intervalPart;BYDAY=$byday$wkstPart"
        val event = recurringEvent(
            """
            BEGIN:VCALENDAR
            VERSION:2.0
            PRODID:-//Proton//AndroidCalendar//EN
            BEGIN:VEVENT
            DTSTAMP:20240101T090000Z
            DTSTART;TZID=$tzId:${fmt(dt)}
            DTEND;TZID=$tzId:${fmt(dt.plusSeconds(durationSec))}
            RRULE:$rrule
            UID:weekly-arith@proton.me
            END:VEVENT
            END:VCALENDAR
            """
        )
        val winStart = from.atStartOfDay(zone).toInstant()
        val winEnd = to.plusDays(1).atStartOfDay(zone).toInstant()
        val oracle = event.generateOccurrencesUntil(to, tzId)!!
            .overlapping(from, to, tzId)
            .map { Triple(it.startDateTime.toInstant(), it.endDateTime.toInstant(), it.occurrenceNumber) }
        val arithmetic = FastOccurrenceGenerator.weeklyOccurrencesInWindow(
            dtStartInstant = dt.atZone(zone).toInstant(),
            durationSeconds = durationSec,
            intervalWeeks = interval,
            byDays = days,
            weekStart = wkstDay,
            expansionZone = zone,
            windowStartInstant = winStart,
            windowEndInstant = winEnd,
        )!!
            .filter { it.end.isAfter(winStart) && it.start.isBefore(winEnd) }
            .map { Triple(it.start, it.end, it.number) }
        assertThat(arithmetic).isEqualTo(oracle)
        if (expectNonEmpty) assertThat(oracle.isNotEmpty()).isTrue()
    }

    @Test
    fun `arithmetic daily generator matches biweekly across intervals, timezones, DST and boundaries`() {
        val h = 3600L
        val base = LocalDateTime.of(2020, 1, 1, 9, 30)
        data class C(
            val label: String, val dtStart: LocalDateTime, val dur: Long, val interval: Int,
            val tz: String, val from: LocalDate, val to: LocalDate, val nonEmpty: Boolean = true,
        )
        listOf(
            C("interval 1, London", base, h, 1, "Europe/London", LocalDate.of(2026, 5, 25), LocalDate.of(2026, 6, 14)),
            C("interval 2", base, h, 2, "Europe/London", LocalDate.of(2026, 5, 25), LocalDate.of(2026, 6, 14)),
            C("interval 3", base, h, 3, "Europe/London", LocalDate.of(2026, 5, 25), LocalDate.of(2026, 6, 14)),
            C("interval 5", base, h, 5, "Europe/London", LocalDate.of(2026, 5, 25), LocalDate.of(2026, 6, 14)),
            C("interval 30, year window", base, h, 30, "Europe/London", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)),
            C("spring-forward window", base, h, 1, "Europe/London", LocalDate.of(2026, 3, 23), LocalDate.of(2026, 4, 5)),
            C("fall-back window", base, h, 1, "Europe/London", LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 28)),
            C("UTC (no DST)", base, h, 1, "UTC", LocalDate.of(2026, 5, 25), LocalDate.of(2026, 6, 14)),
            C("New York DST", base, h, 1, "America/New_York", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 15)),
            C("Sydney SH fall-back", base, h, 1, "Australia/Sydney", LocalDate.of(2026, 3, 30), LocalDate.of(2026, 4, 12)),
            C("Sydney SH spring-forward", base, h, 1, "Australia/Sydney", LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 11)),
            C("far-past DTSTART, large numbers", LocalDateTime.of(2010, 1, 1, 9, 30), h, 1, "Europe/London", LocalDate.of(2026, 5, 25), LocalDate.of(2026, 6, 14)),
            C("far-past DTSTART, interval 7", LocalDateTime.of(2010, 1, 1, 9, 30), h, 7, "Europe/London", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31)),
            C("midnight start, window opens on an occurrence", LocalDateTime.of(2020, 1, 1, 0, 0), h, 1, "Europe/London", LocalDate.of(2026, 5, 25), LocalDate.of(2026, 6, 14)),
            C("window entirely before DTSTART -> empty", LocalDateTime.of(2026, 6, 20, 9, 30), h, 1, "Europe/London", LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 10), nonEmpty = false),
        ).forEach { c ->
            assertDailyMatchesOracle(c.dtStart, c.dur, c.interval, c.tz, c.from, c.to, c.nonEmpty)
        }
    }

    @Test
    fun `arithmetic daily matches biweekly for multi-day durations and DST gap or overlap start times`() {
        // 50h-long daily occurrences: the one whose start is ~2 days before the window still ends inside it and must
        // be kept (the generator's backward walk must not stop too early), with the correct DTSTART-relative number
        assertDailyMatchesOracle(
            LocalDateTime.of(2020, 1, 1, 9, 30), 50 * 3600L, 1, "Europe/London",
            LocalDate.of(2026, 5, 25), LocalDate.of(2026, 6, 14), expectNonEmpty = true,
        )
        // 01:30 London start: the wall time lands in the spring-forward gap (29 Mar) / fall-back overlap (25 Oct)
        // equivalence must still hold with biweekly's own resolution of those instants
        assertDailyMatchesOracle(
            LocalDateTime.of(2020, 1, 1, 1, 30), 3600L, 1, "Europe/London",
            LocalDate.of(2026, 3, 23), LocalDate.of(2026, 4, 5), expectNonEmpty = true,
        )
        assertDailyMatchesOracle(
            LocalDateTime.of(2020, 1, 1, 1, 30), 3600L, 1, "Europe/London",
            LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 28), expectNonEmpty = true,
        )
    }

    @Test
    fun `arithmetic weekly generator matches biweekly across byday, wkst, intervals, timezones and DST`() {
        val mwf = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
        val weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
        val allDays = DayOfWeek.entries.toSet()
        val tuth = setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY)
        val t = LocalTime.of(9, 30)
        val h = 3600L
        val normal = LocalDate.of(2026, 5, 25) to LocalDate.of(2026, 6, 14)
        val spring = LocalDate.of(2026, 3, 23) to LocalDate.of(2026, 4, 5)
        val fall = LocalDate.of(2026, 10, 20) to LocalDate.of(2026, 10, 28)
        val quarter = LocalDate.of(2026, 1, 1) to LocalDate.of(2026, 3, 31)
        // DTSTART weekdays: 2024-01-01 Mon, -02 Tue, -03 Wed, -05 Fri, -06 Sat; 2018-01-01 Mon; 2015-01-06 Tue
        data class W(
            val label: String, val byday: String, val days: Set<DayOfWeek>, val interval: Int,
            val wkst: String?, val wkstDay: DayOfWeek, val dtStart: LocalDate,
            val tz: String, val window: Pair<LocalDate, LocalDate>, val nonEmpty: Boolean = true,
        )
        listOf(
            W("MO,WE,FR i1", "MO,WE,FR", mwf, 1, null, DayOfWeek.MONDAY, LocalDate.of(2024, 1, 1), "Europe/London", normal),
            W("weekdays i1", "MO,TU,WE,TH,FR", weekdays, 1, null, DayOfWeek.MONDAY, LocalDate.of(2024, 1, 3), "Europe/London", normal),
            W("all 7 days i1", "SU,MO,TU,WE,TH,FR,SA", allDays, 1, null, DayOfWeek.MONDAY, LocalDate.of(2024, 1, 1), "Europe/London", normal),
            W("SA,SU wkst SU i1", "SA,SU", setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), 1, "SU", DayOfWeek.SUNDAY, LocalDate.of(2024, 1, 6), "Europe/London", normal),
            W("MO,WE,FR i2", "MO,WE,FR", mwf, 2, null, DayOfWeek.MONDAY, LocalDate.of(2024, 1, 5), "Europe/London", quarter),
            W("MO,WE,FR i3 wkst WE", "MO,WE,FR", mwf, 3, "WE", DayOfWeek.WEDNESDAY, LocalDate.of(2024, 1, 1), "Europe/London", quarter),
            W("TU,TH i4 wkst SU", "TU,TH", tuth, 4, "SU", DayOfWeek.SUNDAY, LocalDate.of(2024, 1, 2), "Europe/London", quarter),
            W("WE single i1", "WE", setOf(DayOfWeek.WEDNESDAY), 1, null, DayOfWeek.MONDAY, LocalDate.of(2024, 1, 3), "Europe/London", normal),
            W("MO,WE,FR i1 spring-forward", "MO,WE,FR", mwf, 1, null, DayOfWeek.MONDAY, LocalDate.of(2024, 1, 1), "Europe/London", spring),
            W("MO,WE,FR i1 fall-back", "MO,WE,FR", mwf, 1, null, DayOfWeek.MONDAY, LocalDate.of(2024, 1, 1), "Europe/London", fall),
            W("MO,WE,FR i1 UTC", "MO,WE,FR", mwf, 1, null, DayOfWeek.MONDAY, LocalDate.of(2024, 1, 1), "UTC", normal),
            W("MO,WE,FR i1 Sydney SH fall-back", "MO,WE,FR", mwf, 1, null, DayOfWeek.MONDAY, LocalDate.of(2024, 1, 1), "Australia/Sydney", LocalDate.of(2026, 3, 30) to LocalDate.of(2026, 4, 12)),
            W("MO,WE,FR i1 Sydney SH spring", "MO,WE,FR", mwf, 1, null, DayOfWeek.MONDAY, LocalDate.of(2024, 1, 1), "Australia/Sydney", LocalDate.of(2026, 9, 28) to LocalDate.of(2026, 10, 11)),
            W("far-past DTSTART i3 large numbers", "MO,WE,FR", mwf, 3, null, DayOfWeek.MONDAY, LocalDate.of(2018, 1, 1), "Europe/London", quarter),
            W("far-past DTSTART i2", "TU,TH", tuth, 2, null, DayOfWeek.MONDAY, LocalDate.of(2015, 1, 6), "Europe/London", quarter),
            W("window before DTSTART -> empty", "MO,WE,FR", mwf, 1, null, DayOfWeek.MONDAY, LocalDate.of(2026, 6, 22), "Europe/London", LocalDate.of(2026, 5, 1) to LocalDate.of(2026, 5, 10), nonEmpty = false),
        ).forEach { c ->
            assertWeeklyMatchesOracle(
                c.dtStart, t, h, c.byday, c.days, c.interval, c.wkst, c.wkstDay, c.tz, c.window.first, c.window.second, c.nonEmpty,
            )
        }
    }

    @Test
    fun `arithmetic weekly matches biweekly for multi-day durations`() {
        // 26h occurrences: the Friday occurrence spills into Saturday, and an occurrence whose start is just before
        // the window still ends inside it. Guards the weekly week-jump's lookback for cross-day durations
        assertWeeklyMatchesOracle(
            LocalDate.of(2024, 1, 1), LocalTime.of(9, 30), 26 * 3600L,
            "MO,WE,FR", setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), 1, null, DayOfWeek.MONDAY,
            "Europe/London", LocalDate.of(2026, 5, 25), LocalDate.of(2026, 6, 14), expectNonEmpty = true,
        )
    }

    @Test
    fun `arithmetic weekly matches biweekly for fall-back ambiguous start time`() {
        // Sunday 01:30 in London lands in the fall-back ambiguous hour on 25 Oct 2026; the generator must resolve it
        // to the same instant as biweekly (later offset), not java.time's default earlier offset
        assertWeeklyMatchesOracle(
            LocalDate.of(2024, 1, 7), LocalTime.of(1, 30), 3600L, // 2024-01-07 is a Sunday
            "SU", setOf(DayOfWeek.SUNDAY), 1, null, DayOfWeek.MONDAY,
            "Europe/London", LocalDate.of(2026, 10, 18), LocalDate.of(2026, 11, 1), expectNonEmpty = true,
        )
    }

}
