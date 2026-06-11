package me.proton.android.calendar.domain.indicators

import biweekly.util.DayOfWeek as BiweeklyDayOfWeek
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import me.proton.android.calendar.common.MAX_CALENDAR_INDICATORS
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.db.EventOccurrencesDao
import me.proton.android.calendar.data.db.EventsDao
import me.proton.android.calendar.data.entity.EventOccurrenceEntity
import me.proton.android.calendar.data.entity.EventColorRow
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.model.Calendar
import me.proton.android.calendar.domain.model.UserInfo
import me.proton.android.calendar.domain.usecase.GetUserInfoUseCase
import me.proton.core.domain.entity.UserId
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MetadataIndicatorsCalculatorTest {

    private val occurrencesDao = mockk<EventOccurrencesDao>()
    private val eventsDao = mockk<EventsDao>()
    private val database = mockk<AppDatabase> {
        every { eventOccurrencesDao() } returns occurrencesDao
        every { eventsDao() } returns eventsDao
    }
    private val calendarsRepo = mockk<CalendarsRepository>()
    private val getUserInfo = mockk<GetUserInfoUseCase>()
    private val logger = mockk<Logger>(relaxed = true)

    private val userId = UserId("u1")
    private val tz = "Europe/Zurich"
    private val zone = ZoneId.of(tz)

    private val from = LocalDate.of(2025, 6, 2)
    private val to = LocalDate.of(2025, 6, 8)

    private val cal1 = calendar("c1", "#FF0000")
    private val cal2 = calendar("c2", "#00FF00")

    private val calculator: MetadataIndicatorsCalculator
        get() = MetadataIndicatorsCalculator(database, calendarsRepo, getUserInfo, logger)

    private fun stubBaseHappyPath(
        nonRecurring: List<EventOccurrenceEntity> = emptyList(),
        finiteRecurring: List<EventOccurrenceEntity> = emptyList(),
        infiniteRecurring: List<EventOccurrenceEntity> = emptyList(),
        calendars: List<Calendar> = listOf(cal1, cal2),
        hasSubscriptionForMail: Boolean = false,
        eventColorOverrides: Map<String, String?> = emptyMap(),
    ) {
        coEvery { calendarsRepo.selectAllCalendars(userId.id) } returns calendars
        every { getUserInfo() } returns flowOf(
            UserInfo(userId = userId, addresses = emptyList(), hasSubscriptionForMail = hasSubscriptionForMail)
        )
        every { occurrencesDao.selectNonRecurringBetweenInclusive(any(), any(), any(), any()) } returns
                flowOf(nonRecurring)
        every { occurrencesDao.selectFiniteRecurring(any(), any(), any(), any()) } returns
                flowOf(finiteRecurring)
        every { occurrencesDao.selectInfiniteRecurring(any(), any(), any()) } returns
                flowOf(infiniteRecurring)
        every { eventsDao.selectEventColorsById(any()) } returns eventColorOverrides.map { (id, color) ->
            EventColorRow(id = id, color = color)
        }
    }

    @Test
    fun `compute returns empty when no visible calendars`() = runTest {
        stubBaseHappyPath(calendars = emptyList())
        val result = calculator.compute(userId.id, from, to, tz)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `compute returns empty when no occurrences`() = runTest {
        stubBaseHappyPath()
        val result = calculator.compute(userId.id, from, to, tz)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `compute aggregates colors per day for non-recurring events`() = runTest {
        val day = from
        val rows = listOf(
            nonRecurring("e1", "u1", "c1", day.atStartOfDay(zone).plusHours(9), day.atStartOfDay(zone).plusHours(10)),
            nonRecurring("e2", "u2", "c2", day.atStartOfDay(zone).plusHours(11), day.atStartOfDay(zone).plusHours(12)),
        )
        stubBaseHappyPath(nonRecurring = rows)

        val result = calculator.compute(userId.id, from, to, tz)

        assertEquals(setOf(day), result.keys)
        assertEquals(listOf("#00FF00", "#FF0000"), result[day])
    }

    @Test
    fun `compute spans multi-day timed event across days`() = runTest {
        val start = from.atStartOfDay(zone).plusHours(10)
        val end = from.plusDays(2).atStartOfDay(zone).plusHours(14)
        stubBaseHappyPath(nonRecurring = listOf(nonRecurring("e1", "u1", "c1", start, end)))

        val result = calculator.compute(userId.id, from, to, tz)

        assertEquals(setOf(from, from.plusDays(1), from.plusDays(2)), result.keys)
        result.values.forEach { assertEquals(listOf("#FF0000"), it) }
    }

    @Test
    fun `compute does not span end-day for part-time event ending at midnight`() = runTest {
        val start = from.atStartOfDay(zone).plusHours(20)
        val end = from.plusDays(1).atStartOfDay(zone) // exactly midnight
        stubBaseHappyPath(nonRecurring = listOf(nonRecurring("e1", "u1", "c1", start, end)))

        val result = calculator.compute(userId.id, from, to, tz)

        assertEquals(setOf(from), result.keys)
    }

    @Test
    fun `compute spans single all-day event on its date only`() = runTest {
        val start = from.atStartOfDay(zone)
        val end = from.plusDays(1).atStartOfDay(zone)
        stubBaseHappyPath(nonRecurring = listOf(nonRecurring("e1", "u1", "c1", start, end, fullDay = true)))

        val result = calculator.compute(userId.id, from, to, tz)

        assertEquals(setOf(from), result.keys)
    }

    @Test
    fun `compute caps indicators per day at MAX_CALENDAR_INDICATORS and sorts`() = runTest {
        val day = from
        val rows = (1..MAX_CALENDAR_INDICATORS + 3).map { i ->
            nonRecurring(
                eventId = "e$i",
                uid = "u$i",
                calendarId = "c1",
                start = day.atStartOfDay(zone).plusHours(i.toLong()),
                end = day.atStartOfDay(zone).plusHours(i + 1L),
            )
        }
        stubBaseHappyPath(nonRecurring = rows)

        val colors = calculator.compute(userId.id, from, to, tz)[day]!!

        assertEquals(MAX_CALENDAR_INDICATORS, colors.size)
        assertEquals(colors.sorted(), colors)
    }

    @Test
    fun `compute drops infinite-recurring whose first occurrence is after window`() = runTest {
        val toEnd = to.plusDays(1).atStartOfDay(zone).toEpochSecond()
        val occ = nonRecurring(
            eventId = "eInf",
            uid = "uInf",
            calendarId = "c1",
            start = from.atStartOfDay(zone),
            end = from.atStartOfDay(zone).plusHours(1),
        ).copy(
            // Window covers our request; first occurrence falls one hour AFTER toEnd.
            windowStartTime = from.atStartOfDay(zone).toEpochSecond() - 86_400,
            windowEndTime = toEnd + 86_400,
            startTime = toEnd + 3_600,
        )
        stubBaseHappyPath(infiniteRecurring = listOf(occ))

        val result = calculator.compute(userId.id, from, to, tz)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `compute uses calendar color for free users even when event color is set`() = runTest {
        val start = from.atStartOfDay(zone).plusHours(9)
        val end = start.plusHours(1)
        stubBaseHappyPath(
            nonRecurring = listOf(nonRecurring("e1", "u1", "c1", start, end)),
            hasSubscriptionForMail = false,
            eventColorOverrides = mapOf("e1" to "#0000FF"),
        )

        val result = calculator.compute(userId.id, from, to, tz)

        assertEquals(listOf("#FF0000"), result[from])
    }

    @Test
    fun `compute applies per-event color override for paid users`() = runTest {
        val start = from.atStartOfDay(zone).plusHours(9)
        val end = start.plusHours(1)
        stubBaseHappyPath(
            nonRecurring = listOf(nonRecurring("e1", "u1", "c1", start, end)),
            hasSubscriptionForMail = true,
            eventColorOverrides = mapOf("e1" to "#0000FF"),
        )

        val result = calculator.compute(userId.id, from, to, tz)

        assertEquals(listOf("#0000FF"), result[from])
    }

    @Test
    fun `compute falls back to calendar color when paid user has no override row`() = runTest {
        val start = from.atStartOfDay(zone).plusHours(9)
        val end = start.plusHours(1)
        stubBaseHappyPath(
            nonRecurring = listOf(nonRecurring("e1", "u1", "c1", start, end)),
            hasSubscriptionForMail = true,
            eventColorOverrides = emptyMap(),
        )

        val result = calculator.compute(userId.id, from, to, tz)

        assertEquals(listOf("#FF0000"), result[from])
    }

    @Test
    fun `compute drops occurrences from invisible calendars`() = runTest {
        val start = from.atStartOfDay(zone).plusHours(9)
        val end = start.plusHours(1)
        stubBaseHappyPath(
            nonRecurring = listOf(nonRecurring("e1", "u1", "cHidden", start, end)),
            calendars = listOf(cal1), // cHidden not in visible set
        )

        val result = calculator.compute(userId.id, from, to, tz)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `compute expands daily RRULE producing one indicator per day`() = runTest {
        val tzId = "UTC"
        val zoneUtc = ZoneId.of(tzId)
        val start = LocalDate.of(2025, 6, 2).atStartOfDay(zoneUtc).plusHours(9)
        val end = start.plusHours(1)
        val rRule = "FREQ=DAILY;COUNT=3"

        val recurringRow = EventOccurrenceEntity(
            userId = userId.id,
            calendarId = "c1",
            eventId = "eD",
            eventUid = "uD",
            fullDay = 0,
            startTime = start.toEpochSecond(),
            endTime = end.toEpochSecond(),
            windowStartTime = start.toEpochSecond() - 86_400,
            windowEndTime = end.toEpochSecond() + 86_400 * 30,
            firstOccurrenceStartTime = start.toEpochSecond(),
            rRule = rRule,
            modifyTime = 1L,
            startTimeZone = tzId,
            endTimeZone = tzId,
        )
        stubBaseHappyPath(finiteRecurring = listOf(recurringRow))

        val result = calculator.compute(userId.id, from, to, tzId)

        assertEquals(
            setOf(
                LocalDate.of(2025, 6, 2),
                LocalDate.of(2025, 6, 3),
                LocalDate.of(2025, 6, 4),
            ),
            result.keys,
        )
    }

    @Test
    fun `compute expands infinite daily RRULE via arithmetic path with DTSTART-relative occurrence numbers`() = runTest {
        // DTSTART is 4 days before the window, so the first in-window occurrence is #5 (not #1); this guards the
        // arithmetic DAILY fast path AND that occurrenceNumber stays absolute/DTSTART-relative for tap-navigation
        val tzId = "UTC"
        val z = ZoneId.of(tzId)
        val dtStart = LocalDate.of(2025, 5, 29).atStartOfDay(z).plusHours(9) // 4 days before `from`
        stubBaseHappyPath(
            infiniteRecurring = listOf(recurring("eD", "uD", "c1", dtStart, "FREQ=DAILY", tzId)),
        )

        val events = calculator.computeSkeletonUiEvents(userId.id, from, to, tzId)

        // window is from(06-02)..to(06-08) inclusive -> 7 daily occurrences
        assertEquals(7, events.size)
        val numberByDate = events.associate { it.dateStart.toLocalDate() to it.occurrenceNumber }
        assertEquals(5, numberByDate[LocalDate.of(2025, 6, 2)])
        assertEquals(8, numberByDate[LocalDate.of(2025, 6, 5)])
        assertEquals(11, numberByDate[LocalDate.of(2025, 6, 8)])

        val indicators = calculator.compute(userId.id, from, to, tzId)
        assertEquals((2..8).map { LocalDate.of(2025, 6, it) }.toSet(), indicators.keys)
    }

    @Test
    fun `compute expands infinite weekly BYDAY RRULE via arithmetic path with DTSTART-relative numbers`() = runTest {
        // DTSTART Mon 2025-05-19; MO/WE/FR weekly; the window's Mon/Wed/Fri are occurrences #7/#8/#9
        val tzId = "UTC"
        val z = ZoneId.of(tzId)
        val dtStart = LocalDate.of(2025, 5, 19).atStartOfDay(z).plusHours(9) // a Monday, 2 weeks before window
        stubBaseHappyPath(
            infiniteRecurring = listOf(recurring("eW", "uW", "c1", dtStart, "FREQ=WEEKLY;BYDAY=MO,WE,FR", tzId)),
        )

        val events = calculator.computeSkeletonUiEvents(userId.id, from, to, tzId)

        assertEquals(3, events.size)
        val numberByDate = events.associate { it.dateStart.toLocalDate() to it.occurrenceNumber }
        assertEquals(7, numberByDate[LocalDate.of(2025, 6, 2)]) // Mon
        assertEquals(8, numberByDate[LocalDate.of(2025, 6, 4)]) // Wed
        assertEquals(9, numberByDate[LocalDate.of(2025, 6, 6)]) // Fri

        val indicators = calculator.compute(userId.id, from, to, tzId)
        assertEquals(
            setOf(LocalDate.of(2025, 6, 2), LocalDate.of(2025, 6, 4), LocalDate.of(2025, 6, 6)),
            indicators.keys,
        )
    }

    @Test
    fun `biweekly weekday names map onto java-time DayOfWeek`() {
        // expandRecurring converts each BYDAY via DayOfWeek.valueOf(biweeklyDay.name)
        BiweeklyDayOfWeek.values().forEach { day ->
            val converted = runCatching { DayOfWeek.valueOf(day.name) }.getOrNull()
            assertEquals(day.name, converted?.name)
        }
    }

    @Test
    fun `compute masks EXDATE occurrence in arithmetic daily expansion`() = runTest {
        val tzId = "UTC"
        val z = ZoneId.of(tzId)
        val dtStart = from.atStartOfDay(z).plusHours(9) // first occurrence on the window's first day
        val exDate = LocalDate.of(2025, 6, 4).atStartOfDay(z).plusHours(9).toEpochSecond()
        stubBaseHappyPath(
            infiniteRecurring = listOf(
                recurring("eD", "uD", "c1", dtStart, "FREQ=DAILY", tzId, exDates = listOf(exDate)),
            ),
        )

        val events = calculator.computeSkeletonUiEvents(userId.id, from, to, tzId)

        // 7 days in window minus the masked 06-04 = 6 occurrences
        assertEquals(6, events.size)
        assertTrue(events.none { it.dateStart.toLocalDate() == LocalDate.of(2025, 6, 4) })
        // numbering stays DTSTART-relative across the gap: 06-03 is #2, 06-05 is #4
        val numberByDate = events.associate { it.dateStart.toLocalDate() to it.occurrenceNumber }
        assertEquals(2, numberByDate[LocalDate.of(2025, 6, 3)])
        assertEquals(4, numberByDate[LocalDate.of(2025, 6, 5)])
    }

    @Test
    fun `computeSkeletonUiEvents emits one UiEvent per occurrence with metadata`() = runTest {
        val start = from.atStartOfDay(zone).plusHours(9)
        val end = start.plusHours(1)
        stubBaseHappyPath(
            nonRecurring = listOf(nonRecurring("e1", "u1", "c1", start, end)),
        )

        val events = calculator.computeSkeletonUiEvents(userId.id, from, to, tz)

        assertEquals(1, events.size)
        val ev = events.single()
        assertEquals("e1", ev.id)
        assertEquals("c1", ev.calendarId)
        assertEquals("u1", ev.uid)
        assertEquals("#FF0000", ev.displayColor)
        assertEquals(start.toInstant(), ev.dateStart.toInstant())
        assertEquals(end.toInstant(), ev.dateEnd.toInstant())
        assertEquals(false, ev.isAllDay)
        assertEquals(0, ev.occurrenceNumber)
    }

    private fun nonRecurring(
        eventId: String,
        uid: String,
        calendarId: String,
        start: java.time.ZonedDateTime,
        end: java.time.ZonedDateTime,
        fullDay: Boolean = false,
    ): EventOccurrenceEntity = EventOccurrenceEntity(
        userId = userId.id,
        calendarId = calendarId,
        eventId = eventId,
        eventUid = uid,
        fullDay = if (fullDay) 1 else 0,
        startTime = start.toEpochSecond(),
        endTime = end.toEpochSecond(),
        windowStartTime = start.toEpochSecond() - 86_400,
        windowEndTime = end.toEpochSecond() + 86_400,
        firstOccurrenceStartTime = start.toEpochSecond(),
        rRule = null,
        modifyTime = 1L,
    )

    private fun recurring(
        eventId: String,
        uid: String,
        calendarId: String,
        dtStart: java.time.ZonedDateTime,
        rRule: String,
        tzId: String,
        durationHours: Long = 1,
        exDates: List<Long> = emptyList(),
    ): EventOccurrenceEntity = EventOccurrenceEntity(
        userId = userId.id,
        calendarId = calendarId,
        eventId = eventId,
        eventUid = uid,
        fullDay = 0,
        startTime = dtStart.toEpochSecond(),
        endTime = dtStart.plusHours(durationHours).toEpochSecond(),
        // wide occurrence-window so the notOccurring filter keeps it (startTime is non-null and inside)
        windowStartTime = dtStart.toEpochSecond() - 86_400,
        windowEndTime = dtStart.toEpochSecond() + 86_400L * 365,
        firstOccurrenceStartTime = dtStart.toEpochSecond(),
        rRule = rRule,
        modifyTime = 1L,
        startTimeZone = tzId,
        endTimeZone = tzId,
        exDates = exDates,
    )

    private fun calendar(id: String, color: String) = Calendar(
        id = id,
        name = "",
        email = "",
        ownerEmail = null,
        description = "",
        color = color,
        priority = 0,
        addressId = "",
        memberId = "",
        flags = 1, // isActive
        display = true,
        type = 0,
        permissions = 0,
        defaultEventDuration = 0,
        defaultPartDayNotifications = emptyList(),
        defaultFullDayNotifications = emptyList(),
    )
}
