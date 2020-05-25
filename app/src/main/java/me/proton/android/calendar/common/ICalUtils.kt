package me.proton.android.calendar.common

import biweekly.Biweekly
import biweekly.ICalendar
import biweekly.component.VEvent
import biweekly.component.VTimezone
import biweekly.io.TimezoneAssignment
import com.google.crypto.tink.subtle.Random
import me.proton.android.calendar.BuildConfig
import me.proton.android.calendar.common.ICalUtils.generateProtonProdId
import java.lang.Exception
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.*


object ICalUtils {

    fun parseICalString(iCalendar: String): ICalendar? {
        return try {
            Biweekly.parse(iCalendar).first().also { normaliseICalendar(it) }
        } catch (e: Exception) {
            TimberLogger.e("error parsing iCalendar", e)
            null
        }
    }

    private fun normaliseICalendar(calendar: ICalendar) {

        // TODO manipulate calendar, like add DTEND?

    }

    /**
     * Takes iCalendar parts split according to "the matrix" and returns one iCalendar object.
     */
    fun mergeCalendarPartsIntoICalendar(calendarStrings: List<String>): ICalendar? {
        return calendarStrings.mapNotNull { parseICalString(it) }.reduce { sum, element -> mergeICalendars(sum, element) }
    }

    /**
     * Takes one iCalendar object and splits it according to "the matrix".
     */
    fun splitICalendarIntoParts(iCalendar: ICalendar): CalendarSplit {

        // TODO Attendees Part

        val event = iCalendar.events.first()
        return CalendarSplit(
            sharedPart = VEvent().run {
                setUid(event.uid)
                setCreated(event.created)
                setLastModified(event.lastModified)
//                setDateTimeStamp(event.dateTimeStamp)
                setDateStart(event.dateStart)
                setDateEnd(event.dateEnd)
                setRecurrenceRule(event.recurrenceRule)
                setRecurrenceId(event.recurrenceId)
                setSequence(event.sequence)
                event.exceptionDates.forEach {
                    addExceptionDates(it)
                }

                val calendar = wrapInICalendar()

                // copy timezone assignments for DateStart & DateEnd
                if (iCalendar.timezoneInfo.getTimezone(event.dateStart) != null) {
                    calendar.timezoneInfo.setTimezone(event.dateStart, iCalendar.timezoneInfo.getTimezone(event.dateStart))
                }
                if (iCalendar.timezoneInfo.getTimezone(event.dateEnd) != null) {
                    calendar.timezoneInfo.setTimezone(event.dateEnd, iCalendar.timezoneInfo.getTimezone(event.dateEnd))
                }
                // delete timezone info created automatically when setting timezones
                calendar.timezoneInfo.timezones.clear()

                calendar
            },
            sharedPartToEncrypt = VEvent().run {
                setUid(event.uid)
                setCreated(event.created)
                setLastModified(event.lastModified)
//                setDateTimeStamp(event.dateTimeStamp)
                setDescription(event.description) // TODO force substring to be max VALIDATION_EVENT_DESCRIPTION_MAX_LENGTH long?
                setSummary(event.summary) // TODO force substring to be max VALIDATION_EVENT_SUMMARY_MAX_LENGTH long?
                setLocation(event.location) // TODO force substring to be max VALIDATION_EVENT_LOCATION_MAX_LENGTH long?
                wrapInICalendar()
            },
            calendarPart = if (event.status != null || event.transparency != null) {
                VEvent().run {
                    setUid(event.uid)
                    setCreated(event.created)
                    setLastModified(event.lastModified)
//                    setDateTimeStamp(event.dateTimeStamp)
                    setStatus(event.status) // TODO set to CONFIRMED when creating event
                    setTransparency(event.transparency)
                    wrapInICalendar()
                }
            } else null,
            calendarPartToEncrypt = if (event.comments.isNotEmpty()) {
                VEvent().run {
                    setUid(event.uid)
                    setCreated(event.created)
                    setLastModified(event.lastModified)
//                    setDateTimeStamp(event.dateTimeStamp)
                    event.comments.forEach {
                        addComment(it)
                    }
                    // TODO here should be inserted "all the rest" of the properties
                    wrapInICalendar()
                }
            } else null,
            personalPart = if (event.alarms.isNotEmpty()) {
                VEvent().run {
                    setUid(event.uid)
                    setCreated(event.created)
                    setLastModified(event.lastModified)
//                    setDateTimeStamp(event.dateTimeStamp)
                    event.alarms.forEach {
                        addAlarm(it)
                    }
                    wrapInICalendar()
                }
            } else null
        )
    }

    /**
     * This function merges only first top-level component of type VEvent.
     *
     * In the future we can extend this to support VTodo and custom components.
     */
    private fun mergeICalendars(left: ICalendar, right: ICalendar) : ICalendar {

        TimberLogger.v("merging left: ${left.printToString()}")
        TimberLogger.v("merging right: ${right.printToString()}")

        // copy components and properties from the only event there is
        right.events.first().components.forEach {
            val components = it.value
            components.forEach {
                if (it !in left.events.first().components.values()) {
                    left.events.first().addComponent(it)
                }
            }
        }
        right.events.first().properties.forEach {
            val properties = it.value
            properties.forEach {
                left.events.first().setProperty(it)
            }
        }

        // copy additional metadata that is not yet set
        if (left.timezoneInfo.getTimezone(left.events[0].dateStart) == null) {
            left.timezoneInfo.setTimezone(left.events[0].dateStart, right.timezoneInfo.getTimezone(left.events[0].dateStart))
        }
        if (left.timezoneInfo.getTimezone(left.events[0].dateEnd) == null) {
            left.timezoneInfo.setTimezone(left.events[0].dateEnd, right.timezoneInfo.getTimezone(left.events[0].dateEnd))
        }
        if (left.productId == null) {
            left.productId = right.productId
        }

        return left
    }

    fun createNewEvent() = VEvent().apply {
        setUid(generateProtonUid())
    }

    /**
     * Generates Proton UID for new ICalendar components.
     */
    fun generateProtonUid() = "${com.google.crypto.tink.subtle.Base64.urlSafeEncode(Random.randBytes(21))}@proton.me"

    /**
     * Generates Proton Product Identifier.
     */
    fun generateProtonProdId() = "-//Proton Technologies//${API_APPLICATION_NAME} ${BuildConfig.VERSION_NAME}//EN"

    /**
     * Generates offline CalendarID to use before it's successfully sent to server.
     */
    fun generateOfflineEventId() = "$OFFLINE_EVENT_ID_PREFIX${UUID.randomUUID()}${UUID.randomUUID()}${UUID.randomUUID()}"

}

data class CalendarSplit(
    val sharedPart: ICalendar,
    val sharedPartToEncrypt: ICalendar,
    val calendarPart: ICalendar?,
    val calendarPartToEncrypt: ICalendar?, // TODO all the other properties not mentioned in matrix should be here
    val personalPart: ICalendar?
)

/**
 * Creates new ICalendar object and sets this VEvent as only event.
 */
fun VEvent.wrapInICalendar(): ICalendar {
    val calendar = ICalendar()
    calendar.setProductId(generateProtonProdId())
    calendar.addEvent(this)
    return calendar
}

fun ICalendar.printToString() : String = Biweekly.write(this).go()

fun LocalDate.toDate(timeZoneId: String? = null): Date = Date.from(this.atStartOfDay(ZoneId.of(timeZoneId ?: "UTC")).toInstant())

fun DayOfWeek.toBiweeklyDayOfWeek(): biweekly.util.DayOfWeek {
    return biweekly.util.DayOfWeek.values()[(this.ordinal + 1) % 7]
}

fun ZonedDateTime.formatDate(timeZoneId: String): String = this.toLocalDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL))

fun ZonedDateTime.formatTime(timeZoneId: String): String = this.toLocalTime().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

fun VEvent.setStart(date: LocalDate) {
    this.setDateStart(date.toDate(), false)
}

fun VEvent.setEnd(date: LocalDate) {
    this.setDateEnd(date.toDate(), false)
}

fun VEvent.setStart(date: LocalDate, time: LocalTime, timeZoneId: String? = "UTC") {
    this.setDateStart(Date.from(LocalDateTime.of(date, time.truncatedTo(ChronoUnit.MINUTES)).atZone(ZoneId.of(timeZoneId)).toInstant()), true)
}

fun VEvent.setEnd(date: LocalDate, time: LocalTime, timeZoneId: String? = "UTC") {
    this.setDateEnd(Date.from(LocalDateTime.of(date, time.truncatedTo(ChronoUnit.MINUTES)).atZone(ZoneId.of(timeZoneId)).toInstant()), true)
}

/**
 * Sets or clears TimeZone for Date Start.
 */
fun ICalendar.setStartTimeZone(timeZoneId: String?) {
    this.events.first()?.dateStart?.let { this.timezoneInfo.setTimezone(this.events.first().dateStart, if (timeZoneId == null) null else TimezoneAssignment(TimeZone.getTimeZone(timeZoneId), VTimezone(timeZoneId))) }
}

/**
 * Sets or clears TimeZone for Date End.
 */
fun ICalendar.setEndTimeZone(timeZoneId: String?) {
    this.events.first()?.dateEnd?.let { this.timezoneInfo.setTimezone(this.events.first().dateEnd, if (timeZoneId == null) null else TimezoneAssignment(TimeZone.getTimeZone(timeZoneId), VTimezone(timeZoneId))) }
}

fun ICalendar.setDefaultTimeZone(timeZoneId: String?) {
    this.timezoneInfo.defaultTimezone = if (timeZoneId == null) null else TimezoneAssignment(TimeZone.getTimeZone(timeZoneId), VTimezone(timeZoneId))
}

/**
 * Sets start and end timezones, preserving the original local datetimes.
 */
fun ICalendar.adjustStartEndTimeZones(currentDateTimeTimezoneId: String, timeZoneId: String) {


    val event = this.events.first()

    TimberLogger.d("adjusting timezone from ${currentDateTimeTimezoneId} to $timeZoneId")
//    TimberLogger.d("current start timezone $${this.timezoneInfo.getTimezone(event.dateStart)?.timeZone?.id}")
//    TimberLogger.d("current end timezone $${this.timezoneInfo.getTimezone(event.dateStart)?.timeZone?.id}")

    val endTimeZoneId = currentDateTimeTimezoneId//this.timezoneInfo.getTimezone(event.dateEnd)?.timeZone?.id ?: this.timezoneInfo.defaultTimezone?.timeZone?.id ?: "UTC"

    TimberLogger.d("adjusting using timezone $currentDateTimeTimezoneId, $endTimeZoneId")

    event.setStart(event.getStart(currentDateTimeTimezoneId)!!.toLocalDate(), event.getStart(currentDateTimeTimezoneId)!!.toLocalTime(), timeZoneId)
    event.setEnd(event.getEnd(endTimeZoneId)!!.toLocalDate(), event.getEnd(endTimeZoneId)!!.toLocalTime(), timeZoneId)

    this.setStartTimeZone(timeZoneId)
    this.setEndTimeZone(timeZoneId)

    this.timezoneInfo.timezones.clear()

}

/**
 * Removes Timezone Assignments and sets correct DTEND according to standard, not GUI form.
 *
 * @param timeZoneId needed to correctly interpret Dates if we are about to remove timezone info
 */
fun ICalendar.adjustAllDayEvent(timeZoneId: String) {
    this.timezoneInfo.timezones.clear()
    this.events.first().apply {
        setStart(this.getStart(timeZoneId)!!.toLocalDate())
        setEnd(this.getEnd(timeZoneId)!!.toLocalDate().plusDays(1))
    }
}

fun VEvent.getStart(timeZoneId: String? = "UTC"): ZonedDateTime? {
    return if (this.dateStart?.value != null) ZonedDateTime.ofInstant(this.dateStart.value.toInstant(), ZoneId.of(timeZoneId)) else null
}

fun VEvent.getEnd(timeZoneId: String? = "UTC"): ZonedDateTime? {
    return if (this.dateEnd?.value != null) ZonedDateTime.ofInstant(this.dateEnd.value.toInstant(), ZoneId.of(timeZoneId)) else null
}
