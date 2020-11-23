package me.proton.android.calendar.common

import biweekly.Biweekly
import biweekly.ICalendar
import biweekly.component.VAlarm
import biweekly.component.VEvent
import biweekly.component.VTimezone
import biweekly.io.TimezoneAssignment
import biweekly.property.Attendee
import biweekly.property.ICalProperty
import biweekly.property.RecurrenceRule
import biweekly.property.Status
import biweekly.util.ByDay
import biweekly.util.Frequency
import biweekly.util.ICalDate
import biweekly.util.Recurrence
import com.google.crypto.tink.subtle.Random
import me.proton.android.calendar.BuildConfig
import me.proton.android.calendar.common.ICalUtils.generateProtonProdId
import me.proton.android.calendar.common.ICalUtils.sanitise
import me.proton.android.calendar.domain.model.Event
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.collections.ArrayList


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

    fun formatTimeZoneId(timeZoneId: String, forInstant: Instant): String {
        val rawOffset = TimeZone.getTimeZone(timeZoneId).getOffset(Date.from(forInstant).time).toLong()
        val offsetLocalTime = LocalTime.MIDNIGHT.plus(if (rawOffset < 0) -rawOffset else rawOffset, ChronoUnit.MILLIS)

        val offset = "${offsetLocalTime.hour}${if (offsetLocalTime.minute > 0) ":${offsetLocalTime.minute}" else ""}"

        return "${timeZoneId} (GMT${if (rawOffset < 0) "-" else "+"}${offset})"
    }

    /**
     * Takes iCalendar parts split according to "the matrix" and returns one iCalendar object.
     */
    fun mergeCalendarPartsIntoICalendar(calendarStrings: List<String>): ICalendar? {
        return calendarStrings.mapNotNull { parseICalString(it) }.reduce { sum, element -> mergeICalendars(sum, element) }
    }

    /**
     * Clones the ICalendar copying timezones.
     */
    fun ICalendar.clone(): ICalendar {
        return parseICalString(this.printToString())!!
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
                val endLocalDate = this.getStart(ZoneId.systemDefault().id)!!.toLocalDate().plusDays(1)
                this.setDateEnd(endLocalDate.toDate(), false)
            }

            // TODO maybe we should force DTEND+1 when dtstart=dtend
        }

        return true
    }

    /**
     * This methods clones Recurrence and overwrites only parameters supplied.
     */
    fun Recurrence.clone(
        byDay: List<biweekly.util.DayOfWeek>? = null,
        bySetPos: List<Int>? = null,
        until: ICalDate? = null,
        workweekStarts: biweekly.util.DayOfWeek? = null
    ): Recurrence {

        val builder = Recurrence.Builder(this.frequency)

        builder.bySecond(this.bySecond)
        builder.byMinute(this.byMinute)
        builder.byHour(this.byHour)
        builder.byDay(byDay ?: this.byDay.map { it.day })
        builder.byMonthDay(this.byMonthDay)
        builder.byYearDay(this.byYearDay)
        builder.byWeekNo(this.byWeekNo)
        builder.byMonth(this.byMonth)
        builder.bySetPos(bySetPos ?: this.bySetPos)

        builder.interval(this.interval)
        builder.count(this.count)
        builder.until(until ?: this.until)
        builder.workweekStarts(workweekStarts ?: this.workweekStarts)

        return builder.build()
    }

    fun ICalendar.adjustRRuleToStartDate(oldDateTime: ZonedDateTime? = null) {

        val iCalEvent = this.events.first()

        if (iCalEvent.recurrenceRule == null) return

        // TODO when editing, we keep the chosen timezone as default timezone, this is an ugly hack
        //  so this.iCalTimeZone doesn't return default timezone, but should be fixed
        val startTimeZone = this.timezoneInfo.defaultTimezone?.timeZone ?: this.iCalTimeZone(iCalEvent.dateStart)
        val startDate = iCalEvent.getStart(startTimeZone.id)!!
        val startWeekday = (iCalEvent.getStart(startTimeZone.id)!!.dayOfWeek.toBiweeklyDayOfWeek())
        val startDayWeekInMonth = iCalEvent.getStart(startTimeZone.id)!!.toLocalDate().weekInMonth()

        when (iCalEvent.recurrenceRule.value.frequency) {
            Frequency.WEEKLY -> {
                // Remove ByDay rule corresponding to old start date day and add new start date day
                val oldByDayOfWeek = ArrayList(iCalEvent.recurrenceRule.value.byDay.map { it.day })
                if (oldDateTime != null) oldByDayOfWeek.remove(oldDateTime.dayOfWeek.toBiweeklyDayOfWeek())
                if (!oldByDayOfWeek.contains(startWeekday)) oldByDayOfWeek.add(startWeekday)
                iCalEvent.recurrenceRule.value = iCalEvent.recurrenceRule.value.clone(byDay = oldByDayOfWeek)
            }
            Frequency.MONTHLY -> {
                val rrule = iCalEvent.recurrenceRule.value

                if (rrule.byDay.isNotEmpty() && rrule.bySetPos.isNotEmpty()) {

                    val setPos = if (rrule.bySetPos[0] < 0 && iCalEvent.getStart(startTimeZone.id)!!.toLocalDate().isLastDayOfWeekInMonth()) {
                        -1
                    } else {
                        startDayWeekInMonth
                    }

                    iCalEvent.recurrenceRule.value = iCalEvent.recurrenceRule.value.clone(
                        byDay = listOf(startWeekday),
                        bySetPos = listOf(setPos)
                    )
                }
            }
        }

        iCalEvent.recurrenceRule.value.until?.let {
            val untilDate = ZonedDateTime.ofInstant(it.toInstant(), ZoneId.of(startTimeZone.id))

            val newUntilDate = if (startDate.isAfter(untilDate)) startDate else untilDate

            val until : ICalDate = if (iCalEvent.dateStart.value.hasTime()) {

                ICalDate(Date.from(ZonedDateTime.of(newUntilDate.toLocalDate(), LocalTime.of(23, 59, 59), ZoneId.of(startTimeZone.id)).withZoneSameInstant(ZoneId.of("UTC")).toInstant()), true)


//                    ICalDate(Date.from(startDate.withHour(23).withMinute(59).withSecond(59).withZoneSameInstant(ZoneId.of("UTC")).toInstant()), true)
            } else {
                ICalDate(Date.from(newUntilDate.toInstant()), false)
            }

            iCalEvent.recurrenceRule.value = iCalEvent.recurrenceRule.value.clone(
                until = until
            )
        }

    }

    fun RecurrenceRule.adjustToWeekStart(settingsWeekStart: DayOfWeek) {

        val addWkst = when (this.value.frequency) {
            Frequency.WEEKLY -> {
                this.value.interval != null && this.value.interval > 1 && (this.value.byDay?.isNotEmpty() == true)
            }
            Frequency.YEARLY -> {
                this.value.byWeekNo?.isNotEmpty() == true
            }
            else -> false
        }

        if (addWkst) {
            this.value = this.value.clone(workweekStarts = settingsWeekStart.toBiweeklyDayOfWeek())
        }

    }

    fun VEvent.isDateTimeTheSame(that: VEvent?): Boolean {

        if (that == null) return false

        return (this.dateStart.value.toInstant() == that.dateStart.value.toInstant()) && (this.dateEnd.value.toInstant() == that.dateEnd.value.toInstant())
    }

    fun ICalendar.isDateTimeTheSame(that: ICalendar?): Boolean {

        if (that == null) return false

        return (this.timezoneInfo.getTimezone(this.events.first().dateStart)?.timeZone?.id == that.timezoneInfo.getTimezone(that.events.first().dateStart)?.timeZone?.id) &&
                (this.timezoneInfo.getTimezone(this.events.first().dateEnd)?.timeZone?.id == that.timezoneInfo.getTimezone(that.events.first().dateEnd)?.timeZone?.id) &&
                (this.events.first().isDateTimeTheSame(that.events.first()))
    }

    fun ICalendar.iCalTimeZone(property: ICalProperty): TimeZone {
        return if (this.timezoneInfo.isFloating(property)) {
            TimeZone.getDefault()
        } else {
            val timezone = this.timezoneInfo.getTimezone(property)
            if (timezone == null) TimeZone.getTimeZone("UTC") else timezone.timeZone
        }
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
                setOrganizer(originalEvent.organizer)

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
        right.events.first().components.forEach { components ->
            components.value.forEach { iCalComponent ->
                if (iCalComponent is VAlarm) {
                    left.events.first().addComponent(iCalComponent)
                } else {
                    if (iCalComponent !in left.events.first().components.values()) {
                        left.events.first().addComponent(iCalComponent)
                    }
                }
            }
        }
        right.events.first().properties.forEach { properties ->
            properties.value.forEach { iCalProperty ->
                // We use setProperty to avoid having duplicates, but we need to use addProperty for Attendees
                // to properly add multiple ones
                if (iCalProperty::class == Attendee::class) left.events.first().addProperty(iCalProperty)
                else left.events.first().setProperty(iCalProperty)
                left.timezoneInfo.setTimezone(iCalProperty, right.timezoneInfo.getTimezone(iCalProperty))
            }
        }

        // copy additional metadata that is not yet set
        if (left.productId == null) {
            left.productId = right.productId
        }

        return left
    }

    fun createNewEvent() = VEvent().apply {
        setUid(generateProtonUid())
        setStatus(Status(Status.CONFIRMED)) // TODO set this as default if imported event has this field empty
        setSequence(0)
    }

    fun generateEventStartTime(timeZoneId: ZoneId): LocalTime {
        val time = ZonedDateTime.now(timeZoneId)
        return time.plusMinutes(30L - (time.minute % 30)).toLocalTime()
    }

    /**
     * Generates Proton UID for new ICalendar components.
     */
    fun generateProtonUid() = "${com.google.crypto.tink.subtle.Base64.urlSafeEncode(Random.randBytes(21))}@proton.me"

    /**
     * Generates UID in the form of "original UID prefix + recurrenceId + original UID postfix (after @ symbol)".
     */
    fun generateProtonUid(originalUid: String, recurrenceId: String): String {
        return "${originalUid.substringBefore("@")}_R$recurrenceId@${originalUid.substringAfter("@")}"
    }

    /**
     * Generates Proton Product Identifier.
     */
    fun generateProtonProdId() = "-//Proton Technologies//${API_APPLICATION_NAME} ${BuildConfig.VERSION_NAME}//EN"

    /**
     * Generates offline CalendarID to use before it's successfully sent to server.
     */
    fun generateOfflineEventId() = "$OFFLINE_EVENT_ID_PREFIX${UUID.randomUUID()}${UUID.randomUUID()}${UUID.randomUUID()}"

    /**
     * Returns iCal Events with already applied DTSTART/DTEND according to Occurrence.
     * Takes single edits into account.
     *
     * @param events all single edits selected by UID
     */
    fun expandOccurrencesWithSingleEdits(originalEvent: Event, events: List<Event>, toDate: LocalDate, timeZoneId: String): List<Event>? {

        val maxRecurrenceIdEvent = events.maxByOrNull { it.iCalEvent.recurrenceId?.value?.time ?: Long.MIN_VALUE }

        // take either maximum RecurrenceId from single edits or the requested "toDate"
        val maxToDate = if (maxRecurrenceIdEvent?.iCalEvent?.recurrenceId?.value?.toInstant()?.isAfter(toDate.plusDays(1).atStartOfDay(ZoneId.of(timeZoneId)).toInstant()) == true) {
            ZonedDateTime.ofInstant(maxRecurrenceIdEvent.iCalEvent.recurrenceId?.value?.toInstant(), ZoneId.of(timeZoneId)).toLocalDate()
        } else {
            toDate
        }
        val occurrences = originalEvent.generateOccurrencesUntil(maxToDate, timeZoneId) ?: return null

        return occurrences.map { occurrence ->
            val event = events.find {
                it.iCalEvent.recurrenceId?.value == eventStartZonedDateTimeToDate(occurrence.startDateTime, originalEvent.isAllDay())
            }?.copy() ?: originalEvent.copy()//.withOccurrence(occurrence)!!
            event.occurrence = occurrence
            event
        }

    }

    /**
     * Given original Event, filter out all occurrences that are excluded by EXDATE
     */
    fun List<Event>.filterOutOccurrencesByExdates(originalEvent: Event): List<Event> {

        val exZonedDateTimes =
            originalEvent.iCalEvent.exceptionDates.flatMap { exDates ->
                exDates.values.map { exDate ->
                    if (originalEvent.isAllDay()) LocalDate.from(ZonedDateTime.ofInstant(exDate.toInstant(), ZoneId.systemDefault()))
                    else exDate.toInstant()
                }
            }

        return this.filterNot {
            if (originalEvent.isAllDay()) LocalDate.from(it.occurrence!!.startDateTime) in exZonedDateTimes
            else it.occurrence!!.startDateTime.toInstant() in exZonedDateTimes
        }
    }

    /**
     * Returns event ZonedDateTime on Date format
     * Converts it to default timezone when event is all day
     */
    fun eventStartZonedDateTimeToDate(startDate: ZonedDateTime, isAllDay: Boolean): Date {
        return if (isAllDay) Date.from(startDate.toLocalDate().atStartOfDay(ZoneId.systemDefault()).toInstant())
        else Date.from(startDate.toInstant())
    }
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

fun DayOfWeek.format(firstLetter: Boolean = false): String {
    val formatted = this.getDisplayName(
        TextStyle.FULL,
        getLocaleForFormatting()
    )
    return if (firstLetter) formatted.firstOrNull()?.toString() ?: "" else formatted
}

fun DayOfWeek.toBiweeklyDayOfWeek(): biweekly.util.DayOfWeek {
    return biweekly.util.DayOfWeek.values()[(this.ordinal + 1) % 7]
}

fun biweekly.util.DayOfWeek.toDayOfWeek(): DayOfWeek {
    return DayOfWeek.of((this.calendarConstant)).minus(1)
}

fun ZonedDateTime.formatDate(timeZoneId: String): String = this.withZoneSameInstant(ZoneId.of(timeZoneId)).toLocalDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(getLocaleForFormatting()))

fun ZonedDateTime.formatTime(timeZoneId: String, is24Hour: Boolean): String = this.withZoneSameInstant(ZoneId.of(timeZoneId)).toLocalTime().format(is24Hour)

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

fun VEvent.setStart(time: LocalTime, timeZoneId: String? = "UTC") {
    this.setDateStart(Date.from(ZonedDateTime.ofInstant(this.dateStart.value.toInstant(), ZoneId.of(timeZoneId)).with(time).toInstant()), true)
}

fun VEvent.setEnd(time: LocalTime, timeZoneId: String? = "UTC") {
    this.setDateEnd(Date.from(ZonedDateTime.ofInstant(this.dateEnd.value.toInstant(), ZoneId.of(timeZoneId)).with(time).toInstant()), true)
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

fun ICalendar.adjustIncomingAllDayEvent() {
    this.events.first().apply {

        if (this.dateStart.value != null && !this.dateStart.value.hasTime()) {
            if (this.dateStart.value == this.dateEnd.value) {
                val endLocalDate = this.getStart(ZoneId.systemDefault().id)!!.toLocalDate().plusDays(1)
                this.setDateEnd(endLocalDate.toDate(), false)
            }
        }
    }
}


fun VEvent.getStart(timeZoneId: String): ZonedDateTime? {

    if (this.dateStart?.value == null) return null // TODO

    return if (this.dateStart.value.hasTime()) {
        ZonedDateTime.ofInstant(
            this.dateStart.value.toInstant(),
            ZoneId.of(timeZoneId)
        )
    } else {
        ZonedDateTime.ofInstant(
            this.dateStart.value.toInstant(),
            ZoneId.systemDefault()
        ).withZoneSameLocal(ZoneId.of(timeZoneId))
    }

}

fun VEvent.getEnd(timeZoneId: String): ZonedDateTime? {

    if (this.dateEnd?.value == null) return null // TODO

    return if (this.dateEnd.value.hasTime()) {
        ZonedDateTime.ofInstant(
            this.dateEnd.value.toInstant(),
            ZoneId.of(timeZoneId)
        )
    } else {
        ZonedDateTime.ofInstant(
            this.dateEnd.value.toInstant(),
            ZoneId.systemDefault()
        ).withZoneSameLocal(ZoneId.of(timeZoneId))
    }
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


