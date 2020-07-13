package me.proton.android.calendar.common

import biweekly.Biweekly
import biweekly.ICalendar
import biweekly.component.VEvent
import biweekly.component.VTimezone
import biweekly.io.TimezoneAssignment
import biweekly.property.Status
import com.google.crypto.tink.subtle.Random
import me.proton.android.calendar.BuildConfig
import me.proton.android.calendar.common.ICalUtils.generateProtonProdId
import me.proton.android.calendar.domain.model.Event
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
     * @return true if Event is valid
     */
    fun VEvent.sanitise(): Boolean {

        if (this.dateStart == null) return false

        // add DTEND
        if (this.dateEnd == null) {
            if (this.dateStart.value.hasTime()) {
                this.setDateEnd(this.dateStart.value)
            } else {
                val endLocalDate = this.getStart()!!.toLocalDate().plusDays(1)
                this.setDateEnd(endLocalDate.toDate(), false)
            }
        }

        return true
    }

    fun VEvent.isDateTimeTheSame(that: VEvent?): Boolean {

        if (that == null) return false

        return (this.dateStart == that.dateStart) && (this.dateEnd == that.dateEnd)
    }

    fun ICalendar.isDateTimeTheSame(that: ICalendar?): Boolean {

        if (that == null) return false

        return (this.timezoneInfo.getTimezone(this.events.first().dateStart)?.timeZone?.id == that.timezoneInfo.getTimezone(that.events.first().dateStart)?.timeZone?.id) &&
                (this.timezoneInfo.getTimezone(this.events.first().dateEnd)?.timeZone?.id == that.timezoneInfo.getTimezone(that.events.first().dateEnd)?.timeZone?.id) &&
                (this.events.first().isDateTimeTheSame(that.events.first()))
    }

    /**
     * Takes one iCalendar object and splits it according to "the matrix".
     */
    fun splitICalendarIntoParts(originalCalendar: ICalendar): CalendarSplit {

        // TODO Attendees Part

        val originalEvent = originalCalendar.events.first()

        return CalendarSplit(
            sharedPart = VEvent().run {

                val newCalendar = wrapInICalendar()

                setUid(originalEvent.uid)
                setCreated(originalEvent.created)
                setLastModified(originalEvent.lastModified)
//                setDateTimeStamp(event.dateTimeStamp)
                setDateStart(originalEvent.dateStart)
                setDateEnd(originalEvent.dateEnd)
                setRecurrenceRule(originalEvent.recurrenceRule)
                setRecurrenceId(originalEvent.recurrenceId)
                setSequence(originalEvent.sequence)
                originalEvent.exceptionDates.forEachIndexed { index, exceptionDate ->
                    addExceptionDates(exceptionDate)

                    // copy timezone assignments for EXDATEs
                    val timezoneAssignment = originalCalendar.timezoneInfo.getTimezone(exceptionDate) ?: originalCalendar.timezoneInfo.defaultTimezone
                    if (timezoneAssignment != null) {
                        newCalendar.timezoneInfo.setTimezone(exceptionDates[index], timezoneAssignment)
                    }
                }

                // copy timezone assignments
                newCalendar.timezoneInfo.setTimezone(this.dateStart, originalCalendar.timezoneInfo.getTimezone(originalEvent.dateStart) ?: originalCalendar.timezoneInfo.defaultTimezone)
                newCalendar.timezoneInfo.setTimezone(this.dateEnd, originalCalendar.timezoneInfo.getTimezone(originalEvent.dateEnd) ?: originalCalendar.timezoneInfo.defaultTimezone)
                newCalendar.timezoneInfo.setTimezone(this.recurrenceId, originalCalendar.timezoneInfo.getTimezone(originalEvent.recurrenceId) ?: originalCalendar.timezoneInfo.defaultTimezone)

                // delete timezone info created automatically when setting timezones
                newCalendar.timezoneInfo.timezones.clear()

                newCalendar
            },
            sharedPartToEncrypt = VEvent().run {
                setUid(originalEvent.uid)
                setCreated(originalEvent.created)
                setLastModified(originalEvent.lastModified)
//                setDateTimeStamp(event.dateTimeStamp)
                setDescription(originalEvent.description) // TODO force substring to be max VALIDATION_EVENT_DESCRIPTION_MAX_LENGTH long?
                setSummary(originalEvent.summary) // TODO force substring to be max VALIDATION_EVENT_SUMMARY_MAX_LENGTH long?
                setLocation(originalEvent.location) // TODO force substring to be max VALIDATION_EVENT_LOCATION_MAX_LENGTH long?
                wrapInICalendar()
            },
            calendarPart = if (originalEvent.status != null || originalEvent.transparency != null) {
                VEvent().run {
                    setUid(originalEvent.uid)
                    setCreated(originalEvent.created)
                    setLastModified(originalEvent.lastModified)
//                    setDateTimeStamp(event.dateTimeStamp)
                    setStatus(originalEvent.status)
                    setTransparency(originalEvent.transparency)
                    wrapInICalendar()
                }
            } else null,
            calendarPartToEncrypt = if (originalEvent.comments.isNotEmpty()) {
                VEvent().run {
                    setUid(originalEvent.uid)
                    setCreated(originalEvent.created)
                    setLastModified(originalEvent.lastModified)
//                    setDateTimeStamp(event.dateTimeStamp)
                    originalEvent.comments.forEach {
                        addComment(it)
                    }
                    // TODO here should be inserted "all the rest" of the properties
                    wrapInICalendar()
                }
            } else null,
            personalPart = if (originalEvent.alarms.isNotEmpty()) {
                VEvent().run {
                    setUid(originalEvent.uid)
                    setCreated(originalEvent.created)
                    setLastModified(originalEvent.lastModified)
//                    setDateTimeStamp(event.dateTimeStamp)
                    originalEvent.alarms.forEach {
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

//        TimberLogger.v("merging left: ${left.printToString()}")
//        TimberLogger.v("merging right: ${right.printToString()}")

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
            left.timezoneInfo.setTimezone(left.events[0].dateStart, right.timezoneInfo.getTimezone(right.events[0].dateStart))
        }
        if (left.timezoneInfo.getTimezone(left.events[0].dateEnd) == null) {
            left.timezoneInfo.setTimezone(left.events[0].dateEnd, right.timezoneInfo.getTimezone(right.events[0].dateEnd))
        }
        if (left.timezoneInfo.getTimezone(left.events[0].recurrenceId) == null) {
            left.timezoneInfo.setTimezone(left.events[0].recurrenceId, right.timezoneInfo.getTimezone(right.events[0].recurrenceId))
        }
        if (left.productId == null) {
            left.productId = right.productId
        }

        left.events[0].exceptionDates.forEachIndexed { index, exceptionDate ->

        }

//        if (left.timezoneInfo.getTimezone(left.events[0].dateStart) == null) {
//            left.timezoneInfo.setTimezone(left.events[0].dateStart, right.timezoneInfo.getTimezone(right.events[0].dateStart))
//        }

        return left
    }

    fun createNewEvent() = VEvent().apply {
        setUid(generateProtonUid())
        setStatus(Status(Status.CONFIRMED)) // TODO set this as default if imported event has this field empty
        setSequence(0)
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

// TODO we strip out "global timezone forward slash" manually, because for some requests server refuses to accept it
fun ICalendar.printToString() : String {
    return Biweekly.write(this).go().replace("TZID=/", "TZID=")
}

    fun LocalDate.toDate(timeZoneId: String? = null): Date = Date.from(this.atStartOfDay(ZoneId.of(timeZoneId ?: ZoneId.systemDefault().id)).toInstant())

    fun DayOfWeek.toBiweeklyDayOfWeek(): biweekly.util.DayOfWeek {
        return biweekly.util.DayOfWeek.values()[(this.ordinal + 1) % 7]
    }

    fun ZonedDateTime.formatDate(timeZoneId: String): String = this.toLocalDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL))

    fun ZonedDateTime.formatTime(timeZoneId: String): String = this.toLocalTime().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

    /**
     * @param excludeTo will exclude exact toDateTime from rightmost range value
     */
    fun ZonedDateTime.isBetween(fromDateTime: ZonedDateTime, toDateTime: ZonedDateTime, excludeFrom: Boolean, excludeTo: Boolean): Boolean {

        val thisInstant = this.toInstant()
        val fromInstant = fromDateTime.toInstant()
        val toInstant = toDateTime.toInstant()

        return (if (excludeFrom) thisInstant > fromInstant else thisInstant >= fromInstant) && (if (excludeTo) thisInstant < toInstant else thisInstant <= toInstant)
    }

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
    fun ICalendar.adjustOutgoingAllDayEvent(timeZoneId: String) {
        this.timezoneInfo.timezones.clear()
        this.events.first().apply {
            setStart(this.getStart(timeZoneId)!!.toLocalDate())
            setEnd(this.getEnd(timeZoneId)!!.toLocalDate().plusDays(1))
        }
    }

    fun VEvent.getStart(timeZoneId: String? = null): ZonedDateTime? {

        if (this.dateStart?.value == null) return null // TODO

        return ZonedDateTime.ofInstant(
            this.dateStart.value.toInstant(),
            if (timeZoneId != null) ZoneId.of(timeZoneId) else ZoneId.systemDefault()
        )
    }

    fun VEvent.getEnd(timeZoneId: String? = null): ZonedDateTime? {
        if (this.dateEnd?.value == null) return null // TODO

        return ZonedDateTime.ofInstant(
            this.dateEnd.value.toInstant(),
            if (timeZoneId != null) ZoneId.of(timeZoneId) else ZoneId.systemDefault()
        )
    }


    /**
     * Filters out original Events that have occurrences with RECURRENCE-ID pointing to
     * that original Event.
     */
    fun List<Event>.filterOccurencesByRecurrenceId(): List<Event> { // TODO take SEQUENCE into account when filtering

        // TODO maybe we should make this use LocalDate so we can use an actual value of the RECURRENCE-ID
        return this.groupBy({it.uid}).mapValues { events ->
            events.value.find { it.iCalEvent.recurrenceId != null } ?: events.value.first()
        }.map { it.value }.toList()
    }
