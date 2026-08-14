package me.proton.android.calendar.common

import assertk.assertThat
import assertk.assertions.isEqualTo
import me.proton.android.calendar.common.utils.ICalUtilsImpl
import me.proton.android.calendar.common.utils.ICalUtilsImpl.printToString
import me.proton.android.calendar.common.utils.IcsSurgeryUtils
import me.proton.android.calendar.domain.model.Calendar
import me.proton.android.calendar.domain.model.Event
import org.junit.jupiter.api.Test
import java.time.LocalDate

/** Import surgery must give an all-day event with no DTEND an exclusive end one day later (a single day). */
internal class AllDayImportDateTest {

    private val calendar = Calendar("id", "name", "email", "ownerEmail", "description", "color", 0, "addressId", "memberId", 1, true, 0, 127, 30, emptyList(), emptyList())

    private val icsNoEnd = """
    BEGIN:VCALENDAR
    VERSION:2.0
    METHOD:PUBLISH
    BEGIN:VEVENT
    SUMMARY:all-day, no DTEND
    DTSTART;VALUE=DATE:20221024
    RRULE:FREQ=DAILY;INTERVAL=1
    DTSTAMP:20221024T113000Z
    SEQUENCE:0
    STATUS:CONFIRMED
    UID:all-day-no-end@proton.me
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

    @Test
    fun `imported all-day event with no end resolves to a single day and survives an ICS round-trip`() {
        val result = IcsSurgeryUtils.cleanIcs(icsNoEnd, timeZoneId = "Europe/Paris")
        check(result is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful)
        val iCal = result.iCalendar!!

        val event = Event.from("id", calendar, iCal, 0)!!
        assertThat(event.getStart("Europe/Paris").toLocalDate()).isEqualTo(LocalDate.of(2022, 10, 24))
        assertThat(event.getEnd("Europe/Paris").toLocalDate()).isEqualTo(LocalDate.of(2022, 10, 25))

        // display re-parses from stored ICS, so the surgery-built end must survive serialize + reparse
        val reparsed = Event.from("id", calendar, ICalUtilsImpl.parseICalString(iCal.printToString())!!, 0)!!
        assertThat(reparsed.getStart("Europe/Paris").toLocalDate()).isEqualTo(LocalDate.of(2022, 10, 24))
        assertThat(reparsed.getEnd("Europe/Paris").toLocalDate()).isEqualTo(LocalDate.of(2022, 10, 25))
    }
}
