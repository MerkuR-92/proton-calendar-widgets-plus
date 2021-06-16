package me.proton.android.calendar.common

import assertk.assertThat
import assertk.assertions.*
import biweekly.Biweekly
import biweekly.util.ICalDate
import me.proton.android.calendar.common.IcsParsingValidation.DESCRIPTION_MAX_LENGTH
import me.proton.android.calendar.common.IcsParsingValidation.LOCATION_MAX_LENGTH
import me.proton.android.calendar.common.IcsParsingValidation.SUMMARY_MAX_LENGTH
import me.proton.android.calendar.common.IcsParsingValidation.UID_MAX_LENGTH
import me.proton.android.calendar.common.IcsSurgeryUtils.cleanAttendees
import me.proton.android.calendar.common.IcsSurgeryUtils.cleanCalscale
import me.proton.android.calendar.common.IcsSurgeryUtils.cleanRawIcs
import me.proton.android.calendar.common.IcsSurgeryUtils.cleanDescription
import me.proton.android.calendar.common.IcsSurgeryUtils.cleanDtEnd
import me.proton.android.calendar.common.IcsSurgeryUtils.cleanDtStart
import me.proton.android.calendar.common.IcsSurgeryUtils.cleanDuration
import me.proton.android.calendar.common.IcsSurgeryUtils.cleanExDate
import me.proton.android.calendar.common.IcsSurgeryUtils.cleanIcs
import me.proton.android.calendar.common.IcsSurgeryUtils.cleanLocation
import me.proton.android.calendar.common.IcsSurgeryUtils.cleanRRule
import me.proton.android.calendar.common.IcsSurgeryUtils.cleanRecurrenceId
import me.proton.android.calendar.common.IcsSurgeryUtils.cleanSequence
import me.proton.android.calendar.common.IcsSurgeryUtils.cleanSummary
import me.proton.android.calendar.common.IcsSurgeryUtils.cleanTimezones
import me.proton.android.calendar.common.IcsSurgeryUtils.cleanUid
import me.proton.android.calendar.common.IcsSurgeryUtils.cleanXWrTimezone
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*
import java.util.concurrent.TimeUnit


internal class IcsSurgeryUtilsTest {

    @Test
    fun `clean ics success test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    PRODID:-//Proton Technologies//ProtonCalendar 4.1.7//EN
    VERSION:2.0
    METHOD:REQUEST
    CALSCALE:GREGORIAN
    BEGIN:VEVENT
    SUMMARY:Single 02-03
    STATUS:CONFIRMED
    DTSTART;TZID=Europe/Paris:20210302T130000
    DTEND;TZID=Europe/Paris:20210302T133000
    ATTENDEE;CN=breakingcalendar+alias@pm.me;ROLE=REQ-PARTICIPANT;RSVP=TRUE;PAR
     TSTAT=NEEDS-ACTION;X-PM-TOKEN=1980a5594f21b76eb27cc969081cf2b85c14895b:mail
     to:breakingcalendar+alias@pm.me
    UID:gKdD4Slj5bSH31aLOhwGUengaLzr@proton.me
    ORGANIZER;CN=benjaminlovesdebugging@pm.me:mailto:benjaminlovesdebugging@pm.
     me
    SEQUENCE:0
    DTSTAMP:20210302T115550Z
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = IcsSurgeryUtils.cleanIcs(iCalString)

        assert(cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful)

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar

            assertThat(iCalendar).isNotNull()
        }
    }

    @Test
    fun `cleanRawIcs all day event with time test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    PRODID:-//Proton Technologies//ProtonCalendar 4.1.7//EN
    VERSION:2.0
    METHOD:REQUEST
    CALSCALE:GREGORIAN
    BEGIN:VEVENT
    SUMMARY:Single 02-03
    STATUS:CONFIRMED
    DTSTART;VALUE=DATE:20200101T120000Z
    UID:gKdD4Slj5bSH31aLOhwGUengaLzr@proton.me
    SEQUENCE:0
    DTSTAMP:20210302T115550Z
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanRawIcsResult = iCalString.cleanRawIcs()
        assert(cleanRawIcsResult is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful)
        if (cleanRawIcsResult !is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful) return
        val cleanICalString = cleanRawIcsResult.cleanICalString

        val iCalendar = Biweekly.parse(cleanICalString).first()
        assertThat(iCalendar).isNotNull()
        assertThat(iCalendar.events.first().dateStart.value.hasTime()).isFalse()
    }

    @Test
    fun `cleanRawIcs all day event with invalid format test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    PRODID:-//Proton Technologies//ProtonCalendar 4.1.7//EN
    VERSION:2.0
    METHOD:REQUEST
    CALSCALE:GREGORIAN
    BEGIN:VEVENT
    SUMMARY:Single 02-03
    STATUS:CONFIRMED
    DTSTART:20200101
    UID:gKdD4Slj5bSH31aLOhwGUengaLzr@proton.me
    SEQUENCE:0
    DTSTAMP:20210302T115550Z
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanICalString = iCalString.cleanRawIcs()

        assert(cleanICalString is IcsSurgeryUtils.HandleIcsResult.Error.Invalid.DateOrDateTimeProperty)
    }

    @Test
    fun `cleanRawIcs part day floating time event with no X-WR-TIMEZONE test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    PRODID:-//Proton Technologies//ProtonCalendar 4.1.7//EN
    VERSION:2.0
    METHOD:REQUEST
    CALSCALE:GREGORIAN
    BEGIN:VEVENT
    SUMMARY:Single 02-03
    STATUS:CONFIRMED
    DTSTART:20200101T120000
    UID:gKdD4Slj5bSH31aLOhwGUengaLzr@proton.me
    SEQUENCE:0
    DTSTAMP:20210302T115550Z
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanICalString = cleanIcs(iCalString)

        assert(cleanICalString is IcsSurgeryUtils.HandleIcsResult.Error.Invalid.DateOrDateTimeProperty)
    }

    @Test
    fun `cleanTimezones part day floating time event with X-WR-TIMEZONE test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    CALSCALE:GREGORIAN
    X-WR-TIMEZONE:Europe/Vilnius
    BEGIN:VEVENT
    DTSTAMP:20190719T130854Z
    CREATED:20200505T173304Z
    LAST-MODIFIED:20200505T173304Z
    UID:6c4ad96a-632f-4b24-bdee-f5e048f70a0c@proton.test
    DTSTART:20200102T133000
    SEQUENCE:0
    STATUS:CONFIRMED
    SUMMARY:API deploy
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanRawIcsResult = iCalString.cleanRawIcs()
        assert(cleanRawIcsResult is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful)
        if (cleanRawIcsResult !is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful) return
        val cleanICalString = cleanRawIcsResult.cleanICalString

        val iCalendar = Biweekly.parse(cleanICalString).first()
        assertThat(iCalendar).isNotNull()
        assertThat(iCalendar.cleanTimezones()).isTrue()
        iCalendar.events.forEach { event ->
            assertThat(event.cleanDtStart()).isTrue()
            assertThat(event.dateStart.value).isEqualTo(
                ICalDate.from(
                    ZonedDateTime.of(
                        2020, 1, 2, 13, 30, 0, 0, ZoneId.of(
                            "Europe/Vilnius"
                        )
                    ).toInstant()
                )
            )
            assertThat(iCalendar.timezoneInfo.getTimezone(event.dateStart).globalId).isEqualTo("Europe/Vilnius")
        }
    }

    @Test
    fun `cleanTimezones part day zulu time event with X-WR-TIMEZONE test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    CALSCALE:GREGORIAN
    X-WR-TIMEZONE:Europe/Vilnius
    BEGIN:VEVENT
    DTSTAMP:20190719T130854Z
    CREATED:20200505T173304Z
    LAST-MODIFIED:20200505T173304Z
    UID:6c4ad96a-632f-4b24-bdee-f5e048f70a0c@proton.test
    DTSTART:20200102T133000Z
    SEQUENCE:0
    STATUS:CONFIRMED
    SUMMARY:API deploy
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanRawIcsResult = iCalString.cleanRawIcs()
        assert(cleanRawIcsResult is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful)
        if (cleanRawIcsResult !is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful) return
        val cleanICalString = cleanRawIcsResult.cleanICalString

        val iCalendar = Biweekly.parse(cleanICalString).first()
        assertThat(iCalendar).isNotNull()
        assertThat(iCalendar.cleanTimezones()).isTrue()
        iCalendar.events.forEach { event ->
            assertThat(event.cleanDtStart()).isTrue()
            assertThat(event.dateStart.value).isEqualTo(
                ICalDate.from(
                    ZonedDateTime.of(
                        2020, 1, 2, 15, 30, 0, 0, ZoneId.of(
                            "Europe/Vilnius"
                        )
                    ).toInstant()
                )
            )
            assertThat(iCalendar.timezoneInfo.getTimezone(event.dateStart).globalId).isEqualTo("Europe/Vilnius")
        }
    }

    @Test
    fun `cleanTimezones non-supported timezone test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    CALSCALE:GREGORIAN
    BEGIN:VEVENT
    DTSTAMP:20190719T130854Z
    CREATED:20200505T173304Z
    LAST-MODIFIED:20200505T173304Z
    UID:6c4ad96a-632f-4b24-bdee-f5e048f70a0c@proton.test
    DTSTART;TZID=Europe/KazluRuda:20200102T133000Z
    SEQUENCE:0
    STATUS:CONFIRMED
    SUMMARY:API deploy
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanRawIcsResult = iCalString.cleanRawIcs()
        assert(cleanRawIcsResult is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful)
        if (cleanRawIcsResult !is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful) return
        val cleanICalString = cleanRawIcsResult.cleanICalString

        val iCalendar = Biweekly.parse(cleanICalString).first()
        assertThat(iCalendar).isNotNull()
        assertThat(iCalendar.cleanTimezones()).isFalse()
    }

    @Test
    fun `cleanTimezones convert to supported timezone test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    PRODID:-//Proton Technologies//ProtonCalendar 4.1.7//EN
    VERSION:2.0
    METHOD:REQUEST
    CALSCALE:GREGORIAN
    BEGIN:VEVENT
    SUMMARY:Single 02-03
    STATUS:CONFIRMED
    DTSTART;TZID=Europe/Bratislava:20210302T130000
    DTEND;TZID=Europe/Bratislava:20210302T133000
    UID:hwGUengaLzr@proton.me
    SEQUENCE:0
    DTSTAMP:20210302T115550Z
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanRawIcsResult = iCalString.cleanRawIcs()
        assert(cleanRawIcsResult is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful)
        if (cleanRawIcsResult !is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful) return
        val cleanICalString = cleanRawIcsResult.cleanICalString

        val iCalendar = Biweekly.parse(cleanICalString).first()
        assertThat(iCalendar).isNotNull()
        assertThat(iCalendar.cleanTimezones()).isTrue()
        assertThat(iCalendar.timezoneInfo.getTimezone(iCalendar.events.first().dateStart).timeZone.id).isEqualTo("Europe/Prague")
    }

    @Test
    fun `cleanVersion missing VERSION test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    PRODID:-//Proton Technologies//ProtonCalendar 4.1.7//EN
    METHOD:REQUEST
    CALSCALE:GREGORIAN
    BEGIN:VEVENT
    SUMMARY:Single 02-03
    STATUS:CONFIRMED
    DTSTART;TZID=Europe/Paris:20210302T130000
    DTEND;TZID=Europe/Paris:20210302T133000
    UID:gKdD4Slj5bSH31aLOhwGUengaLzr@proton.me
    SEQUENCE:0
    DTSTAMP:20210302T115550Z
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanRawIcsResult = iCalString.cleanRawIcs()

        assert(cleanRawIcsResult is IcsSurgeryUtils.HandleIcsResult.Error.Invalid.Version)
    }

    @Test
    fun `cleanVersion invalid value test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    PRODID:-//Proton Technologies//ProtonCalendar 4.1.7//EN
    VERSION:1.0
    METHOD:REQUEST
    CALSCALE:GREGORIAN
    BEGIN:VEVENT
    SUMMARY:Single 02-03
    STATUS:CONFIRMED
    DTSTART;TZID=Europe/Paris:20210302T130000
    DTEND;TZID=Europe/Paris:20210302T133000
    UID:gKdD4Slj5bSH31aLOhwGUengaLzr@proton.me
    SEQUENCE:0
    DTSTAMP:20210302T115550Z
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanRawIcsResult = iCalString.cleanRawIcs()

        assert(cleanRawIcsResult is IcsSurgeryUtils.HandleIcsResult.Error.Invalid.Version)
    }

    @Test
    fun `cleanCalscale missing CALSCALE test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    PRODID:-//Proton Technologies//ProtonCalendar 4.1.7//EN
    VERSION:2.0
    METHOD:REQUEST
    BEGIN:VEVENT
    SUMMARY:Single 02-03
    STATUS:CONFIRMED
    DTSTART;TZID=Europe/Paris:20210302T130000
    DTEND;TZID=Europe/Paris:20210302T133000
    UID:gKdD4Slj5bSH31aLOhwGUengaLzr@proton.me
    SEQUENCE:0
    DTSTAMP:20210302T115550Z
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanRawIcsResult = iCalString.cleanRawIcs()
        assert(cleanRawIcsResult is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful)
        if (cleanRawIcsResult !is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful) return
        val cleanICalString = cleanRawIcsResult.cleanICalString

        val iCalendar = Biweekly.parse(cleanICalString).first()
        assertThat(iCalendar).isNotNull()
        assertThat(iCalendar.cleanCalscale()).isTrue()
    }

    @Test
    fun `cleanCalscale invalid value test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    PRODID:-//Proton Technologies//ProtonCalendar 4.1.7//EN
    VERSION:2.0
    METHOD:REQUEST
    CALSCALE:JULIAN
    BEGIN:VEVENT
    SUMMARY:Single 02-03
    STATUS:CONFIRMED
    DTSTART;TZID=Europe/Paris:20210302T130000
    DTEND;TZID=Europe/Paris:20210302T133000
    UID:gKdD4Slj5bSH31aLOhwGUengaLzr@proton.me
    SEQUENCE:0
    DTSTAMP:20210302T115550Z
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanRawIcsResult = iCalString.cleanRawIcs()
        assert(cleanRawIcsResult is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful)
        if (cleanRawIcsResult !is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful) return
        val cleanICalString = cleanRawIcsResult.cleanICalString

        val iCalendar = Biweekly.parse(cleanICalString).first()
        assertThat(iCalendar).isNotNull()
        assertThat(iCalendar.cleanCalscale()).isFalse()
    }

    @Test
    fun `cleanXWrTimezone unsupported X-WR-TIMEZONE test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    PRODID:-//Proton Technologies//ProtonCalendar 4.1.7//EN
    VERSION:2.0
    METHOD:REQUEST
    CALSCALE:GREGORIAN
    X-WR-TIMEZONE:Europe/Bratislava
    BEGIN:VEVENT
    SUMMARY:Single 02-03
    STATUS:CONFIRMED
    DTSTART;TZID=Europe/Paris:20210302T130000
    DTEND;TZID=Europe/Paris:20210302T133000
    UID:hwGUengaLzr@proton.me
    SEQUENCE:0
    DTSTAMP:20210302T115550Z
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanRawIcsResult = iCalString.cleanRawIcs()
        assert(cleanRawIcsResult is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful)
        if (cleanRawIcsResult !is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful) return
        val cleanICalString = cleanRawIcsResult.cleanICalString

        val iCalendar = Biweekly.parse(cleanICalString).first()
        assertThat(iCalendar).isNotNull()
        assertThat(iCalendar.cleanXWrTimezone()).isTrue()
        assertThat(iCalendar.getExperimentalProperty("X-WR-TIMEZONE").value).isEqualTo("Europe/Prague")
    }

    @Test
    fun `cleanXWrTimezone invalid X-WR-TIMEZONE test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    PRODID:-//Proton Technologies//ProtonCalendar 4.1.7//EN
    VERSION:2.0
    METHOD:REQUEST
    CALSCALE:GREGORIAN
    X-WR-TIMEZONE:InvalidTimezoneId
    BEGIN:VEVENT
    SUMMARY:Single 02-03
    STATUS:CONFIRMED
    DTSTART;TZID=Europe/Paris:20210302T130000
    DTEND;TZID=Europe/Paris:20210302T133000
    UID:hwGUengaLzr@proton.me
    SEQUENCE:0
    DTSTAMP:20210302T115550Z
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanRawIcsResult = iCalString.cleanRawIcs()
        assert(cleanRawIcsResult is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful)
        if (cleanRawIcsResult !is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful) return
        val cleanICalString = cleanRawIcsResult.cleanICalString

        val iCalendar = Biweekly.parse(cleanICalString).first()
        assertThat(iCalendar).isNotNull()
        assertThat(iCalendar.cleanXWrTimezone()).isTrue()
        assertThat(iCalendar.getExperimentalProperty("X-WR-TIMEZONE")).isNull()
    }

    @Test
    fun `cleanUid too long test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    PRODID:-//Proton Technologies//ProtonCalendar 4.1.7//EN
    VERSION:2.0
    METHOD:REQUEST
    CALSCALE:GREGORIAN
    BEGIN:VEVENT
    SUMMARY:Single 02-03
    STATUS:CONFIRMED
    DTSTART;TZID=Europe/Paris:20210302T130000
    DTEND;TZID=Europe/Paris:20210302T133000
    UID:gKdD4Slj5bSH31aLOhwGUegKdD4Slj5bSH31aLOhwGUengaLzrngaLzrgKdD4Slj5bSH31aLOhwGUengaLzrgKdD4Slj5bSH31aLOhwGUengaLzrgKdD4Slj5bSH31aLOhgKdD4Slj5bSH31aLOhwGUengaLzrwGUengaLzrgKdD4Slj5bSH31aLOhwGUengaLzr@proton.me
    SEQUENCE:0
    DTSTAMP:20210302T115550Z
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanRawIcsResult = iCalString.cleanRawIcs()
        assert(cleanRawIcsResult is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful)
        if (cleanRawIcsResult !is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful) return
        val cleanICalString = cleanRawIcsResult.cleanICalString

        val iCalendar = Biweekly.parse(cleanICalString).first()
        assertThat(iCalendar).isNotNull()
        iCalendar.events.forEach { event ->
            assertThat(event.cleanUid()).isTrue()
            assertThat(event.uid.value.length).isEqualTo(UID_MAX_LENGTH)
        }
    }

    @Test
    fun `cleanUid missing UID test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    PRODID:-//Proton Technologies//ProtonCalendar 4.1.7//EN
    VERSION:2.0
    METHOD:REQUEST
    CALSCALE:GREGORIAN
    BEGIN:VEVENT
    SUMMARY:Single 02-03
    STATUS:CONFIRMED
    DTSTART;TZID=Europe/Paris:20210302T130000
    DTEND;TZID=Europe/Paris:20210302T133000
    SEQUENCE:0
    DTSTAMP:20210302T115550Z
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanRawIcsResult = iCalString.cleanRawIcs()
        assert(cleanRawIcsResult is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful)
        if (cleanRawIcsResult !is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful) return
        val cleanICalString = cleanRawIcsResult.cleanICalString

        val iCalendar = Biweekly.parse(cleanICalString).first()
        assertThat(iCalendar).isNotNull()
        iCalendar.events.forEach { event ->
            assertThat(event.cleanUid()).isFalse()
        }
    }

    @Test
    fun `cleanDateProperties fail test`() {

        // TODO

        val iCalString = """
    
    """.trimIndent()

//        val cleanRawIcsResult = iCalString.cleanRawIcs()
//        assert(cleanRawIcsResult is IcsSurgeryUtils.IcsParsingResult.RawParsingSuccessful)
//        if (cleanRawIcsResult !is IcsSurgeryUtils.IcsParsingResult.RawParsingSuccessful) return
//        val cleanICalString = cleanRawIcsResult.cleanICalString

//        val iCalendar = Biweekly.parse(cleanICalString).first()
//        assertThat(iCalendar).isNotNull()
//        iCalendar.events.forEach { event ->
//            assertThat(event.cleanDateProperties(iCalString)).isFalse()
//        }
    }

    @Test
    fun `cleanDtStart missing DTSTART test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    CALSCALE:GREGORIAN
    BEGIN:VEVENT
    DTSTAMP:20190719T130854Z
    CREATED:20200505T173304Z
    LAST-MODIFIED:20200505T173304Z
    UID:6c4ad96a-632f-4b24-bdee-f5e048f70a0c@proton.test
    SEQUENCE:0
    STATUS:CONFIRMED
    SUMMARY:API deploy
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanRawIcsResult = iCalString.cleanRawIcs()
        assert(cleanRawIcsResult is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful)
        if (cleanRawIcsResult !is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful) return
        val cleanICalString = cleanRawIcsResult.cleanICalString

        val iCalendar = Biweekly.parse(cleanICalString).first()
        assertThat(iCalendar).isNotNull()
        iCalendar.events.forEach { event ->
            assertThat(event.cleanDtStart()).isFalse()
        }
    }

    @Test
    fun `cleanDtStart value before MIN_DATE test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    CALSCALE:GREGORIAN
    BEGIN:VEVENT
    DTSTAMP:20190719T130854Z
    CREATED:20200505T173304Z
    LAST-MODIFIED:20200505T173304Z
    UID:6c4ad96a-632f-4b24-bdee-f5e048f70a0c@proton.test
    DTSTART;VALUE=DATE:19681212
    SEQUENCE:0
    STATUS:CONFIRMED
    SUMMARY:API deploy
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanRawIcsResult = iCalString.cleanRawIcs()
        assert(cleanRawIcsResult is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful)
        if (cleanRawIcsResult !is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful) return
        val cleanICalString = cleanRawIcsResult.cleanICalString

        val iCalendar = Biweekly.parse(cleanICalString).first()
        assertThat(iCalendar).isNotNull()
        iCalendar.events.forEach { event ->
            assertThat(event.cleanDtStart()).isFalse()
        }
    }

    @Test
    fun `cleanDtStart value after MAX_DATE test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    CALSCALE:GREGORIAN
    BEGIN:VEVENT
    DTSTAMP:20190719T130854Z
    CREATED:20200505T173304Z
    LAST-MODIFIED:20200505T173304Z
    UID:6c4ad96a-632f-4b24-bdee-f5e048f70a0c@proton.test
    DTSTART;VALUE=DATE:20380102
    SEQUENCE:0
    STATUS:CONFIRMED
    SUMMARY:API deploy
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanRawIcsResult = iCalString.cleanRawIcs()
        assert(cleanRawIcsResult is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful)
        if (cleanRawIcsResult !is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful) return
        val cleanICalString = cleanRawIcsResult.cleanICalString

        val iCalendar = Biweekly.parse(cleanICalString).first()
        assertThat(iCalendar).isNotNull()
        iCalendar.events.forEach { event ->
            assertThat(event.cleanDtStart()).isFalse()
        }
    }

    @Test
    fun `cleanDtStart different value type for DTSTART and DTEND test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    CALSCALE:GREGORIAN
    BEGIN:VEVENT
    DTSTAMP:20190719T130854Z
    CREATED:20200505T173304Z
    LAST-MODIFIED:20200505T173304Z
    UID:6c4ad96a-632f-4b24-bdee-f5e048f70a0c@proton.test
    DTSTART;VALUE=DATE:20200102
    DTEND;TZID=Europe/Paris:20210302T133000
    SEQUENCE:0
    STATUS:CONFIRMED
    SUMMARY:API deploy
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanRawIcsResult = iCalString.cleanRawIcs()
        assert(cleanRawIcsResult is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful)
        if (cleanRawIcsResult !is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful) return
        val cleanICalString = cleanRawIcsResult.cleanICalString

        val iCalendar = Biweekly.parse(cleanICalString).first()
        assertThat(iCalendar).isNotNull()
        iCalendar.events.forEach { event ->
            assertThat(event.cleanDtStart()).isFalse()
        }
    }

    @Test
    fun `cleanDuration with DURATION and no DTEND test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    CALSCALE:GREGORIAN
    BEGIN:VEVENT
    DTSTAMP:20190719T130854Z
    CREATED:20200505T173304Z
    LAST-MODIFIED:20200505T173304Z
    UID:6c4ad96a-632f-4b24-bdee-f5e048f70a0c@proton.test
    DTSTART;VALUE=DATE:20380102
    DURATION:PT15M
    SEQUENCE:0
    STATUS:CONFIRMED
    SUMMARY:API deploy
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanRawIcsResult = iCalString.cleanRawIcs()
        assert(cleanRawIcsResult is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful)
        if (cleanRawIcsResult !is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful) return
        val cleanICalString = cleanRawIcsResult.cleanICalString

        val iCalendar = Biweekly.parse(cleanICalString).first()
        assertThat(iCalendar).isNotNull()
        iCalendar.events.forEach { event ->
            assertThat(event.cleanDuration()).isFalse()
        }
    }

    @Test
    fun `cleanDtEnd DTEND value before DTSTART test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    CALSCALE:GREGORIAN
    BEGIN:VEVENT
    DTSTAMP:20190719T130854Z
    CREATED:20200505T173304Z
    LAST-MODIFIED:20200505T173304Z
    UID:6c4ad96a-632f-4b24-bdee-f5e048f70a0c@proton.test
    DTSTART;VALUE=DATE:20210102
    DTEND;VALUE=DATE:20210101
    SEQUENCE:0
    STATUS:CONFIRMED
    SUMMARY:API deploy
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanRawIcsResult = iCalString.cleanRawIcs()
        assert(cleanRawIcsResult is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful)
        if (cleanRawIcsResult !is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful) return
        val cleanICalString = cleanRawIcsResult.cleanICalString

        val iCalendar = Biweekly.parse(cleanICalString).first()
        assertThat(iCalendar).isNotNull()
        iCalendar.events.forEach { event ->
            assertThat(event.cleanDtEnd()).isTrue()
        }
    }

    @Test
    fun `cleanDtEnd no DTEND partial day test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    CALSCALE:GREGORIAN
    BEGIN:VEVENT
    DTSTAMP:20190719T130854Z
    CREATED:20200505T173304Z
    LAST-MODIFIED:20200505T173304Z
    UID:6c4ad96a-632f-4b24-bdee-f5e048f70a0c@proton.test
    DTSTART;TZID=Europe/Paris:20210302T130000
    SEQUENCE:0
    STATUS:CONFIRMED
    SUMMARY:API deploy
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanRawIcsResult = iCalString.cleanRawIcs()
        assert(cleanRawIcsResult is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful)
        if (cleanRawIcsResult !is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful) return
        val cleanICalString = cleanRawIcsResult.cleanICalString

        val iCalendar = Biweekly.parse(cleanICalString).first()
        assertThat(iCalendar).isNotNull()
        iCalendar.events.forEach { event ->
            assertThat(event.cleanDtEnd()).isTrue()
            assertThat(event.dateEnd.value).isEqualTo(event.dateStart.value)
        }
    }

    @Test
    fun `cleanDtEnd no DTEND full day test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    CALSCALE:GREGORIAN
    BEGIN:VEVENT
    DTSTAMP:20190719T130854Z
    CREATED:20200505T173304Z
    LAST-MODIFIED:20200505T173304Z
    UID:6c4ad96a-632f-4b24-bdee-f5e048f70a0c@proton.test
    DTSTART;VALUE=DATE:20210102
    SEQUENCE:0
    STATUS:CONFIRMED
    SUMMARY:API deploy
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanRawIcsResult = iCalString.cleanRawIcs()
        assert(cleanRawIcsResult is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful)
        if (cleanRawIcsResult !is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful) return
        val cleanICalString = cleanRawIcsResult.cleanICalString

        val iCalendar = Biweekly.parse(cleanICalString).first()
        assertThat(iCalendar).isNotNull()
        iCalendar.events.forEach { event ->
            assertThat(event.cleanDtEnd()).isTrue()
            assertThat(event.dateEnd.value.time).isEqualTo(event.dateStart.value.time + TimeUnit.DAYS.toMillis(1))
        }
    }

    @Test
    fun `cleanDescription DESCRIPTION too long test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    CALSCALE:GREGORIAN
    BEGIN:VEVENT
    DTSTAMP:20190719T130854Z
    CREATED:20200505T173304Z
    LAST-MODIFIED:20200505T173304Z
    UID:6c4ad96a-632f-4b24-bdee-f5e048f70a0c@proton.test
    DTSTART;VALUE=DATE:20200506
    DESCRIPTION:iWJZBZJzu8qZpa1EwfN43fFepSCfklnua9L1xDqJFneRBF4cChhWTTOBYt1Es8mx5wiRa4izKeheC9BAlAw1ss1EJpEHEpRxUyeNTdi5jneUqa3LhfCechW1dz2Ar44orITzw1hLOQ2p0ergwPe9rQch2OzdwpcWFXkKDb9gk45lccy0uz8mqCpG5yYQIBmSkW3mO7SrgtrCM7fKVdDKekEFfJzS4IMvyeckdiwlo6RyQoLTNMKqAuw6IfYzPAKbKj3TiN0iJEHCb6KmzSmCafT8iGI5UydvWZU8dHkCdViITZ7f8G3Ns47fGJ7giv9oZR902LZXVRU0uyThpjGwGy5GHLRTuKL4TF1IkKJk6Wvh3NT9dfsiY0P3uhXE26OtZmImgCCld255clSOGtPe3dWX8hrTrnX0KIAGLxHB0jOGDI6Av0QeRCAiNY12Bh9OVCbP0OU27ZBIXffq6LRHI8VCj4tNs95PDeaBU39E8wfbn55niiCSwuxWHF8DxPnIL0EUOhZoU2FiqGc58yXLGqRBZpKRk2oiBwQbd7HoF7pfNhMF902jBkO2iGF4Gh3DUTzxy5Nc7O2UyH2XxSiggSNYzkPAWZyecitvxQ1KQtyhEjfiNkQZgCxGUAvkyQGJ9ZJaKUujG4Nbpc9z3xhnMqsXRnEYxJvopurfwodM0i2AYGpfKtJaFOy4GINDhbozC9IWZxmYxVsEPbhv3lnQP07iNEh1YKsVaNr6EPOtrNjUNSG9euEkfJZni6TX4i8ggEsGEco8hgphrBBrS2JhkWrDWH2f5CIgfgmkGMumqEA8WLNyeo30eZKPGeW58S0UljxNckLlI9agxdevmdhjCZxOlv51awUw4AhSn0FkvtMuPKvd8leFf5tma8UbVZvnw5w8wcFYlFsQN56qGbH2U7bpYs1QH2JMWp92f2KYVBreyv4UbbUmr1Ct3A3oqrl5QPnlrW55FZPLhAauk5fjvMTXdkICfRMeeoeEBJkBxYhveUXCuIaSkiD3TQrwqdAMFzw0OtKbYcTrEhxg6VfASDboGUzAG6N9SHYWCl7Xmwff1Ow4oY57tiHB4TZk8J5KaMKp6OqIqxRcOFUJ1aIZ5INS8gB6G08vU7ypygsET6tP1A67e3NCWUcpW3JH7yNlVIKoiOKZutnAhM6qEzHdp4e1w9tZzXb100HfAQB82phfQBAqiRkIYLOJVLEj2YnJN7Xf3v87FKHznPFQfbaUBrKAz3EY8Rzj8QTF9d6GofCivv9Orliy5pc9fh3x8dbyfjKSZpPGhygojz31dAWoDvrz7mNcB2v8YI4whvcpRXIO3uUlLSGBdkkbMXESu7IjPdWsnMSeGz09ALrAJigWuHmmDmY1d3XXNNrfcb5X53ym6wGJLARkKCt6FnvP8B2PcXkMyIToImAOfGyDruRYSeMVBudxiEdJfaVqxA2iHxeY6Tw4D78QTN81rAJGTsTMkO5Ow4ybj1m9FUO3nHP27LQGfczOzqmVs8qgqRpYy2IsCdnpRFQLStahSTFTfKv4loh1UJqM5V7esn6fmzhNx8omsvTtawyka5oKcK5VBPnygSN02sBEf2kVkDomxW9I3eFV9XYpceRETGdUH9MMBHEYYxzAnEVMDvVL78D0AoREIpQMUFPiWd7lGc9nJiZW21KMcCbdeTUuU9Y5eoZPrJ9I3szCcej2uPt8l0TmWo4HMxjgVtiMz2ywMalPcVdWkpLmeoCv3xqTzT0rRBfVoAgETuI0RSORHELMQ73FJ9mYDI4YHRWOmivRkRvijf0V2oRJDM2kJ8QbYzyFqRgohRWwx58anTLXNsZHIYrm7K7KXp7Znf6JFccerRW7Oq0UaCoT6mTwj9wSXpQuBw07H8AFxd5be2nDFvF29UMqcZvjPxkbIkaoRe2ceW7gILrBcqbwwOm82fvyHDuhn0utVJpUL5Dwlf6TVn5W2cy5bOShkGVttXQ45QtNYpcgq7Jf65EKhse3AxRa8J3FkfwJa4UoHqlhNsLTTtoZKf0i8iXBrySWUzeQmAoXrQ5LQDXD64Tfjofc52UdGAiucG5PJgKLECbeykR3f8Laz3IjJ5ES8hXcqCgdVkFYz5hm9F5YzjgBGVmf2D9KguRQlMujOgI9VKjhSoJZRADOoOTLRZnBPGzj7HwyKnRexQtgU0xwftylxI7Igi6d42mGdKQrqDpruyDPd78qnNFHSJBTCQoihnuyJ0LcvVbG2UREDwz9QVg9HrlfcJcutEqKzgpApkzfy0pFZbTH8V4ooPMYoFfyseBZZaSOzxvSdNmMednwVPTqboHwgqgt1Cay2LsW5SOWWtDt3Uvy3sjWoSBzPUx9Jgm1wK0OOvteTnFKrSKepz1wuGBXnjhdaKopvCWOL9B6whPP6PBLLYu1uFM4fyySVJgMc2YvB7mnpUmzCbisuD5Th8jvrg5q8Dt6KLU2C1eJoSpiOzJooCJlx9EIfy1UPXcelJDM1c2qzNT1GxHhIAgaTgIntr1bDX0rwMl29cQUnYZhXNvYQuz3uXfszRC441R0yokiyisSupNloIuMWkjsUux1HJEZzaq9gWRYqCZxdPzCTj81FRph2wvG9k9ZoFjVJVAAtP71dbWwRPkH1xGVgq1AHEhu2J6OQ6FDtl1wLZZPxJPd37l1n0wxWMoQOLdrSGcdiQEco7QPnX3XyhukoSMTuDP2kE1QkqJtXnCEMhC480FTjlqNyOWm5EJ6aBqn2btLngUq9EMcAaQ2dbIwgNLiGzc7j39oaDggVQotBqWcsELfsetrrJD7ZMSdlQIDxIWQlPFYDIrIYJdyptXqafvDl1jcXVC7mhGiXL8gna27e0xQoP24oTKdCN9WEJxxE8AnZAyxkiXxvLVY82rj4HaJafAbCCwQoLvEDy8QpKpWGp55m4B9VqkCjeJvX46x0WEv80vv1erWshIMmujgAFsdsXdktDQ7DOpK1tyKLLb0hgJKMFp1zy7NM5YAm1ssLrpPyqChV
    SEQUENCE:0
    STATUS:CONFIRMED
    SUMMARY:API deploy
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanRawIcsResult = iCalString.cleanRawIcs()
        assert(cleanRawIcsResult is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful)
        if (cleanRawIcsResult !is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful) return
        val cleanICalString = cleanRawIcsResult.cleanICalString

        val iCalendar = Biweekly.parse(cleanICalString).first()
        assertThat(iCalendar).isNotNull()
        iCalendar.events.forEach { event ->
            assertThat(event.cleanDescription()).isTrue()
            assertThat(event.description.value.length).isEqualTo(DESCRIPTION_MAX_LENGTH)
        }
    }

    @Test
    fun `cleanDescription LOCATION too long test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    CALSCALE:GREGORIAN
    BEGIN:VEVENT
    DTSTAMP:20190719T130854Z
    CREATED:20200505T173304Z
    LAST-MODIFIED:20200505T173304Z
    UID:6c4ad96a-632f-4b24-bdee-f5e048f70a0c@proton.test
    DTSTART;VALUE=DATE:20200506
    LOCATION:iWJZBZJzu8qZpa1EwfN43fFepSCfklnua9L1xDqJFneRBF4cChhWTTOBYt1Es8mx5wiRa4izKeheC9BAlAw1ss1EJpEHEpRxUyeNTdi5jneUqa3LhfCechW1dz2Ar44orITzw1hLOQ2p0ergwPe9rQch2OzdwpcWFXkKDb9gk45lccy0uz8mqCpG5yYQIBmSkW3mO7SrgtrCM7fKVdDKekEFfJzS4IMvyeckdiwlo6RyQoLTNMKqAuw6IfYzPAKbKj3T
    SEQUENCE:0
    STATUS:CONFIRMED
    SUMMARY:API deploy
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanRawIcsResult = iCalString.cleanRawIcs()
        assert(cleanRawIcsResult is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful)
        if (cleanRawIcsResult !is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful) return
        val cleanICalString = cleanRawIcsResult.cleanICalString

        val iCalendar = Biweekly.parse(cleanICalString).first()
        assertThat(iCalendar).isNotNull()
        iCalendar.events.forEach { event ->
            assertThat(event.cleanLocation()).isTrue()
            assertThat(event.location.value.length).isEqualTo(LOCATION_MAX_LENGTH)
        }
    }

    @Test
    fun `cleanDescription SUMMARY too long test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    CALSCALE:GREGORIAN
    BEGIN:VEVENT
    DTSTAMP:20190719T130854Z
    CREATED:20200505T173304Z
    LAST-MODIFIED:20200505T173304Z
    UID:6c4ad96a-632f-4b24-bdee-f5e048f70a0c@proton.test
    DTSTART;VALUE=DATE:20200506
    SUMMARY:iWJZBZJzu8qZpa1EwfN43fFepSCfklnua9L1xDqJFneRBF4cChhWTTOBYt1Es8mx5wiRa4izKeheC9BAlAw1ss1EJpEHEpRxUyeNTdi5jneUqa3LhfCechW1dz2Ar44orITzw1hLOQ2p0ergwPe9rQch2OzdwpcWFXkKDb9gk45lccy0uz8mqCpG5yYQIBmSkW3mO7SrgtrCM7fKVdDKekEFfJzS4IMvyeckdiwlo6RyQoLTNMKqAuw6IfYzPAKbKj3T
    SEQUENCE:0
    STATUS:CONFIRMED
    SUMMARY:API deploy
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanRawIcsResult = iCalString.cleanRawIcs()
        assert(cleanRawIcsResult is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful)
        if (cleanRawIcsResult !is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful) return
        val cleanICalString = cleanRawIcsResult.cleanICalString

        val iCalendar = Biweekly.parse(cleanICalString).first()
        assertThat(iCalendar).isNotNull()
        iCalendar.events.forEach { event ->
            assertThat(event.cleanSummary()).isTrue()
            assertThat(event.summary.value.length).isEqualTo(SUMMARY_MAX_LENGTH)
        }
    }

    @Test
    fun `cleanRRule do not generate DTSTART as occurrence test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Proton Technologies//AndroidCalendar 0.18.8//EN
    BEGIN:VEVENT
    DTSTART;TZID=Europe/Paris:20210306T160000
    DTEND;TZID=Europe/Paris:20210306T163000
    RRULE:FREQ=WEEKLY;UNTIL=20210327T225959Z;BYDAY=SU
    SEQUENCE:0
    EXDATE;TZID=Europe/Paris:20210313T160000
    EXDATE;TZID=Europe/Paris:20210320T160000
    SUMMARY:Recurring with exdates
    STATUS:CONFIRMED
    DTSTAMP:20210311T145808Z
    UID:35fdx2qMv8RPjvIFecqY1qTMIooJ@proton.me
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanRawIcsResult = iCalString.cleanRawIcs()
        assert(cleanRawIcsResult is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful)
        if (cleanRawIcsResult !is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful) return
        val cleanICalString = cleanRawIcsResult.cleanICalString

        val iCalendar = Biweekly.parse(cleanICalString).first()
        assertThat(iCalendar).isNotNull()
        iCalendar.events.forEach { event ->
            assertThat(event.cleanRRule(iCalendar)).isFalse()
        }
    }

    @Test
    fun `cleanRRule do not generate any occurrence test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Proton Technologies//AndroidCalendar 0.18.8//EN
    BEGIN:VEVENT
    DTSTART;TZID=Europe/Paris:20210306T160000
    DTEND;TZID=Europe/Paris:20210306T163000
    RRULE:FREQ=WEEKLY;UNTIL=20210305T225959Z;BYDAY=SA
    SEQUENCE:0
    EXDATE;TZID=Europe/Paris:20210313T160000
    EXDATE;TZID=Europe/Paris:20210320T160000
    SUMMARY:Recurring with exdates
    STATUS:CONFIRMED
    DTSTAMP:20210311T145808Z
    UID:35fdx2qMv8RPjvIFecqY1qTMIooJ@proton.me
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanRawIcsResult = iCalString.cleanRawIcs()
        assert(cleanRawIcsResult is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful)
        if (cleanRawIcsResult !is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful) return
        val cleanICalString = cleanRawIcsResult.cleanICalString

        val iCalendar = Biweekly.parse(cleanICalString).first()
        assertThat(iCalendar).isNotNull()
        iCalendar.events.forEach { event ->
            assertThat(event.cleanRRule(iCalendar)).isFalse()
        }
    }

    @Test
    fun `cleanRRule recurring with ex date on only occurrence test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Proton Technologies//AndroidCalendar 0.18.8//EN
    BEGIN:VEVENT
    DTSTART;TZID=Europe/Paris:20210306T160000
    DTEND;TZID=Europe/Paris:20210306T163000
    RRULE:FREQ=WEEKLY;UNTIL=20210306T225959Z;BYDAY=SA
    SEQUENCE:0
    EXDATE;TZID=Europe/Paris:20210306T160000
    SUMMARY:Recurring with exdates
    STATUS:CONFIRMED
    DTSTAMP:20210311T145808Z
    UID:35fdx2qMv8RPjvIFecqY1qTMIooJ@proton.me
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanRawIcsResult = iCalString.cleanRawIcs()
        assert(cleanRawIcsResult is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful)
        if (cleanRawIcsResult !is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful) return
        val cleanICalString = cleanRawIcsResult.cleanICalString

        val iCalendar = Biweekly.parse(cleanICalString).first()
        assertThat(iCalendar).isNotNull()
        iCalendar.events.forEach { event ->
            assertThat(event.cleanRRule(iCalendar)).isFalse()
        }
    }

    @Test
    fun `cleanRRule DAILY interval over max allowed value test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Proton Technologies//AndroidCalendar 0.18.8//EN
    BEGIN:VEVENT
    DTSTART;TZID=Europe/Paris:20210306T160000
    DTEND;TZID=Europe/Paris:20210306T163000
    RRULE:FREQ=DAILY;INTERVAL=1000
    SEQUENCE:0
    EXDATE;TZID=Europe/Paris:20210313T160000
    EXDATE;TZID=Europe/Paris:20210320T160000
    SUMMARY:Recurring with exdates
    STATUS:CONFIRMED
    DTSTAMP:20210311T145808Z
    UID:35fdx2qMv8RPjvIFecqY1qTMIooJ@proton.me
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanRawIcsResult = iCalString.cleanRawIcs()
        assert(cleanRawIcsResult is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful)
        if (cleanRawIcsResult !is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful) return
        val cleanICalString = cleanRawIcsResult.cleanICalString

        val iCalendar = Biweekly.parse(cleanICalString).first()
        assertThat(iCalendar).isNotNull()
        iCalendar.events.forEach { event ->
            assertThat(event.cleanRRule(iCalendar)).isFalse()
        }
    }

    @Test
    fun `cleanRRule WEEKLY interval over max allowed value test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Proton Technologies//AndroidCalendar 0.18.8//EN
    BEGIN:VEVENT
    DTSTART;TZID=Europe/Paris:20210306T160000
    DTEND;TZID=Europe/Paris:20210306T163000
    RRULE:FREQ=WEEKLY;INTERVAL=5000
    SEQUENCE:0
    EXDATE;TZID=Europe/Paris:20210313T160000
    EXDATE;TZID=Europe/Paris:20210320T160000
    SUMMARY:Recurring with exdates
    STATUS:CONFIRMED
    DTSTAMP:20210311T145808Z
    UID:35fdx2qMv8RPjvIFecqY1qTMIooJ@proton.me
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanRawIcsResult = iCalString.cleanRawIcs()
        assert(cleanRawIcsResult is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful)
        if (cleanRawIcsResult !is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful) return
        val cleanICalString = cleanRawIcsResult.cleanICalString

        val iCalendar = Biweekly.parse(cleanICalString).first()
        assertThat(iCalendar).isNotNull()
        iCalendar.events.forEach { event ->
            assertThat(event.cleanRRule(iCalendar)).isFalse()
        }
    }

    @Test
    fun `cleanRRule MONTHLY interval over max allowed value test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Proton Technologies//AndroidCalendar 0.18.8//EN
    BEGIN:VEVENT
    DTSTART;TZID=Europe/Paris:20210306T160000
    DTEND;TZID=Europe/Paris:20210306T163000
    RRULE:FREQ=MONTHLY;INTERVAL=1000
    SEQUENCE:0
    EXDATE;TZID=Europe/Paris:20210313T160000
    EXDATE;TZID=Europe/Paris:20210320T160000
    SUMMARY:Recurring with exdates
    STATUS:CONFIRMED
    DTSTAMP:20210311T145808Z
    UID:35fdx2qMv8RPjvIFecqY1qTMIooJ@proton.me
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanRawIcsResult = iCalString.cleanRawIcs()
        assert(cleanRawIcsResult is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful)
        if (cleanRawIcsResult !is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful) return
        val cleanICalString = cleanRawIcsResult.cleanICalString

        val iCalendar = Biweekly.parse(cleanICalString).first()
        assertThat(iCalendar).isNotNull()
        iCalendar.events.forEach { event ->
            assertThat(event.cleanRRule(iCalendar)).isFalse()
        }
    }

    @Test
    fun `cleanRRule YEARLY interval over max allowed value test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Proton Technologies//AndroidCalendar 0.18.8//EN
    BEGIN:VEVENT
    DTSTART;TZID=Europe/Paris:20210306T160000
    DTEND;TZID=Europe/Paris:20210306T163000
    RRULE:FREQ=YEARLY;INTERVAL=100
    SEQUENCE:0
    EXDATE;TZID=Europe/Paris:20210313T160000
    EXDATE;TZID=Europe/Paris:20210320T160000
    SUMMARY:Recurring with exdates
    STATUS:CONFIRMED
    DTSTAMP:20210311T145808Z
    UID:35fdx2qMv8RPjvIFecqY1qTMIooJ@proton.me
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanRawIcsResult = iCalString.cleanRawIcs()
        assert(cleanRawIcsResult is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful)
        if (cleanRawIcsResult !is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful) return
        val cleanICalString = cleanRawIcsResult.cleanICalString

        val iCalendar = Biweekly.parse(cleanICalString).first()
        assertThat(iCalendar).isNotNull()
        iCalendar.events.forEach { event ->
            assertThat(event.cleanRRule(iCalendar)).isFalse()
        }
    }

    @Test
    fun `cleanRRule COUNT over max allowed value test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Proton Technologies//AndroidCalendar 0.18.8//EN
    BEGIN:VEVENT
    DTSTART;TZID=Europe/Paris:20210306T160000
    DTEND;TZID=Europe/Paris:20210306T163000
    RRULE:FREQ=YEARLY;COUNT=50
    SEQUENCE:0
    EXDATE;TZID=Europe/Paris:20210313T160000
    EXDATE;TZID=Europe/Paris:20210320T160000
    SUMMARY:Recurring with exdates
    STATUS:CONFIRMED
    DTSTAMP:20210311T145808Z
    UID:35fdx2qMv8RPjvIFecqY1qTMIooJ@proton.me
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanRawIcsResult = iCalString.cleanRawIcs()
        assert(cleanRawIcsResult is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful)
        if (cleanRawIcsResult !is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful) return
        val cleanICalString = cleanRawIcsResult.cleanICalString

        val iCalendar = Biweekly.parse(cleanICalString).first()
        assertThat(iCalendar).isNotNull()
        iCalendar.events.forEach { event ->
            assertThat(event.cleanRRule(iCalendar)).isFalse()
        }
    }

    @Test
    fun `cleanRRule UNTIL over max allowed value test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Proton Technologies//AndroidCalendar 0.18.8//EN
    BEGIN:VEVENT
    DTSTART;TZID=Europe/Paris:20210306T160000
    DTEND;TZID=Europe/Paris:20210306T163000
    RRULE:FREQ=YEARLY;UNTIL=20380102T120000Z
    SEQUENCE:0
    EXDATE;TZID=Europe/Paris:20210313T160000
    EXDATE;TZID=Europe/Paris:20210320T160000
    SUMMARY:Recurring with exdates
    STATUS:CONFIRMED
    DTSTAMP:20210311T145808Z
    UID:35fdx2qMv8RPjvIFecqY1qTMIooJ@proton.me
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanRawIcsResult = iCalString.cleanRawIcs()
        assert(cleanRawIcsResult is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful)
        if (cleanRawIcsResult !is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful) return
        val cleanICalString = cleanRawIcsResult.cleanICalString

        val iCalendar = Biweekly.parse(cleanICalString).first()
        assertThat(iCalendar).isNotNull()
        iCalendar.events.forEach { event ->
            assertThat(event.cleanRRule(iCalendar)).isFalse()
        }
    }

    @Test
    fun `cleanRRule UNTIL is DATETIME for all day event test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Proton Technologies//AndroidCalendar 0.18.10//EN
    BEGIN:VEVENT
    DTSTART;VALUE=DATE:20210329
    DTEND;VALUE=DATE:20210330
    RRULE:FREQ=DAILY;UNTIL=20210331T120000Z
    SEQUENCE:1
    STATUS:CONFIRMED
    DTSTAMP:20210329T094605Z
    UID:002bTy1gdPpekrxdL-hc99N76RF3@proton.me
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanRawIcsResult = iCalString.cleanRawIcs()
        assert(cleanRawIcsResult is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful)
        if (cleanRawIcsResult !is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful) return
        val cleanICalString = cleanRawIcsResult.cleanICalString

        val iCalendar = Biweekly.parse(cleanICalString).first()
        assertThat(iCalendar).isNotNull()
        iCalendar.events.forEach { event ->
            assertThat(event.cleanRRule(iCalendar)).isTrue()
            assertThat(event.recurrenceRule.value.until.hasTime()).isFalse()
        }
    }

    @Test
    fun `cleanRRule UNTIL is DATE for part day event test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Proton Technologies//AndroidCalendar 0.18.10//EN
    BEGIN:VEVENT
    DTSTART;TZID=Europe/Paris:20210329T120000
    DTEND;TZID=Europe/Paris:20210329T123000
    RRULE:FREQ=DAILY;UNTIL=20210331
    SEQUENCE:0
    STATUS:CONFIRMED
    DTSTAMP:20210329T094841Z
    UID:B6CCcxfX8f3TfV0WVYTEcdSNz2aa@proton.me
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanRawIcsResult = iCalString.cleanRawIcs()
        assert(cleanRawIcsResult is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful)
        if (cleanRawIcsResult !is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful) return
        val cleanICalString = cleanRawIcsResult.cleanICalString

        val iCalendar = Biweekly.parse(cleanICalString).first()
        assertThat(iCalendar).isNotNull()
        iCalendar.events.forEach { event ->
            assertThat(event.cleanRRule(iCalendar)).isTrue()
            assertThat(event.recurrenceRule.value.until.hasTime()).isTrue()
            assertThat(event.recurrenceRule.value.until).isEqualTo(
                ICalDate.from(
                    ZonedDateTime.of(
                        2021, 3, 31, 23, 59, 59, 0, ZoneId.of(
                            "Europe/Paris"
                        )
                    ).toInstant()
                )
            )
        }
    }

    @Test
    fun `cleanRRule YEARLY with BYMONTHDAY but no BYMONTH test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Proton Technologies//AndroidCalendar 0.18.10//EN
    BEGIN:VEVENT
    DTSTART;VALUE=DATE:20210322
    DTEND;VALUE=DATE:20210323
    RRULE:FREQ=YEARLY;BYMONTHDAY=22
    SEQUENCE:0
    STATUS:CONFIRMED
    DTSTAMP:20210329T094841Z
    UID:B6CCcxfX8f3TfV0WVYTEcdSNz2aa@proton.me
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanRawIcsResult = iCalString.cleanRawIcs()
        assert(cleanRawIcsResult is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful)
        if (cleanRawIcsResult !is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful) return
        val cleanICalString = cleanRawIcsResult.cleanICalString

        val iCalendar = Biweekly.parse(cleanICalString).first()
        assertThat(iCalendar).isNotNull()
        iCalendar.events.forEach { event ->
            assertThat(event.cleanRRule(iCalendar)).isFalse()
        }
    }

    @Test
    fun `cleanRRule event with both RECURRENCE-ID and RRULE test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Proton Technologies//AndroidCalendar 0.18.10//EN
    METHOD:REPLY
    BEGIN:VEVENT
    DTSTART;VALUE=DATE:20210329
    DTEND;VALUE=DATE:20210330
    RRULE:FREQ=DAILY;UNTIL=20210331
    RECURRENCE-ID;VALUE=DATE:20210329
    SEQUENCE:1
    STATUS:CONFIRMED
    DTSTAMP:20210329T094605Z
    UID:002bTy1gdPpekrxdL-hc99N76RF3@proton.me
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanRawIcsResult = iCalString.cleanRawIcs()
        assert(cleanRawIcsResult is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful)
        if (cleanRawIcsResult !is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful) return
        val cleanICalString = cleanRawIcsResult.cleanICalString

        val iCalendar = Biweekly.parse(cleanICalString).first()
        assertThat(iCalendar).isNotNull()
        iCalendar.events.forEach { event ->
            assertThat(event.cleanRRule(iCalendar)).isTrue()
            assertThat(event.recurrenceRule).isNull()
        }
    }

    @Test
    fun `cleanRRule all day event with DATETIME UNTIL in RRULE test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    METHOD:REQUEST
    PRODID:Microsoft Exchange Server 2010
    VERSION:2.0
    BEGIN:VTIMEZONE
    TZID:Taipei Standard Time
    BEGIN:STANDARD
    DTSTART:16010101T000000
    TZOFFSETFROM:+0800
    TZOFFSETTO:+0800
    END:STANDARD
    BEGIN:DAYLIGHT
    DTSTART:16010101T000000
    TZOFFSETFROM:+0800
    TZOFFSETTO:+0800
    END:DAYLIGHT
    END:VTIMEZONE
    BEGIN:VEVENT
    ORGANIZER;CN=test:mailto:test@protonmail.com
    ATTENDEE;ROLE=REQ-PARTICIPANT;PARTSTAT=NEEDS-ACTION;RSVP=TRUE;CN=breakingcalendar@pm.me:mailto:breakingcalendar@pm.me
    DESCRIPTION;LANGUAGE=en-US:\n
    RRULE:FREQ=DAILY;UNTIL=20210522T160000Z;INTERVAL=1
    UID:android_until_outlook
    SUMMARY;LANGUAGE=en-US:android, until
    DTSTART;VALUE=DATE:20210519
    DTEND;VALUE=DATE:20210519
    CLASS:PUBLIC
    PRIORITY:5
    DTSTAMP:20201112T025741Z
    TRANSP:TRANSPARENT
    STATUS:CONFIRMED
    SEQUENCE:0
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = cleanIcs(iCalString)
        assert(cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful)
        if (cleanIcsResult !is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) return
        val iCalendar = cleanIcsResult.iCalendar

        assertThat(iCalendar).isNotNull()
        iCalendar!!.events.forEach { event ->
            assertThat(event.cleanRRule(iCalendar)).isTrue()
            val newUntilDate = ZonedDateTime.of(
                LocalDate.of(2021, 5, 23),
                LocalTime.MIDNIGHT,
                ZoneId.systemDefault()
            ).toInstant()
            assertThat(event.recurrenceRule.value.until).isEqualTo(ICalDate(Date.from(newUntilDate), false))
        }
    }

    @Test
    fun `cleanRecurrenceId RECURRENCE-ID with RRULE test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Proton Technologies//AndroidCalendar 0.18.8//EN
    BEGIN:VEVENT
    DTSTART;TZID=Europe/Paris:20210320T120000
    DTEND;TZID=Europe/Paris:20210320T123000
    RECURRENCE-ID;TZID=Europe/Paris:20210320T120000
    RRULE:FREQ=WEEKLY;UNTIL=20210328;BYDAY=SU
    SEQUENCE:0
    SUMMARY:Recurring with SE 1
    STATUS:CONFIRMED
    DTSTAMP:20210317T105458Z
    UID:lOIY56JOStapy1PUGuN4WiN67oBQ@proton.me
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanRawIcsResult = iCalString.cleanRawIcs()
        assert(cleanRawIcsResult is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful)
        if (cleanRawIcsResult !is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful) return
        val cleanICalString = cleanRawIcsResult.cleanICalString

        val iCalendar = Biweekly.parse(cleanICalString).first()
        assertThat(iCalendar).isNotNull()
        assertThat(iCalendar.cleanRecurrenceId(iCalendar.method?.isReply == true)).isFalse()
    }

    @Test
    fun `cleanRecurrenceId datetime type RECURRENCE-ID for all day event test`() {

        val parentICalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Proton Technologies//AndroidCalendar 0.18.8//EN
    BEGIN:VEVENT
    DTSTART;VALUE=DATE:20210320
    DTEND;VALUE=DATE:20210320
    RRULE:FREQ=DAILY
    SEQUENCE:0
    SUMMARY:Recurring
    STATUS:CONFIRMED
    DTSTAMP:20210317T105458Z
    UID:lOIY56JOStapy1PUGuN4WiN67oBQ@proton.me
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Proton Technologies//AndroidCalendar 0.18.8//EN
    BEGIN:VEVENT
    DTSTART;VALUE=DATE:20210420
    DTEND;VALUE=DATE:20210420
    RECURRENCE-ID;TZID=Europe/Paris:20210420T120000
    SEQUENCE:1
    SUMMARY:Recurring with SE 1
    STATUS:CONFIRMED
    DTSTAMP:20210317T105558Z
    UID:lOIY56JOStapy1PUGuN4WiN67oBQ@proton.me
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val parentICal = ICalUtilsImpl.parseICalString(parentICalString)

        val cleanRawIcsResult = iCalString.cleanRawIcs()
        assert(cleanRawIcsResult is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful)
        if (cleanRawIcsResult !is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful) return
        val cleanICalString = cleanRawIcsResult.cleanICalString

        val iCalendar = Biweekly.parse(cleanICalString).first()
        assertThat(iCalendar).isNotNull()
        assertThat(iCalendar.cleanRecurrenceId(iCalendar.method?.isReply == true, parentICal)).isTrue()
        assertThat(iCalendar.events.first().recurrenceId.value.hasTime()).isFalse()
    }

    @Test
    fun `cleanRecurrenceId date type RECURRENCE-ID for part day event test`() {

        val parentICalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Proton Technologies//AndroidCalendar 0.18.8//EN
    BEGIN:VEVENT
    DTSTART;TZID=Europe/Paris:20210320T120000
    DTEND;TZID=Europe/Paris:20210320T123000
    RRULE:FREQ=DAILY
    SEQUENCE:0
    SUMMARY:Recurring
    STATUS:CONFIRMED
    DTSTAMP:20210317T105458Z
    UID:lOIY56JOStapy1PUGuN4WiN67oBQ@proton.me
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Proton Technologies//AndroidCalendar 0.18.8//EN
    BEGIN:VEVENT
    DTSTART;TZID=Europe/Paris:20210420T120000
    DTEND;TZID=Europe/Paris:20210420T123000
    RECURRENCE-ID;VALUE=DATE:20210420
    RRULE:FREQ=WEEKLY;UNTIL=20210328;BYDAY=SU
    SEQUENCE:0
    SUMMARY:Recurring with SE 1
    STATUS:CONFIRMED
    DTSTAMP:20210317T105458Z
    UID:lOIY56JOStapy1PUGuN4WiN67oBQ@proton.me
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val parentICal = ICalUtilsImpl.parseICalString(parentICalString)

        val cleanRawIcsResult = iCalString.cleanRawIcs()
        assert(cleanRawIcsResult is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful)
        if (cleanRawIcsResult !is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful) return
        val cleanICalString = cleanRawIcsResult.cleanICalString

        val iCalendar = Biweekly.parse(cleanICalString).first()
        assertThat(iCalendar).isNotNull()
        assertThat(iCalendar.cleanRecurrenceId(iCalendar.method?.isReply == true, parentICal)).isFalse()
    }

    @Test
    fun `cleanRecurrenceId RECURRENCE-ID timezone different from the parent DTSTART test`() {

        val parentICalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Proton Technologies//AndroidCalendar 0.18.8//EN
    BEGIN:VEVENT
    DTSTART;TZID=Europe/Athens:20210320T120000
    DTEND;TZID=Europe/Athens:20210320T123000
    RRULE:FREQ=DAILY
    SEQUENCE:0
    SUMMARY:Recurring
    STATUS:CONFIRMED
    DTSTAMP:20210317T105458Z
    UID:lOIY56JOStapy1PUGuN4WiN67oBQ@proton.me
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Proton Technologies//AndroidCalendar 0.18.8//EN
    BEGIN:VEVENT
    DTSTART;VALUE=DATE:20210420
    DTEND;VALUE=DATE:20210420
    RECURRENCE-ID;TZID=Europe/Paris:20210420T120000
    SEQUENCE:1
    SUMMARY:Recurring with SE 1
    STATUS:CONFIRMED
    DTSTAMP:20210317T105558Z
    UID:lOIY56JOStapy1PUGuN4WiN67oBQ@proton.me
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        // TODO
        val parentICal = ICalUtilsImpl.parseICalString(parentICalString)

        val cleanRawIcsResult = iCalString.cleanRawIcs()
        assert(cleanRawIcsResult is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful)
        if (cleanRawIcsResult !is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful) return
        val cleanICalString = cleanRawIcsResult.cleanICalString

        val iCalendar = Biweekly.parse(cleanICalString).first()
        assertThat(iCalendar).isNotNull()
        assertThat(iCalendar.cleanRecurrenceId(false, parentICal)).isTrue()
        assertThat(iCalendar.timezoneInfo.getTimezone(iCalendar.events.first().recurrenceId).timeZone.id).isEqualTo("Europe/Athens")
    }

    @Test
    fun `cleanRecurrenceId RECURRENCE-ID timezone (with TZ definition) same as the parent DTSTART test`() {

        val parentICalString = """
BEGIN:VCALENDAR
VERSION:2.0
BEGIN:VEVENT
DTSTAMP:20210601T134616Z
DTSTART;TZID=Africa/El_Aaiun:20210601T100000
DTEND;TZID=Africa/El_Aaiun:20210601T103000
RRULE:FREQ=WEEKLY;UNTIL=20210901T225959Z;BYDAY=FR,SA,TH,TU,WE
ORGANIZER;CN=iamblueuser@gmail.com:mailto:iamblueuser@gmail.com
SEQUENCE:0
DESCRIPTION:-::~:~::~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~
 :~:~:~:~:~:~:~:~::~:~::-\nPlease do not edit this section of the descripti
 on.\n\nView your event at https://calendar.google.com/calendar/event?actio
 n=VIEW&eid=MHFndW1sbmFraGg3dTliYmZkcDNvbjk5b2YgY2FsZW5kYXJhdXRvbWF0aW9uOTk
 5QHByb3Rvbm1haWwuY29t&tok=MjEjaWFtYmx1ZXVzZXJAZ21haWwuY29tMDRjOWNmNTJhNzQ1
 YTAyYTJiODM4NTE4NzljNTU2YjY5OTM4YThjNw&ctz=Europe%2FVilnius&hl=en_GB&es=1.
 \n-::~:~::~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:
 ~:~:~:~::~:~::-
SUMMARY:Timezone +1
UID:0qgumlnakhh7u9bbfdp3on99ofadam@google.com
ATTENDEE;X-PM-TOKEN=bd25aa978853c40eec974a60a8b1911a1c0ec567;RSVP=TRUE;ROLE
 =REQ-PARTICIPANT;PARTSTAT=TENTATIVE;CN=adamtst@protonmail.com:mailto:adamt
 st@protonmail.com
ATTENDEE;X-PM-TOKEN=cb098dff9886f4688bb50b9138d796357144dfa2;RSVP=TRUE;ROLE
 =REQ-PARTICIPANT;PARTSTAT=ACCEPTED;CN=iamblueuser@gmail.com:mailto:iamblue
 user@gmail.com
BEGIN:VALARM
ACTION:DISPLAY
TRIGGER:-PT15M
END:VALARM
END:VEVENT
END:VCALENDAR
    """.trimIndent()

        val iCalString = """
BEGIN:VCALENDAR
PRODID:-//Google Inc//Google Calendar 70.9054//EN
VERSION:2.0
CALSCALE:GREGORIAN
METHOD:REQUEST
BEGIN:VTIMEZONE
TZID:Africa/El_Aaiun
X-LIC-LOCATION:Africa/El_Aaiun
BEGIN:STANDARD
TZOFFSETFROM:+0000
TZOFFSETTO:+0000
TZNAME:+00
DTSTART:19700101T000000
END:STANDARD
END:VTIMEZONE
BEGIN:VEVENT
DTSTART;VALUE=DATE:20210729
DTEND;VALUE=DATE:20210730
DTSTAMP:20210601T135021Z
ORGANIZER;CN=iamblueuser@gmail.com:mailto:iamblueuser@gmail.com
UID:0qgumlnakhh7u9bbfdp3on99ofadam2@google.com
ATTENDEE;CUTYPE=INDIVIDUAL;ROLE=REQ-PARTICIPANT;PARTSTAT=ACCEPTED;RSVP=TRUE
 ;CN=iamblueuser@gmail.com;X-NUM-GUESTS=0:mailto:iamblueuser@gmail.com
ATTENDEE;CUTYPE=INDIVIDUAL;ROLE=REQ-PARTICIPANT;PARTSTAT=NEEDS-ACTION;RSVP=
 TRUE;CN=adamtst@protonmail.com;X-NUM-GUESTS=0:mailto:adamtst
 @protonmail.com
X-MICROSOFT-CDO-OWNERAPPTID:-462541747
RECURRENCE-ID;TZID=Africa/El_Aaiun:20210729T100000
CREATED:20210601T134615Z
DESCRIPTION:-::~:~::~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~
 :~:~:~:~:~:~:~:~::~:~::-\nPlease do not edit this section of the descriptio
 n.\n\nView your event at https://calendar.google.com/calendar/event?action=
 VIEW&eid=MHFndW1sbmFraGg3dTliYmZkcDNvbjk5b2ZfMjAyMTA3MjlUMDkwMDAwWiBjYWxlbm
 RhcmF1dG9tYXRpb245OTlAcHJvdG9ubWFpbC5jb20&tok=MjEjaWFtYmx1ZXVzZXJAZ21haWwuY
 29tOGQwZTZjNTc5ZWY1NDgyN2UzODc0ZDU5ZjQ3NmRiMTA5YzFhOTdkOQ&ctz=Europe%2FViln
 ius&hl=en_GB&es=0.\n-::~:~::~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~
 :~:~:~:~:~:~:~:~:~:~:~:~::~:~::-
LAST-MODIFIED:20210601T135020Z
LOCATION:
SEQUENCE:1
STATUS:CONFIRMED
SUMMARY:Timezone To full day
TRANSP:OPAQUE
END:VEVENT
END:VCALENDAR
    """.trimIndent()

        val parentICal = ICalUtilsImpl.parseICalString(parentICalString)

        val cleanIcsResult = IcsSurgeryUtils.cleanIcs(iCalString)

        assert(cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful)

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar!!

            assertThat(iCalendar).isNotNull()
            assertThat(iCalendar.cleanRecurrenceId(true, parentICal)).isTrue()
            assertThat(iCalendar.timezoneInfo.getTimezone(iCalendar.events.first().recurrenceId).timeZone.id).isEqualTo("Africa/El_Aaiun")
        }
    }

    @Test
    fun `cleanExDate EXDATE is of type DATE-TIME for an all-day event test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Proton Technologies//AndroidCalendar 0.18.8//EN
    BEGIN:VEVENT
    DTSTART;VALUE=DATE:20210307
    DTEND;VALUE=DATE:20210308
    RRULE:FREQ=WEEKLY;UNTIL=20210328;BYDAY=SU
    SEQUENCE:0
    EXDATE;TZID=Europe/Paris:20210314T160000
    EXDATE;TZID=Europe/Paris:20210321T160000
    SUMMARY:Recurring all day with exdates
    STATUS:CONFIRMED
    DTSTAMP:20210311T152217Z
    UID:tJdI3clfxULR9iZ5oUkKhvqUsB6d@proton.me
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanRawIcsResult = iCalString.cleanRawIcs()
        assert(cleanRawIcsResult is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful)
        if (cleanRawIcsResult !is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful) return
        val cleanICalString = cleanRawIcsResult.cleanICalString

        val iCalendar = Biweekly.parse(cleanICalString).first()
        assertThat(iCalendar).isNotNull()
        iCalendar.events.forEach { event ->
            assertThat(event.cleanExDate()).isTrue()
            assertThat(event.exceptionDates.first().values.first().hasTime()).isFalse()
            assertThat(event.exceptionDates.first().values.first()).isEqualTo(
                ICalDate(
                    Date.from(
                        ZonedDateTime.of(
                            2021,
                            3,
                            14,
                            0,
                            0,
                            0,
                            0,
                            ZoneId.systemDefault()
                        ).toInstant()
                    ), false
                )
            )
        }
    }

    @Test
    fun `cleanExDate EXDATE of type DATE for part day event test`() {

        // TODO

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Proton Technologies//AndroidCalendar 0.18.8//EN
    BEGIN:VEVENT
    DTSTART;TZID=Europe/Paris:20210306T160000
    DTEND;TZID=Europe/Paris:20210306T163000
    RRULE:FREQ=WEEKLY;UNTIL=20210327T225959Z;BYDAY=SA
    SEQUENCE:0
    EXDATE;VALUE=DATE:20210313
    EXDATE;VALUE=DATE:20210320
    SUMMARY:Recurring with exdates
    STATUS:CONFIRMED
    DTSTAMP:20210311T145808Z
    UID:35fdx2qMv8RPjvIFecqY1qTMIooJ@proton.me
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanRawIcsResult = iCalString.cleanRawIcs()
        assert(cleanRawIcsResult is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful)
        if (cleanRawIcsResult !is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful) return
        val cleanICalString = cleanRawIcsResult.cleanICalString

        val iCalendar = Biweekly.parse(cleanICalString).first()
        assertThat(iCalendar).isNotNull()
        iCalendar.events.forEach { event ->
            assertThat(event.cleanExDate()).isFalse()
        }
    }

    @Test
    fun `cleanExDate EXDATE has timezone different from DTSTART test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Proton Technologies//AndroidCalendar 0.18.8//EN
    BEGIN:VEVENT
    DTSTART;TZID=Europe/Paris:20210306T160000
    DTEND;TZID=Europe/Paris:20210306T163000
    RRULE:FREQ=WEEKLY;UNTIL=20210327T225959Z;BYDAY=SA
    SEQUENCE:0
    EXDATE;TZID=Europe/Vilnius:20210306T170000
    EXDATE;TZID=Europe/Vilnius:20210320T170000
    SUMMARY:Recurring with exdates
    STATUS:CONFIRMED
    DTSTAMP:20210311T145808Z
    UID:35fdx2qMv8RPjvIFecqY1qTMIooJ@proton.me
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanRawIcsResult = iCalString.cleanRawIcs()
        assert(cleanRawIcsResult is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful)
        if (cleanRawIcsResult !is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful) return
        val cleanICalString = cleanRawIcsResult.cleanICalString

        val iCalendar = Biweekly.parse(cleanICalString).first()
        assertThat(iCalendar).isNotNull()
        assertThat(iCalendar.cleanTimezones()).isTrue()
        assertThat(iCalendar.timezoneInfo.getTimezone(iCalendar.events.first().exceptionDates[0])).isEqualTo(iCalendar.timezoneInfo.getTimezone(iCalendar.events.first().dateStart))
        assertThat(iCalendar.timezoneInfo.getTimezone(iCalendar.events.first().exceptionDates[1])).isEqualTo(iCalendar.timezoneInfo.getTimezone(iCalendar.events.first().dateStart))
        assertThat(iCalendar.events.first().exceptionDates[0].values.first()).isEqualTo(iCalendar.events.first().dateStart.value)
        iCalendar.events.forEach { event ->
            assertThat(event.cleanExDate()).isTrue()
        }
    }

    @Test
    fun `cleanAttendees duplicated ATTENDEE`() {

        val iCalString = """
    BEGIN:VCALENDAR
    PRODID:-//Proton Technologies//ProtonCalendar 4.1.8//EN
    VERSION:2.0
    METHOD:REQUEST
    CALSCALE:GREGORIAN
    BEGIN:VEVENT
    SEQUENCE:1
    STATUS:CONFIRMED
    SUMMARY:Test
    UID:FKQKTlAcHr6irit-E7iuR3elGxFf@proton.me
    DTSTART;TZID=Europe/Paris:20210326T130000
    DTEND;TZID=Europe/Paris:20210326T140000
    ORGANIZER;CN=test@protonmail.com:mailto:test@proton
     mail.com
    ATTENDEE;CN=testattendee@protonmail.com;ROLE=REQ-PARTICIPANT;RSVP=TRUE;PA
     RTSTAT=NEEDS-ACTION:mailto:testattendee@protonmail.com
    ATTENDEE;CN=testattendee@protonmail.com;ROLE=REQ-PARTICIPANT;RSVP=TRUE;PA
     RTSTAT=NEEDS-ACTION:mailto:testattendee@protonmail.com
    DTSTAMP:20210324T090044Z
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanRawIcsResult = iCalString.cleanRawIcs()
        assert(cleanRawIcsResult is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful)
        if (cleanRawIcsResult !is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful) return
        val cleanICalString = cleanRawIcsResult.cleanICalString

        val iCalendar = Biweekly.parse(cleanICalString).first()
        assertThat(iCalendar).isNotNull()
        iCalendar.events.forEach { event ->
            assertThat(event.cleanAttendees()).isFalse()
        }
    }

    @Test
    fun `cleanAttendees ATTENDEE with no email`() {

        val iCalString = """
    BEGIN:VCALENDAR
    PRODID:-//Proton Technologies//ProtonCalendar 4.1.8//EN
    VERSION:2.0
    METHOD:REQUEST
    CALSCALE:GREGORIAN
    BEGIN:VEVENT
    SEQUENCE:1
    STATUS:CONFIRMED
    SUMMARY:Test
    UID:FKQKTlAcHr6irit-E7iuR3elGxFf@proton.me
    DTSTART;TZID=Europe/Paris:20210326T130000
    DTEND;TZID=Europe/Paris:20210326T140000
    ORGANIZER;CN=test@protonmail.com:mailto:test@proton
     mail.com
    ATTENDEE;CN=testattendee;ROLE=REQ-PARTICIPANT;RSVP=TRUE;PARTSTAT=NEEDS-AC
     TION:mailto:testattendee
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanRawIcsResult = iCalString.cleanRawIcs()
        assert(cleanRawIcsResult is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful)
        if (cleanRawIcsResult !is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful) return
        val cleanICalString = cleanRawIcsResult.cleanICalString

        val iCalendar = Biweekly.parse(cleanICalString).first()
        assertThat(iCalendar).isNotNull()
        iCalendar.events.forEach { event ->
            assertThat(event.cleanAttendees()).isFalse()
        }
    }

    @Test
    fun `cleanAttendees ATTENDEE with Apple garbage`() {

        val iCalString = """
    BEGIN:VCALENDAR
    PRODID:-//Proton Technologies//ProtonCalendar 4.1.8//EN
    VERSION:2.0
    METHOD:REQUEST
    CALSCALE:GREGORIAN
    BEGIN:VEVENT
    SEQUENCE:1
    STATUS:CONFIRMED
    SUMMARY:Test
    UID:FKQKTlAcHr6irit-E7iuR3elGxFf@proton.me
    DTSTART;TZID=Europe/Paris:20210326T130000
    DTEND;TZID=Europe/Paris:20210326T140000
    ORGANIZER;CN=test@protonmail.com:mailto:test@proton
     mail.com
    ATTENDEE;CN=test1@pm.me;CUTYPE=INDIVIDUAL;EMAIL=test1@pm.me;RSVP=TRUE;PARTST
     AT=NEEDS-ACTION:/1234567zMDQ4Mjk2MDIzMIZjbeHD-pCEmJU6loV23jx6n2nXhXA9yXmtoE
     412345/principal/
    ATTENDEE;CN=test2@protonmail.com;CUTYPE=INDIVIDUAL;EMAIL=test2@protonmail.co
     m;RSVP=TRUE;PARTSTAT=NEEDS-ACTION:/1234567zMDQ4Mjk2MDIzMIZjbeHD-pCEmJU6loV2
     3jx6n2nXhXA9yXmtoE412345/principal/
    ATTENDEE;CN=test31@example.com;EMAIL=test32@example.com;PARTSTAT=NEEDS
     -ACTION;ROLE=REQ-PARTICIPANT:test33
    ATTENDEE;CN=test41;EMAIL=test42@example.com;PARTSTAT=NEEDS-ACTION;ROLE
     =REQ-PARTICIPANT:test43
    ATTENDEE;CN=test51;EMAIL=test52@example.com;PARTSTAT=NEEDS-ACTION;ROLE
     =REQ-PARTICIPANT:test53
    ATTENDEE;CN=test61@example.com;EMAIL=test62;PARTSTAT=NEEDS-ACTION;ROLE
     =REQ-PARTICIPANT:test63
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanRawIcsResult = iCalString.cleanRawIcs()
        assert(cleanRawIcsResult is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful)
        if (cleanRawIcsResult !is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful) return
        val cleanICalString = cleanRawIcsResult.cleanICalString

        val iCalendar = Biweekly.parse(cleanICalString).first()
        assertThat(iCalendar).isNotNull()
        iCalendar.events.forEach { event ->
            assertThat(event.cleanAttendees()).isTrue()
            event.attendees.forEach {
                assertThat(it.uri?.contains("/principal/") == true).isFalse()
                assertThat(it.uri?.contains("test") == true).isFalse()
            }

            assertThat(event.attendees[0].email).isEqualTo("test1@pm.me")
            assertThat(event.attendees[1].email).isEqualTo("test2@protonmail.com")
            assertThat(event.attendees[2].email).isEqualTo("test32@example.com")
            assertThat(event.attendees[3].email).isEqualTo("test42@example.com")
            assertThat(event.attendees[4].email).isEqualTo("test52@example.com")
            assertThat(event.attendees[5].email).isEqualTo("test61@example.com")
        }
    }

    @Test
    fun `cleanSequence SEQUENCE negative value test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    CALSCALE:GREGORIAN
    BEGIN:VEVENT
    DTSTAMP:20190719T130854Z
    CREATED:20200505T173304Z
    LAST-MODIFIED:20200505T173304Z
    UID:6c4ad96a-632f-4b24-bdee-f5e048f70a0c@proton.test
    DTSTART;VALUE=DATE:20210102
    SEQUENCE:-2
    STATUS:CONFIRMED
    SUMMARY:API deploy
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanRawIcsResult = iCalString.cleanRawIcs()
        assert(cleanRawIcsResult is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful)
        if (cleanRawIcsResult !is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful) return
        val cleanICalString = cleanRawIcsResult.cleanICalString

        val iCalendar = Biweekly.parse(cleanICalString).first()
        assertThat(iCalendar).isNotNull()
        iCalendar.events.forEach { event ->
            assertThat(event.cleanSequence()).isTrue()
            assertThat(event.sequence.value).isEqualTo(0)
        }
    }

    @Test
    fun `test your VALID ics here`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Proton Technologies//AndroidCalendar 0.18.7//EN
    METHOD:REQUEST
    CALSCALE:GREGORIAN
    X-PM-SESSION-KEY:GOdCw1W1in43El2X+uVgMXrorJW87D6WN/6ao5nkrSI=
    X-PM-SHARED-EVENT-ID:_dVrW1d2kRgxU-pfRi5gZrZzmlEPKRHVrq5gA1UdzOU1RB8CN3nzLp
     ULi04uPDv9Nys3XJjTnVTiY0ApsQyKyM58Jt7hHBogG05YJrRgrH0=
    BEGIN:VEVENT
    DTSTART;TZID=Europe/Paris:20210310T200000
    DTEND;TZID=Europe/Paris:20210310T203000
    SEQUENCE:0
    ORGANIZER;CN=breakingcalendar@protonmail.com:mailto:breakingcalendar@proton
     mail.com
    SUMMARY:Test android 5
    STATUS:CONFIRMED
    DTSTAMP:20210310T183328Z
    UID:n3l-g25tlL00MzsgzqlVAKJB8RRs@proton.me
    ATTENDEE;X-PM-TOKEN=d7dff916bc6a007718f7e7bcb637821aee5279bb;RSVP=TRUE;ROLE
     =REQ-PARTICIPANT;PARTSTAT=NEEDS-ACTION;CN=:mailto:benjaminlovestesting@pro
     tonmail.com
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = IcsSurgeryUtils.cleanIcs(iCalString)

        assert(cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful)

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar

            assertThat(iCalendar).isNotNull()
        }
    }

    @Disabled
    @Test
    fun `ics error folder test`() {
        val files = File("./src/test/resources/ics/errors").listFiles()

        assertThat(files).isNotNull()

        files?.forEach {
            val inputStream: InputStream? = this.javaClass.classLoader?.getResourceAsStream("ics/errors/" + it.name)
            val bufferedReader = BufferedReader(InputStreamReader(inputStream))
            val iCalString = bufferedReader.use { it.readText() }

            val cleanIcsResult = IcsSurgeryUtils.cleanIcs(iCalString, allowMultipleEvents = true)

            print("File tested: ${it.name}\n")
            assert(cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.Error)
        }
    }

    @Disabled
    @Test
    fun `ics valid folder test`() {
        val files = File("./src/test/resources/ics/valid").listFiles()

        assertThat(files).isNotNull()

        files?.forEach {
            val inputStream: InputStream? = this.javaClass.classLoader?.getResourceAsStream("ics/valid/" + it.name)
            val bufferedReader = BufferedReader(InputStreamReader(inputStream))
            val iCalString = bufferedReader.use { it.readText() }

            val cleanIcsResult = IcsSurgeryUtils.cleanIcs(iCalString, allowMultipleEvents = true)

            print("File tested: ${it.name}\n")
            assert(cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful)
        }
    }

    @Disabled
    @Test
    fun `ics error file test`() {
        val inputStream: InputStream? = this.javaClass.classLoader?.getResourceAsStream("ics/errors/RecurringRuleInconsistent.ics")
        val bufferedReader = BufferedReader(InputStreamReader(inputStream))
        val iCalString = bufferedReader.use { it.readText() }

        val cleanIcsResult = IcsSurgeryUtils.cleanIcs(iCalString, allowMultipleEvents = true)

        assert(cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.Error)
    }
}
