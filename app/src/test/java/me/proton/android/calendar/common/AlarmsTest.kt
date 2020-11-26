package me.proton.android.calendar.common

import assertk.assertThat
import assertk.assertions.isEqualTo
import me.proton.android.calendar.BaseTest
import org.junit.jupiter.api.Test
import java.time.*

internal class AlarmsTest : BaseTest() {

    @Test
    fun `calculate alarms for regular part-day event`() {

        val event = eventForICalString(
            """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    DTSTART;TZID=UTC:20201126T200000
    DTEND;TZID=UTC:20201126T203000
    SEQUENCE:1
    SUMMARY:20:00
    STATUS:CONFIRMED
    UID:1FkJarw0ZGxECCG955eRylOdsdVN@proton.me
    DTSTAMP:20201126T165438Z
    BEGIN:VALARM
    TRIGGER:PT0S
    ACTION:DISPLAY
    END:VALARM
    BEGIN:VALARM
    TRIGGER:-PT15M
    ACTION:DISPLAY
    END:VALARM
    BEGIN:VALARM
    TRIGGER:-PT30M
    ACTION:DISPLAY
    END:VALARM
    BEGIN:VALARM
    TRIGGER:-PT60M
    ACTION:DISPLAY
    END:VALARM
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()
        )

        // alarms for part-day event fire at the same point-in-time no matter the timezone

        val alarmsUtc = ICalUtils.calculateAlarmEntities(event, "UTC", "member-id")

        assertThat(alarmsUtc.size).isEqualTo(4)

        assertThat(alarmsUtc[0].occurrence).isEqualTo(
            ZonedDateTime.of(
                LocalDate.of(2020, 11, 26),
                LocalTime.of(20, 0, 0),
                ZoneId.of("UTC")
            ).toEpochSecond()
        )
        assertThat(alarmsUtc[0].action).isEqualTo(2)
        assertThat(alarmsUtc[0].trigger).isEqualTo("PT0S")

        assertThat(alarmsUtc[1].occurrence).isEqualTo(
            ZonedDateTime.of(
                LocalDate.of(2020, 11, 26),
                LocalTime.of(19, 45, 0),
                ZoneId.of("UTC")
            ).toEpochSecond()
        )
        assertThat(alarmsUtc[1].action).isEqualTo(2)
        assertThat(alarmsUtc[1].trigger).isEqualTo("-PT15M")

        assertThat(alarmsUtc[2].occurrence).isEqualTo(
            ZonedDateTime.of(
                LocalDate.of(2020, 11, 26),
                LocalTime.of(19, 30, 0),
                ZoneId.of("UTC")
            ).toEpochSecond()
        )
        assertThat(alarmsUtc[2].action).isEqualTo(2)
        assertThat(alarmsUtc[2].trigger).isEqualTo("-PT30M")

        assertThat(alarmsUtc[3].occurrence).isEqualTo(
            ZonedDateTime.of(
                LocalDate.of(2020, 11, 26),
                LocalTime.of(19, 0, 0),
                ZoneId.of("UTC")
            ).toEpochSecond()
        )
        assertThat(alarmsUtc[3].action).isEqualTo(2)
        assertThat(alarmsUtc[3].trigger).isEqualTo("-PT60M")

        val alarmsZurich = ICalUtils.calculateAlarmEntities(event, "Europe/Zurich", "member-id")

        assertThat(alarmsZurich.size).isEqualTo(4)

        assertThat(alarmsZurich[0].occurrence).isEqualTo(
            ZonedDateTime.of(
                LocalDate.of(2020, 11, 26),
                LocalTime.of(21, 0, 0),
                ZoneId.of("Europe/Zurich")
            ).toEpochSecond()
        )
        assertThat(alarmsZurich[0].action).isEqualTo(2)
        assertThat(alarmsZurich[0].trigger).isEqualTo("PT0S")

        assertThat(alarmsZurich[1].occurrence).isEqualTo(
            ZonedDateTime.of(
                LocalDate.of(2020, 11, 26),
                LocalTime.of(20, 45, 0),
                ZoneId.of("Europe/Zurich")
            ).toEpochSecond()
        )
        assertThat(alarmsZurich[1].action).isEqualTo(2)
        assertThat(alarmsZurich[1].trigger).isEqualTo("-PT15M")

        assertThat(alarmsZurich[2].occurrence).isEqualTo(
            ZonedDateTime.of(
                LocalDate.of(2020, 11, 26),
                LocalTime.of(20, 30, 0),
                ZoneId.of("Europe/Zurich")
            ).toEpochSecond()
        )
        assertThat(alarmsZurich[2].action).isEqualTo(2)
        assertThat(alarmsZurich[2].trigger).isEqualTo("-PT30M")

        assertThat(alarmsZurich[3].occurrence).isEqualTo(
            ZonedDateTime.of(
                LocalDate.of(2020, 11, 26),
                LocalTime.of(20, 0, 0),
                ZoneId.of("Europe/Zurich")
            ).toEpochSecond()
        )
        assertThat(alarmsZurich[3].action).isEqualTo(2)
        assertThat(alarmsZurich[3].trigger).isEqualTo("-PT60M")

    }

    @Test
    fun `calculate alarms for regular all-day event`() {

        val event = eventForICalString(
            """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    DTSTART;VALUE=DATE:20201130
    SEQUENCE:1
    SUMMARY:all-day 3 notifs
    STATUS:CONFIRMED
    UID:JeEXmSg8RbKD8VWKitbcvdHp7UFD@proton.me
    DTSTAMP:20201126T180559Z
    DTEND;VALUE=DATE:20201130
    BEGIN:VALARM
    TRIGGER:PT9H
    ACTION:DISPLAY
    END:VALARM
    BEGIN:VALARM
    TRIGGER:-PT15H30M
    ACTION:DISPLAY
    END:VALARM
    BEGIN:VALARM
    TRIGGER:-P2DT16H
    ACTION:DISPLAY
    END:VALARM
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()
        )

        // on the same day at 9:00
        // 1 day before at 8:30
        // 3 days before at 8:00

        // in different timezones alarms should fire at the same local time of the same day

        val alarms = ICalUtils.calculateAlarmEntities(event, "UTC", "member-id")

        assertThat(alarms.size).isEqualTo(3)

        TestsLogger.d("${Instant.ofEpochSecond(alarms[0].occurrence)}")

        assertThat(alarms[0].occurrence).isEqualTo(
            ZonedDateTime.of(
                LocalDate.of(2020, 11, 30),
                LocalTime.of(9, 0, 0),
                ZoneId.of("UTC")
            ).toEpochSecond()
        )
        assertThat(alarms[0].action).isEqualTo(2)
        assertThat(alarms[0].trigger).isEqualTo("PT9H")

        assertThat(alarms[1].occurrence).isEqualTo(
            ZonedDateTime.of(
                LocalDate.of(2020, 11, 29),
                LocalTime.of(8, 30, 0),
                ZoneId.of("UTC")
            ).toEpochSecond()
        )
        assertThat(alarms[1].action).isEqualTo(2)
        assertThat(alarms[1].trigger).isEqualTo("-PT15H30M")

        assertThat(alarms[2].occurrence).isEqualTo(
            ZonedDateTime.of(
                LocalDate.of(2020, 11, 27),
                LocalTime.of(8, 0, 0),
                ZoneId.of("UTC")
            ).toEpochSecond()
        )
        assertThat(alarms[2].action).isEqualTo(2)
        assertThat(alarms[2].trigger).isEqualTo("-P2DT16H")

        val alarmsZurich = ICalUtils.calculateAlarmEntities(event, "Europe/Zurich", "member-id")

        assertThat(alarms.size).isEqualTo(3)

        TestsLogger.d("${Instant.ofEpochSecond(alarmsZurich[0].occurrence)}")

        assertThat(alarmsZurich[0].occurrence).isEqualTo(
            ZonedDateTime.of(
                LocalDate.of(2020, 11, 30),
                LocalTime.of(9, 0, 0),
                ZoneId.of("Europe/Zurich")
            ).toEpochSecond()
        )
        assertThat(alarmsZurich[0].action).isEqualTo(2)
        assertThat(alarmsZurich[0].trigger).isEqualTo("PT9H")

        assertThat(alarmsZurich[1].occurrence).isEqualTo(
            ZonedDateTime.of(
                LocalDate.of(2020, 11, 29),
                LocalTime.of(8, 30, 0),
                ZoneId.of("Europe/Zurich")
            ).toEpochSecond()
        )
        assertThat(alarmsZurich[1].action).isEqualTo(2)
        assertThat(alarmsZurich[1].trigger).isEqualTo("-PT15H30M")

        assertThat(alarmsZurich[2].occurrence).isEqualTo(
            ZonedDateTime.of(
                LocalDate.of(2020, 11, 27),
                LocalTime.of(8, 0, 0),
                ZoneId.of("Europe/Zurich")
            ).toEpochSecond()
        )
        assertThat(alarmsZurich[2].action).isEqualTo(2)
        assertThat(alarmsZurich[2].trigger).isEqualTo("-P2DT16H")
    }

}
