package me.proton.android.calendar.common

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isNullOrEmpty
import assertk.assertions.isTrue
import biweekly.Biweekly
import biweekly.property.Action
import me.proton.android.calendar.common.utils.ICalUtilsImpl.printToString
import me.proton.android.calendar.common.utils.IcsSurgeryUtils
import me.proton.android.calendar.common.utils.IcsSurgeryUtils.cleanAlarms
import me.proton.android.calendar.common.utils.IcsSurgeryUtils.cleanDtStamp
import me.proton.android.calendar.common.utils.IcsSurgeryUtils.cleanRawIcs
import org.junit.jupiter.api.Test
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Date

internal class ImportIcsSurgeryUtilsTest {

    @Test
    fun `cleanDtstamp missing DTSTAMP for Import test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-// calendar.com //NONSGML Version 1//EN
    SEQUENCE:0
    STATUS:CONFIRMED
    UID:475i5djdmpjt342igfvfc7bo0c@google.com
    BEGIN:VEVENT
    SUMMARY:test
    DTSTART:20210115T130000Z
    DTEND:20210115T140000Z
    ATTENDEE;CN=test.test@protonmail.com;CUTYPE=INDIVIDUAL;EMAIL=test.test@pro
     tonmail.com;PARTSTAT=NEEDS-ACTION;ROLE=REQ-PARTICIPANT;RSVP=TRUE:mailto:te
     st.test@protonmail.com
    DESCRIPTION:\n
    ORGANIZER;CN=test@gmail.com:mailto:test@gmail.com
    TRANSP:OPAQUE
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanRawIcsResult = iCalString.cleanRawIcs()
        assertThat(cleanRawIcsResult is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful).isTrue()
        val cleanICalString = (cleanRawIcsResult as IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful).cleanICalString

        val iCalendar = Biweekly.parse(cleanICalString).first()
        assertThat(iCalendar).isNotNull()
        iCalendar.events.forEach { event ->
            assertThat(event.cleanDtStamp(iCalendar, isImport = true)).isTrue()
        }
    }

    @Test
    fun `cleanDtstamp DTSTAMP no timezone for Import test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-// calendar.com //NONSGML Version 1//EN
    SEQUENCE:0
    STATUS:CONFIRMED
    UID:475i5djdmpjt342igfvfc7bo0c@google.com
    BEGIN:VEVENT
    SUMMARY:test
    DTSTART:20210115T130000Z
    DTEND:20210115T140000Z
    ATTENDEE;CN=test.test@protonmail.com;CUTYPE=INDIVIDUAL;EMAIL=test.test@pro
     tonmail.com;PARTSTAT=NEEDS-ACTION;ROLE=REQ-PARTICIPANT;RSVP=TRUE:mailto:te
     st.test@protonmail.com
    DESCRIPTION:\n
    ORGANIZER;CN=test@gmail.com:mailto:test@gmail.com
    DTSTAMP:20210302T115550
    TRANSP:OPAQUE
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanRawIcsResult = iCalString.cleanRawIcs()
        assertThat(cleanRawIcsResult is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful).isTrue()
        val cleanICalString = (cleanRawIcsResult as IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful).cleanICalString

        val iCalendar = Biweekly.parse(cleanICalString).first()
        assertThat(iCalendar).isNotNull()
        iCalendar.events.forEach { event ->
            assertThat(event.cleanDtStamp(iCalendar, isImport = true)).isTrue()
            assertThat(event.dateTimeStamp.value).isEqualTo(Date.from(
                ZonedDateTime.of(
                    2021,
                    3,
                    2,
                    11,
                    55,
                    50,
                    0,
                    ZoneId.of("UTC")
                ).toInstant()
            ))
        }
    }

    @Test
    fun `cleanDtstamp DTSTAMP date no time for Import test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-// calendar.com //NONSGML Version 1//EN
    SEQUENCE:0
    STATUS:CONFIRMED
    UID:475i5djdmpjt342igfvfc7bo0c@google.com
    BEGIN:VEVENT
    SUMMARY:test
    DTSTART:20210115T130000Z
    DTEND:20210115T140000Z
    ATTENDEE;CN=test.test@protonmail.com;CUTYPE=INDIVIDUAL;EMAIL=test.test@pro
     tonmail.com;PARTSTAT=NEEDS-ACTION;ROLE=REQ-PARTICIPANT;RSVP=TRUE:mailto:te
     st.test@protonmail.com
    DESCRIPTION:\n
    ORGANIZER;CN=test@gmail.com:mailto:test@gmail.com
    DTSTAMP;VALUE=DATE:20210302
    TRANSP:OPAQUE
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanRawIcsResult = iCalString.cleanRawIcs()
        assertThat(cleanRawIcsResult is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful).isTrue()
        val cleanICalString = (cleanRawIcsResult as IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful).cleanICalString

        val iCalendar = Biweekly.parse(cleanICalString).first()
        assertThat(iCalendar).isNotNull()
        iCalendar.events.forEach { event ->
            assertThat(event.cleanDtStamp(iCalendar, isImport = true)).isTrue()
            assertThat(event.dateTimeStamp.value).isEqualTo(Date.from(
                ZonedDateTime.of(
                    2021,
                    3,
                    2,
                    0,
                    0,
                    0,
                    0,
                    ZoneId.of("UTC")
                ).toInstant()
            ))
        }
    }

    @Test
    fun `cleanTimezones date no timezone for Import test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//DigitalU//NONSGML 32030344//EN
    CALSCALE:GREGORIAN
    METHOD:PUBLISH
    BEGIN:VEVENT
    SUMMARY:Retrait de votre commande CoursesU.com
    CLASS:PUBLIC
    STATUS:CONFIRMED
    UID:1649423783427@www.coursesu.com
    DTSTART:20220409T103000
    DTEND:20220409T113000
    DTSTAMP:20220408T125428
    LOCATION:Super U LOISIN,RD 1206,74140,LOISIN
    BEGIN:VALARM
    TRIGGER:-PT1H
    DESCRIPTION:Retrait de votre commande CoursesU.com
    ACTION:DISPLAY
    END:VALARM
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult =
            IcsSurgeryUtils.cleanIcs(iCalString, timeZoneId = "Europe/Paris")
        assertThat(cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful).isTrue()
        val iCalendar = (cleanIcsResult as IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful).iCalendar

        assertThat(iCalendar).isNotNull()
        iCalendar?.events?.forEach { event ->

        }
    }

    @Test
    fun `cleanAlarms VALARM with RELATED END`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:icalendar
    CALSCALE:GREGORIAN
    METHOD:PUBLISH
    BEGIN:VEVENT
    DTSTAMP:20210204T130756Z
    DTSTART;TZID=Europe/Paris:20210223T090000
    DTEND;TZID=Europe/Paris:20210223T094000
    CLASS:PRIVATE
    PRIORITY:2
    SUMMARY:Test alarms
    BEGIN:VALARM
    ACTION:DISPLAY
    TRIGGER:-PT15M
    END:VALARM
    BEGIN:VALARM
    ACTION:DISPLAY
    TRIGGER;RELATED=END:-P2D    
    END:VALARM
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanRawIcsResult = iCalString.cleanRawIcs()
        assertThat(cleanRawIcsResult is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful).isTrue()
        val cleanICalString = (cleanRawIcsResult as IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful).cleanICalString

        val iCalendar = Biweekly.parse(cleanICalString).first()
        assertThat(iCalendar).isNotNull()
        iCalendar.events.forEach { event ->
            assertThat(event.alarms.size).isEqualTo(2)
            event.cleanAlarms()
            assertThat(event.alarms.size).isEqualTo(1)
        }
    }

    @Test
    fun `cleanAlarms VALARM with ACTION AUDIO`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:icalendar
    CALSCALE:GREGORIAN
    METHOD:PUBLISH
    BEGIN:VEVENT
    DTSTAMP:20210204T130756Z
    DTSTART;TZID=Europe/Paris:20210223T090000
    DTEND;TZID=Europe/Paris:20210223T094000
    CLASS:PRIVATE
    PRIORITY:2
    SUMMARY:Test alarms
    BEGIN:VALARM
    ACTION:AUDIO
    TRIGGER:-PT15M
    END:VALARM
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanRawIcsResult = iCalString.cleanRawIcs()
        assertThat(cleanRawIcsResult is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful).isTrue()
        val cleanICalString = (cleanRawIcsResult as IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful).cleanICalString

        val iCalendar = Biweekly.parse(cleanICalString).first()
        assertThat(iCalendar).isNotNull()
        iCalendar.events.forEach { event ->
            assertThat(event.alarms.size).isEqualTo(1)
            assertThat(event.alarms.first().action).isEqualTo(Action(Action.AUDIO))
            event.cleanAlarms()
            assertThat(event.alarms.first().action).isEqualTo(Action(Action.DISPLAY))
        }
    }

    @Test
    fun `cleanAlarms VALARM with duplicates`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:icalendar
    CALSCALE:GREGORIAN
    METHOD:PUBLISH
    BEGIN:VEVENT
    DTSTAMP:20210204T130756Z
    DTSTART;TZID=Europe/Paris:20210223T090000
    DTEND;TZID=Europe/Paris:20210223T094000
    CLASS:PRIVATE
    PRIORITY:2
    SUMMARY:Test alarms
    BEGIN:VALARM
    ACTION:DISPLAY
    TRIGGER:-PT15M
    END:VALARM
    BEGIN:VALARM
    ACTION:DISPLAY
    TRIGGER:-PT15M
    END:VALARM
    BEGIN:VALARM
    ACTION:DISPLAY
    TRIGGER:-PT15M
    END:VALARM
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanRawIcsResult = iCalString.cleanRawIcs()
        assertThat(cleanRawIcsResult is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful).isTrue()
        val cleanICalString = (cleanRawIcsResult as IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful).cleanICalString

        val iCalendar = Biweekly.parse(cleanICalString).first()
        assertThat(iCalendar).isNotNull()
        iCalendar.events.forEach { event ->
            assertThat(event.alarms.size).isEqualTo(3)
            event.cleanAlarms()
            assertThat(event.alarms.size).isEqualTo(1)
        }
    }

    @Test
    fun `cleanAlarms VALARM with one duplicate and more than 10`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:icalendar
    CALSCALE:GREGORIAN
    METHOD:PUBLISH
    BEGIN:VEVENT
    DTSTAMP:20210204T130756Z
    DTSTART;TZID=Europe/Paris:20210223T090000
    DTEND;TZID=Europe/Paris:20210223T094000
    CLASS:PRIVATE
    PRIORITY:2
    SUMMARY:Test alarms
    BEGIN:VALARM
    ACTION:DISPLAY
    TRIGGER:-PT1M
    END:VALARM
    BEGIN:VALARM
    ACTION:DISPLAY
    TRIGGER:-PT1M
    END:VALARM
    BEGIN:VALARM
    ACTION:DISPLAY
    TRIGGER:-PT2M
    END:VALARM
    BEGIN:VALARM
    ACTION:DISPLAY
    TRIGGER:-PT3M
    END:VALARM
    BEGIN:VALARM
    ACTION:DISPLAY
    TRIGGER:-PT4M
    END:VALARM
    BEGIN:VALARM
    ACTION:DISPLAY
    TRIGGER:-PT5M
    END:VALARM
    BEGIN:VALARM
    ACTION:DISPLAY
    TRIGGER:-PT6M
    END:VALARM
    BEGIN:VALARM
    ACTION:DISPLAY
    TRIGGER:-PT7M
    END:VALARM
    BEGIN:VALARM
    ACTION:DISPLAY
    TRIGGER:-PT8M
    END:VALARM
    BEGIN:VALARM
    ACTION:DISPLAY
    TRIGGER:-PT9M
    END:VALARM
    BEGIN:VALARM
    ACTION:DISPLAY
    TRIGGER:-PT10M
    END:VALARM
    BEGIN:VALARM
    ACTION:DISPLAY
    TRIGGER:-PT11M
    END:VALARM
    BEGIN:VALARM
    ACTION:DISPLAY
    TRIGGER:-PT12M
    END:VALARM
    BEGIN:VALARM
    ACTION:DISPLAY
    TRIGGER:-PT13M
    END:VALARM
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanRawIcsResult = iCalString.cleanRawIcs()
        assertThat(cleanRawIcsResult is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful).isTrue()
        val cleanICalString = (cleanRawIcsResult as IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful).cleanICalString

        val iCalendar = Biweekly.parse(cleanICalString).first()
        assertThat(iCalendar).isNotNull()
        iCalendar.events.forEach { event ->
            assertThat(event.alarms.size).isEqualTo(14)
            event.cleanAlarms()
            assertThat(event.alarms.first().trigger?.duration?.toMillis()).isEqualTo(Duration.ofMinutes(1).toMillis() * -1)
            assertThat(event.alarms.last().trigger?.duration?.toMillis()).isEqualTo(Duration.ofMinutes(10).toMillis() * -1)
            assertThat(event.alarms.size).isEqualTo(10)
        }
    }

    @Test
    fun `cleanAlarms VALARM with positive TRIGGER`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:icalendar
    CALSCALE:GREGORIAN
    METHOD:PUBLISH
    BEGIN:VEVENT
    DTSTAMP:20210204T130756Z
    DTSTART;TZID=Europe/Paris:20210223T090000
    DTEND;TZID=Europe/Paris:20210223T094000
    CLASS:PRIVATE
    PRIORITY:2
    SUMMARY:Test alarms
    BEGIN:VALARM
    ACTION:DISPLAY
    TRIGGER:PT1M
    END:VALARM
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanRawIcsResult = iCalString.cleanRawIcs()
        assertThat(cleanRawIcsResult is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful).isTrue()
        val cleanICalString = (cleanRawIcsResult as IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful).cleanICalString

        val iCalendar = Biweekly.parse(cleanICalString).first()
        assertThat(iCalendar).isNotNull()
        iCalendar.events.forEach { event ->
            assertThat(event.alarms.size).isEqualTo(1)
            event.cleanAlarms()
            assertThat(event.alarms.size).isEqualTo(0)
        }
    }

    @Test
    fun `cleanAlarms part day event VALARM with mixed components TRIGGER`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:icalendar
    CALSCALE:GREGORIAN
    METHOD:PUBLISH
    BEGIN:VEVENT
    DTSTAMP:20210204T130756Z
    DTSTART;TZID=Europe/Paris:20210223T090000
    DTEND;TZID=Europe/Paris:20210223T094000
    CLASS:PRIVATE
    PRIORITY:2
    SUMMARY:Test alarms
    BEGIN:VALARM
    ACTION:DISPLAY
    TRIGGER:-P1W3DT4H
    END:VALARM
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanRawIcsResult = iCalString.cleanRawIcs()
        assertThat(cleanRawIcsResult is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful).isTrue()
        val cleanICalString = (cleanRawIcsResult as IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful).cleanICalString

        val iCalendar = Biweekly.parse(cleanICalString).first()
        assertThat(iCalendar).isNotNull()
        iCalendar.events.forEach { event ->
            assertThat(event.alarms.first().trigger.duration.toString()).isEqualTo("-P1W3DT4H")
            event.cleanAlarms()
            assertThat(event.alarms.first().trigger.duration.toString()).isEqualTo("PT-244H")
        }
    }

    @Test
    fun `cleanAlarms all day event VALARM with mixed components TRIGGER`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:icalendar
    CALSCALE:GREGORIAN
    METHOD:PUBLISH
    BEGIN:VEVENT
    DTSTAMP:20210204T130756Z
    DTSTART;VALUE=DATE:20210223
    DTEND;VALUE=DATE:20210223
    CLASS:PRIVATE
    PRIORITY:2
    SUMMARY:Test alarms
    BEGIN:VALARM
    ACTION:DISPLAY
    TRIGGER:-P1W3DT4H
    END:VALARM
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanRawIcsResult = iCalString.cleanRawIcs()
        assertThat(cleanRawIcsResult is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful).isTrue()
        val cleanICalString = (cleanRawIcsResult as IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful).cleanICalString

        val iCalendar = Biweekly.parse(cleanICalString).first()
        assertThat(iCalendar).isNotNull()
        iCalendar.events.forEach { event ->
            assertThat(event.alarms.first().trigger.duration.toString()).isEqualTo("-P1W3DT4H")
            event.cleanAlarms()
            assertThat(event.alarms.first().trigger.duration.toString()).isEqualTo("-P1W3DT4H")
        }
    }

    @Test
    fun `test handle import invitation`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Recruitee//Recruitee Events//EN
    CALSCALE:GREGORIAN
    BEGIN:VEVENT
    DTSTART:20201125T130000Z
    DTEND:20201125T140000Z
    CREATED:20201124T170307Z
    DTSTAMP:20201124T170310Z
    RECURRENCE-ID:20201125T130000Z
    RRULE:FREQ=WEEKLY;UNTIL=20210328;BYDAY=WE
    ORGANIZER;CN=Test:MAILTO:testOrga@proton.me
    ATTENDEE;CN=Test:MAILTO:testAttendee@proton.me
    SUMMARY:Test this out
    DESCRIPTION:Test this description
    LOCATION:Test this location
    UID:123456970bluemnday@recruitee.com
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = IcsSurgeryUtils.cleanIcs(iCalString, timeZoneId = "Europe/Paris", isOpeningFromProtonMail = true)

        assertThat(cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful).isTrue()

        val expectedUid = "original-uid-123456970bluemnday@recruitee.com-sha1-uid-2f56b753fd19967006672eaa365fec86821c8e74"
        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar

            assertThat(iCalendar).isNotNull()
            assertThat(iCalendar?.events?.firstOrNull()?.recurrenceId).isNull()
            assertThat(iCalendar?.events?.firstOrNull()?.alarms).isNullOrEmpty()
            assertThat(iCalendar?.events?.firstOrNull()?.uid?.value).isEqualTo(expectedUid)
        }
    }

    @Test
    fun `ics import from file generate UID`() {
        val file = File("./src/test/resources/importWithNoUid.ics")

        assertThat(file).isNotNull()

        val inputStream: InputStream? = this.javaClass.classLoader?.getResourceAsStream(file.name)
        val bufferedReader = BufferedReader(InputStreamReader(inputStream))
        val iCalString = bufferedReader.use { it.readText() }

        val cleanIcsResult = IcsSurgeryUtils.cleanIcs(iCalString, timeZoneId = "Europe/Paris", isOpeningFromProtonMail = true)

        assertThat(cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful).isTrue()

        val event = (cleanIcsResult as IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful).iCalendar?.events?.firstOrNull()
        assertThat(event?.uid?.value).isEqualTo("sha1-uid-45bcf24f9032a3f0865fa55492876b770e96e5ab")
    }

    @Test
    fun `ics import from file generate UID with short original UID`() {
        val file = File("./src/test/resources/importWithShortUid.ics")

        assertThat(file).isNotNull()

        val inputStream: InputStream? = this.javaClass.classLoader?.getResourceAsStream(file.name)
        val bufferedReader = BufferedReader(InputStreamReader(inputStream))
        val iCalString = bufferedReader.use { it.readText() }

        val cleanIcsResult = IcsSurgeryUtils.cleanIcs(iCalString, timeZoneId = "Europe/Paris", isOpeningFromProtonMail = true)

        assertThat(cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful).isTrue()

        val event = (cleanIcsResult as IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful).iCalendar?.events?.firstOrNull()
        assertThat(event?.uid?.value).isEqualTo("original-uid-123456970bluemnday@recruitee.com-sha1-uid-db2828aaf1b1084fbf004530e2008f945fb796fd")
    }

    @Test
    fun `ics import from file generate UID with long original UID`() {
        val file = File("./src/test/resources/importWithLongUid.ics")

        assertThat(file).isNotNull()

        val inputStream: InputStream? = this.javaClass.classLoader?.getResourceAsStream(file.name)
        val bufferedReader = BufferedReader(InputStreamReader(inputStream))
        val iCalString = bufferedReader.use { it.readText() }

        val cleanIcsResult = IcsSurgeryUtils.cleanIcs(iCalString, timeZoneId = "Europe/Paris", isOpeningFromProtonMail = true)

        assertThat(cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful).isTrue()

        val event = (cleanIcsResult as IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful).iCalendar?.events?.firstOrNull()
        assertThat(event?.uid?.value).isEqualTo("original-uid-FmcWKJ0q0eeNWIN4OLZ8yJnSDdC8DT9CndSxOnnPC47VWjQHu0psXB25lZuCt4EWsWAtgmCPWe1Wa0AIL0y8rlPn0qbB05u3WuyOst8XYkJNWz6gYx@recruitee.com-sha1-uid-a058b52a132530144c9fa59597036c8aac8ae550")
    }

    @Test
    fun `test all day date without VALUE=DATE`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Calendar Labs//Calendar 1.0//EN
    CALSCALE:GREGORIAN
    METHOD:REQUEST
    X-WR-CALNAME:US Holidays
    X-WR-TIMEZONE:Etc/GMT
    BEGIN:VEVENT
    DTSTART:20220101
    DTEND:20220102
    UID:636a37c4d8b361667905476@calendarlabs.com
    DTSTAMP:20221108T110436Z
    SEQUENCE:0
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = IcsSurgeryUtils.cleanIcs(iCalString, timeZoneId = "Europe/Paris")

        assertThat(cleanIcsResult).isInstanceOf(IcsSurgeryUtils.HandleIcsResult.Error.Invalid.DateOrDateTimeProperty::class)
    }

    @Test
    fun `test Date time formatted as Date`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Calendar Labs//Calendar 1.0//EN
    CALSCALE:GREGORIAN
    METHOD:REQUEST
    X-WR-CALNAME:US Holidays
    X-WR-TIMEZONE:Etc/GMT
    BEGIN:VEVENT
    DTSTART;VALUE=DATE-TIME:20220101
    DTEND;VALUE=DATE-TIME:20220102
    UID:636a37c4d8b361667905476@calendarlabs.com
    DTSTAMP:20221108T110436Z
    SEQUENCE:0
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = IcsSurgeryUtils.cleanIcs(iCalString, timeZoneId = "Europe/Paris")

        assertThat(cleanIcsResult).isInstanceOf(IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful::class)

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar

            assertThat(iCalendar).isNotNull()
            // TODO Output should be DTSTART;VALUE=DATE-TIME:20220101T000000Z
            assertThat(iCalendar?.printToString()?.contains("DTSTART;VALUE=DATE-TIME:20220101T000000Z")).isEqualTo(true)
            assertThat(iCalendar?.printToString()?.contains("DTEND;VALUE=DATE-TIME:20220101T000000Z")).isEqualTo(true)
        }
    }

    @Test
    fun `test DTEND before DTSTART`() {

        val iCalString = """
    BEGIN:VCALENDAR
    PRODID:-//Google Inc//Google Calendar 70.9054//EN
    VERSION:2.0
    CALSCALE:GREGORIAN
    METHOD:REQUEST
    BEGIN:VEVENT
    DTSTART:20211130T080000Z
    DTEND:20211030T090000Z
    DTSTAMP:20211129T130415Z
    UID:1o2b18ap2lqbcgckgugthjic1de1@google.com
    CREATED:20211129T130414Z
    DESCRIPTION:Lorem ipsum dolor sit amet\, consectetur adipiscing elit\, sed
     do eiusmod tempor incididunt ut labore et dolore magna aliqua.Lorem ipsum d
    LAST-MODIFIED:20211129T130414Z
    SEQUENCE:0
    STATUS:CONFIRMED
    SUMMARY:End date is earlier2
    TRANSP:OPAQUE
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = IcsSurgeryUtils.cleanIcs(iCalString, timeZoneId = "Europe/Paris")

        assertThat(cleanIcsResult).isInstanceOf(IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful::class)

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar

            assertThat(iCalendar).isNotNull()
            assertThat(iCalendar?.printToString()?.contains("DTEND:20211130T080000Z")).isEqualTo(true)
        }
    }

    @Test
    fun `test DTSTAMP with VALUE=DATE`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    METHOD:REQUEST
    BEGIN:VEVENT
    DTSTART;TZID=America/New_York:20221012T171500
    DTEND;TZID=America/New_York:20221012T182500
    DTSTAMP;VALUE=DATE:20200131
    UID:11353R6@voltigeursbourget.com
    SEQUENCE:0
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = IcsSurgeryUtils.cleanIcs(iCalString, timeZoneId = "Europe/Paris")

        assertThat(cleanIcsResult).isInstanceOf(IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful::class)

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar

            assertThat(iCalendar).isNotNull()
            assertThat(iCalendar?.printToString()?.contains("DTSTAMP:20200131T000000Z")).isEqualTo(true)
        }
    }

    @Test
    fun `test DTSTAMP with floating date time`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    METHOD:REQUEST
    BEGIN:VEVENT
    DTSTART;TZID=America/New_York:20221012T171500
    DTEND;TZID=America/New_York:20221012T182500
    DTSTAMP:20200131T151111
    UID:11353R6@voltigeursbourget.com
    SEQUENCE:0
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = IcsSurgeryUtils.cleanIcs(iCalString, timeZoneId = "Europe/Paris")

        assertThat(cleanIcsResult).isInstanceOf(IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful::class)

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar

            assertThat(iCalendar).isNotNull()
            assertThat(iCalendar?.printToString()?.contains("DTSTAMP:20200131T151111Z")).isEqualTo(true)
        }
    }

    @Test
    fun `test DTSTAMP with TZID`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    METHOD:REQUEST
    BEGIN:VEVENT
    DTSTART;TZID=America/New_York:20221012T171500
    DTEND;TZID=America/New_York:20221012T182500
    DTSTAMP;TZID=America/Montevideo:20200131T151111
    UID:11353R6@voltigeursbourget.com
    SEQUENCE:0
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = IcsSurgeryUtils.cleanIcs(iCalString, timeZoneId = "Europe/Paris")

        assertThat(cleanIcsResult).isInstanceOf(IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful::class)

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar

            assertThat(iCalendar).isNotNull()
            assertThat(iCalendar?.printToString()?.contains("DTSTAMP:20200131T181111Z")).isEqualTo(true)
        }
    }

    @Test
    fun `test DTSTAMP with TZID and VALUE=DATE`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    METHOD:REQUEST
    BEGIN:VEVENT
    DTSTART;TZID=America/New_York:20221012T171500
    DTEND;TZID=America/New_York:20221012T182500
    DTSTAMP;TZID=America/Montevideo;VALUE=DATE:20200131
    UID:11353R6@voltigeursbourget.com
    SEQUENCE:0
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = IcsSurgeryUtils.cleanIcs(iCalString, timeZoneId = "Europe/Paris")

        assertThat(cleanIcsResult).isInstanceOf(IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful::class)

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar

            assertThat(iCalendar).isNotNull()
            assertThat(iCalendar?.printToString()?.contains("DTSTAMP:20200131T030000Z")).isEqualTo(true)
        }
    }

    @Test
    fun `test DTSTAMP with TZID and Zulu marker`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    METHOD:REQUEST
    BEGIN:VEVENT
    DTSTART;TZID=America/New_York:20221012T171500
    DTEND;TZID=America/New_York:20221012T182500
    DTSTAMP;TZID=Europe/Brussels:20221026T063929Z
    UID:11353R6@voltigeursbourget.com
    SEQUENCE:0
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = IcsSurgeryUtils.cleanIcs(iCalString, timeZoneId = "Europe/Paris")

        assertThat(cleanIcsResult).isInstanceOf(IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful::class)

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar

            assertThat(iCalendar).isNotNull()
            assertThat(iCalendar?.printToString()?.contains("DTSTAMP:20221026T063929Z")).isEqualTo(true)
        }
    }

    @Test
    fun `test empty TZID`() {

        val iCalString = """
    BEGIN:VCALENDAR
    TZID:Europe/Zurich
    METHOD:REQUEST
    BEGIN:VEVENT
    UID:random
    DTSTART;TZID=:20221027T103000Z
    DTEND;TZID=:20221027T113000Z
    DTSTAMP;TZID=:20221027T022510Z
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = IcsSurgeryUtils.cleanIcs(iCalString, timeZoneId = "Europe/Paris")

        assertThat(cleanIcsResult).isInstanceOf(IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful::class)

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar

            assertThat(iCalendar).isNotNull()
            assertThat(iCalendar?.printToString()?.contains("DTSTART:20221027T103000Z")).isEqualTo(true)
            assertThat(iCalendar?.printToString()?.contains("DTEND:20221027T113000Z")).isEqualTo(true)
            assertThat(iCalendar?.printToString()?.contains("DTSTAMP:20221027T022510Z")).isEqualTo(true)
        }
    }

    @Test
    fun `test floating Date time with no X-WR-TIMEZONE`() {

        val iCalString = """
    BEGIN:VCALENDAR
    METHOD:REQUEST
    PRODID:-//xxxx//NONSGML agenda//FR
    VERSION:2.0
    X-WR-CALNAME: xxxx
    BEGIN:VTIMEZONE
    TZID:Europe/Paris
    BEGIN:DAYLIGHT
    TZOFFSETFROM:+0100
    TZOFFSETTO:+0200
    TZNAME:CEST
    DTSTART:19700329T020000
    RRULE:FREQ=YEARLY;BYDAY=-1SU;BYMONTH=3
    END:DAYLIGHT
    BEGIN:STANDARD
    TZOFFSETFROM:+0200
    TZOFFSETTO:+0100
    TZNAME:CET
    DTSTART:19701025T030000
    RRULE:FREQ=YEARLY;BYDAY=-1SU;BYMONTH=10
    END:STANDARD
    END:VTIMEZONE
    BEGIN:VEVENT
    UID:hwkhwkhteke
    DTSTAMP:20210920T080000Z
    DTSTART:20210920T080000
    DTEND:20210920T120000
    SUMMARY:TD AN0501
    DESCRIPTION:xxxx
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = IcsSurgeryUtils.cleanIcs(iCalString, timeZoneId = "Europe/Paris")

        assertThat(cleanIcsResult).isInstanceOf(IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful::class)

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar

            assertThat(iCalendar).isNotNull()
            assertThat(iCalendar?.printToString()?.contains("DTSTART;TZID=Europe/Paris:20210920T080000")).isEqualTo(true)
            assertThat(iCalendar?.printToString()?.contains("DTEND;TZID=Europe/Paris:20210920T120000")).isEqualTo(true)
        }
    }

    @Test
    fun `test floating Date time with X-WR-TIMEZONE`() {

        val iCalString = """
    BEGIN:VCALENDAR
    METHOD:REQUEST
    PRODID;X-RICAL-TZSOURCE=TZINFO:-//com.denhaven2/NONSGML ri_cal gem//EN
    CALSCALE:GREGORIAN
    VERSION:2.0
    X-PUBLISHED-TTL:PT10M
    X-WR-TIMEZONE:Asia/Taipei
    BEGIN:VEVENT
    DTSTART;VALUE=DATE-TIME:20210824T100000
    DTEND;VALUE=DATE-TIME:20210824T145000
    DTSTAMP:20230109T080000Z
    UID:1132139459
    URL:xxxx
    SUMMARY:yyyy
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = IcsSurgeryUtils.cleanIcs(iCalString, timeZoneId = "Europe/Paris")

        assertThat(cleanIcsResult).isInstanceOf(IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful::class)

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar

            assertThat(iCalendar).isNotNull()
            assertThat(iCalendar?.printToString()?.contains("DTSTART;TZID=Asia/Taipei:20210824T100000")).isEqualTo(true)
            assertThat(iCalendar?.printToString()?.contains("DTEND;TZID=Asia/Taipei:20210824T145000")).isEqualTo(true)
        }
    }
}