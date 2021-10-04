package me.proton.android.calendar.mocks

import me.proton.core.domain.entity.UserId
import me.proton.core.user.domain.entity.Delinquent
import me.proton.core.user.domain.entity.Role

/**
 * Global mocks
 */

val userId = UserId("userId")

const val defaultTimezone = "Europe/Paris"
const val defaultEventDuration = 30

/**
 * Calendar mocks
 */

const val calendarId = "calendarId"
const val calendarName = "calendarName"
const val calendarDescription = "calendarDescription"
const val calendarColor = "#657EE4"
const val calendarDisplay = 1
const val calendarFlags = 1
const val calendarType = 0

const val calendarSettingsId = "calendarSettingsId"

const val weekLength = 7
const val displayWeekNumber = 1
const val autoDetectPrimaryTimezone = 1
const val viewPreference = 0

/**
 * Event mocks
 */

const val eventId = "eventId"
const val singleEditEventId = "singleEditEventId"

const val eventUid = "eventUid@proton.me"

const val attendeeEmail = "attendee@email.com"
const val attendeeName = "attendeeName"

const val sharedEventId = "sharedEventId"
const val calendarKeyPacket = "calendarKeyPacket"
const val sharedKeyPacket = "sharedKeyPacket"

/**
 * User mocks
 */

const val weekStart = 1
const val dateFormat = 1
const val timeFormat = 1

const val userEmail = "userEmail@email.com"
const val userName = "userName"
const val userDisplayName = "displayName"

const val currency = "EUR"
const val credit = 50
const val usedSpace = 0L
const val maxSpace = 3096L
const val maxUpload = 3096L
const val private = true
const val services = 1
const val subscribed = 1

val role = Role.NoOrganization
val delinquent = Delinquent.None

/**
 * Event Ics mocks
 */

// Single part day event
val baseIcs = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Proton Technologies//AndroidCalendar 0.25.1//EN
    BEGIN:VTIMEZONE
    TZID:Europe/Paris
    END:VTIMEZONE
    BEGIN:VEVENT
    DTSTAMP:20210914T132502Z
    UID:eventUid@proton.me
    STATUS:CONFIRMED
    SEQUENCE:0
    DTSTART;TZID=Europe/Paris:20210914T153000
    DTEND;TZID=Europe/Paris:20210914T160000
    SUMMARY:Single event
    BEGIN:VALARM
    ACTION:DISPLAY
    TRIGGER;RELATED=START:-PT15M
    END:VALARM
    END:VEVENT
    END:VCALENDAR
""".trimIndent()

// Daily recurring 10 times part day event
val recurringIcs = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Proton Technologies//AndroidCalendar 0.25.1//EN
    BEGIN:VTIMEZONE
    TZID:Europe/Paris
    END:VTIMEZONE
    BEGIN:VEVENT
    DTSTAMP:20210914T132502Z
    UID:eventUid@proton.me
    STATUS:CONFIRMED
    SEQUENCE:0
    RRULE:FREQ=DAILY;COUNT=10
    DTSTART;TZID=Europe/Paris:20210914T153000
    DTEND;TZID=Europe/Paris:20210914T160000
    SUMMARY:Recurring event
    BEGIN:VALARM
    ACTION:DISPLAY
    TRIGGER;RELATED=START:-PT15M
    END:VALARM
    END:VEVENT
    END:VCALENDAR
""".trimIndent()

// All day single edit replacing 2nd occurrence
val singleEditIcs = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Proton Technologies//AndroidCalendar 0.25.1//EN
    BEGIN:VTIMEZONE
    TZID:Europe/Paris
    END:VTIMEZONE
    BEGIN:VEVENT
    DTSTAMP:20210914T132502Z
    UID:eventUid@proton.me
    STATUS:CONFIRMED
    SEQUENCE:0
    DTSTART;VALUE=DATE:20210915
    DTEND;VALUE=DATE:20210916
    RECURRENCE-ID;TZID=Europe/Paris:20210915T153000
    SUMMARY:Single edit
    BEGIN:VALARM
    ACTION:DISPLAY
    TRIGGER;RELATED=START:-PT15M
    END:VALARM
    END:VEVENT
    END:VCALENDAR
""".trimIndent()
