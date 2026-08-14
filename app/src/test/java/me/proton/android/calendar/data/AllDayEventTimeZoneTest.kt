package me.proton.android.calendar.data

import assertk.assertThat
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import me.proton.android.calendar.common.utils.EventUtilsImpl.addExceptionDate
import me.proton.android.calendar.common.utils.EventUtilsImpl.generateOccurrencesUntil
import me.proton.android.calendar.common.utils.ICalUtilsImpl
import me.proton.android.calendar.common.utils.ICalUtilsImpl.filterOutEventOccurrencesByExdates
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.EventEntity
import me.proton.android.calendar.domain.model.Calendar
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.domain.usecase.TransformEventUseCase
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.TimeZone

/**
 * All-day events are floating: a device timezone change must not move them.
 * Bug report: "when changing time zone, all-day events show across two days instead of one."
 */
internal class AllDayEventTimeZoneTest {

    private val primaryTimeZoneId = "Europe/Helsinki"

    private val allDayIcsJuly7 = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Proton Technologies//AndroidCalendar//EN
    BEGIN:VEVENT
    DTSTART;VALUE=DATE:20200707
    DTEND;VALUE=DATE:20200708
    SUMMARY:All-day event on 7th July
    UID:proton-calendar-all-day-7-july
    DTSTAMP:20200706T155327Z
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

    private val recurringAllDayIcsJuly7 = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Proton Technologies//AndroidCalendar//EN
    BEGIN:VEVENT
    DTSTART;VALUE=DATE:20200707
    DTEND;VALUE=DATE:20200708
    RRULE:FREQ=DAILY
    SUMMARY:Daily all-day event from 7th July
    UID:proton-calendar-all-day-recurring
    DTSTAMP:20200706T155327Z
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

    private val calendar = Calendar("cal-id", "name", "email", "ownerEmail", "description", "color", 0, "addressId", "memberId", 1, true, 0, 127, 30, emptyList(), emptyList())

    private fun parse(ics: String) = Event.from("id", calendar, ICalUtilsImpl.parseICalString(ics)!!, 0)!!

    private val transformEventUseCase: TransformEventUseCase = mockk()
    private val database: AppDatabase = mockk()

    private val entity: EventEntity = mockk {
        every { id } returns "event-id"
        every { calendarId } returns "cal-id"
        every { modifyTime } returns 0L
    }

    private val decryptor = EventDecryptorImpl(transformEventUseCase, database, Json)

    private lateinit var originalTimeZone: TimeZone

    @BeforeEach
    fun setUp() {
        originalTimeZone = TimeZone.getDefault()
        // re-parses per call: date freezes at the current device tz
        coEvery { transformEventUseCase.execute(entity, any()) } answers {
            Event.from("event-id", calendar, ICalUtilsImpl.parseICalString(allDayIcsJuly7)!!, 0)
        }
    }

    @AfterEach
    fun tearDown() {
        TimeZone.setDefault(originalTimeZone)
    }

    @Test
    fun `all-day event stays on its single day across a device tz change, even from a stale cache`() = runTest {
        decryptor.setCalendars(listOf(calendar))

        val july7 = LocalDate.of(2020, 7, 7)

        // parsed and cached while the device is in Helsinki
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Helsinki"))
        val atHome = decryptor.decrypt(entity)!!
        assertThat(atHome.daysShown()).isEqualTo(listOf(july7))

        // device switches to Amsterdam; the cached (Helsinki-parsed) event still resolves to the same day
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Amsterdam"))
        val whileTravelling = decryptor.decrypt(entity)!!
        assertThat(whileTravelling.daysShown()).isEqualTo(listOf(july7))
    }

    @Test
    fun `all-day event resolves to the same day under any device timezone`() {
        val event = parse(allDayIcsJuly7)
        val deviceZones = listOf("UTC", "Europe/Helsinki", "Europe/Amsterdam", "America/Los_Angeles", "Pacific/Pago_Pago", "Pacific/Kiritimati")

        val starts = deviceZones.associateWith { zone ->
            TimeZone.setDefault(TimeZone.getTimeZone(zone))
            event.getStart(primaryTimeZoneId).toLocalDateTime()
        }
        val ends = deviceZones.associateWith { zone ->
            TimeZone.setDefault(TimeZone.getTimeZone(zone))
            event.getEnd(primaryTimeZoneId).toLocalDateTime()
        }

        assertThat(starts).isEqualTo(deviceZones.associateWith { LocalDateTime.of(2020, 7, 7, 0, 0) })
        assertThat(ends).isEqualTo(deviceZones.associateWith { LocalDateTime.of(2020, 7, 8, 0, 0) })
    }

    @Test
    fun `all-day event is at midnight on its date in whatever display timezone is requested`() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles")) // device intentionally differs from display
        val event = parse(allDayIcsJuly7)
        val displayZones = listOf("Europe/Helsinki", "UTC", "Pacific/Kiritimati", "America/New_York")

        val starts = displayZones.associateWith { event.getStart(it) }

        assertThat(starts).isEqualTo(displayZones.associateWith { ZonedDateTime.of(2020, 7, 7, 0, 0, 0, 0, ZoneId.of(it)) })
    }

    @Test
    fun `recurring all-day occurrences stay on their dates across a device tz change`() {
        val event = parse(recurringAllDayIcsJuly7)

        fun occurrenceDates() = event.generateOccurrencesUntil(LocalDate.of(2020, 7, 11), primaryTimeZoneId)!!
            .map { it.startDateTime.toLocalDate() }

        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Helsinki"))
        val atHome = occurrenceDates()

        TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
        val travelling = occurrenceDates()

        val expectedDaily = List(atHome.size) { LocalDate.of(2020, 7, 7).plusDays(it.toLong()) }
        assertThat(atHome).isEqualTo(expectedDaily)
        assertThat(travelling).isEqualTo(atHome)
    }

    @Test
    fun `deleted all-day occurrence stays excluded after a device tz change`() {
        val event = parse(recurringAllDayIcsJuly7)

        // delete the 3rd occurrence (July 9) while the device is in Helsinki
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Helsinki"))
        event.addExceptionDate(3, primaryTimeZoneId)

        fun visibleDates(): List<LocalDate> = event.generateOccurrencesUntil(LocalDate.of(2020, 7, 11), primaryTimeZoneId)!!
            .filterOutEventOccurrencesByExdates(event, primaryTimeZoneId)
            .map { it.startDateTime.toLocalDate() }

        assertThat(visibleDates()).doesNotContain(LocalDate.of(2020, 7, 9))

        // device tz changes while the exdate is live; the occurrence must stay excluded
        TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
        assertThat(visibleDates()).doesNotContain(LocalDate.of(2020, 7, 9))
    }

    private fun Event.daysShown(): List<LocalDate> {
        val start = getStart(primaryTimeZoneId)
        val end = getEnd(primaryTimeZoneId)
        val firstDay = start.toLocalDate()
        val lastDay = if (end.toLocalTime() == LocalTime.MIDNIGHT) end.toLocalDate().minusDays(1) else end.toLocalDate()
        return generateSequence(firstDay) { day -> if (day < lastDay) day.plusDays(1) else null }.toList()
    }
}
