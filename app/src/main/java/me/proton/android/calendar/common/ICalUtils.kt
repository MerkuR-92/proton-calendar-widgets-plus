package me.proton.android.calendar.common

import biweekly.Biweekly
import biweekly.ICalVersion
import biweekly.ICalendar
import biweekly.component.VAlarm
import biweekly.component.VEvent
import biweekly.component.VTimezone
import biweekly.io.TimezoneAssignment
import biweekly.io.TimezoneInfo
import biweekly.parameter.ParticipationStatus
import biweekly.property.*
import biweekly.parameter.Role
import biweekly.util.Frequency
import biweekly.util.ICalDate
import biweekly.util.Recurrence
import com.google.crypto.tink.subtle.Hex
import com.google.crypto.tink.subtle.Random
import me.proton.android.calendar.BuildConfig
import me.proton.android.calendar.common.CustomICalPropertyParameter.X_PM_SESSION_KEY
import me.proton.android.calendar.common.CustomICalPropertyParameter.X_PM_SHARED_EVENT_ID
import me.proton.android.calendar.common.ICalUtils.clone
import me.proton.android.calendar.common.ICalUtils.generateProtonProdId
import me.proton.android.calendar.common.MessageDigestHashType.SHA1
import me.proton.android.calendar.data.entity.EventAlarmEntity
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.presentation.calendar.MiniCalendarItemAdapter
import java.security.MessageDigest
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.IsoFields
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

        // TODO replace this with global validation of all properties from biweekly
        calendar.events?.forEach { vEvent ->
            vEvent?.attendees?.forEach {
                it.commonName = it.commonName?.replace("\"", "")
            }
        }

    }

    fun formatTimeZoneId(timeZoneId: String, forInstant: Instant): String {
        val rawOffset = TimeZone.getTimeZone(timeZoneId).getOffset(Date.from(forInstant).time).toLong()
        val offsetLocalTime = LocalTime.MIDNIGHT.plus(if (rawOffset < 0) -rawOffset else rawOffset, ChronoUnit.MILLIS)

        val offset = "${offsetLocalTime.hour}${if (offsetLocalTime.minute > 0) ":${offsetLocalTime.minute}" else ""}"

        return "${timeZoneId} (GMT${if (rawOffset < 0) "-" else "+"}${offset})"
    }

    fun areTimeZoneOffsetsDifferent(timeZoneIdA: String, timeZoneIdB: String, forInstant: Instant? = null): Boolean? {

        if (!TimeZone.getAvailableIDs().contains(timeZoneIdA) || !TimeZone.getAvailableIDs().contains(timeZoneIdB)) {
            return null
        }

        val offsetA = TimeZone.getTimeZone(timeZoneIdA).getOffset(Date.from(forInstant ?: Instant.now()).time).toLong()
        val offsetB = TimeZone.getTimeZone(timeZoneIdB).getOffset(Date.from(forInstant ?: Instant.now()).time).toLong()

        return offsetA != offsetB
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

                    val setPos = if (iCalEvent.getStart(startTimeZone.id)!!.toLocalDate().isLastDayOfWeekInMonth()) {
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
            val untilDate = it.toZonedDateTime(startTimeZone.id)

            val newUntilDate = if (startDate.isAfter(untilDate)) startDate else untilDate

            val until : ICalDate = if (iCalEvent.dateStart.value.hasTime()) {
                ICalDate(Date.from(ZonedDateTime.of(newUntilDate.toLocalDate(), LocalTime.of(23, 59, 59), ZoneId.of(startTimeZone.id)).withZoneSameInstant(ZoneId.of(startTimeZone.id)).toInstant()), true)
            } else {
                ICalDate(newUntilDate.toLocalDate().toDate(ZoneId.systemDefault().id), false)
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
                setDateTimeStamp(originalEvent.dateTimeStamp)
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
                    originalEvent.alarms.forEach {
                        addAlarm(it)
                    }
                    wrapInICalendar()
                }
            } else null,
            attendeesPart = if (originalEvent.attendees.isNotEmpty()) {
                VEvent().run {
                    setUid(originalEvent.uid)
                    setCreated(originalEvent.created)
                    setLastModified(originalEvent.lastModified)
                    originalEvent.attendees.forEach {
                        addAttendee(it)
                    }
                    wrapInICalendar()
                }
            } else null
        )
    }

    /**
     * The token is calculated by doing SHA1(EventUID + canonicalAttendeeAddress)
     */
    fun generateXPmToken(email: String, uid: String): String {
        val messageDigest = MessageDigest.getInstance(SHA1)
        messageDigest.update((uid + email).toByteArray())
        val token = messageDigest.digest()
        return Hex.encode(token)
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
        // UID has a maximum length allowed so we need to remove existing date from originalUid
        val dateRegex = Regex("_R\\d{8}T\\d{6}")
        val cleanOriginalUid = originalUid.replace(dateRegex, "")
        val provider = cleanOriginalUid.substringAfterLast("@", "")
        return "${cleanOriginalUid.substringBeforeLast("@", cleanOriginalUid)}_R$recurrenceId" + if (provider.isNotEmpty()) "@${provider}" else ""
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
     * Generates offline AlarmID for offline alarms calculated locally.
     */
    fun generateOfflineAlarmId() = "$OFFLINE_ALARM_ID_PREFIX${UUID.randomUUID()}${UUID.randomUUID()}${UUID.randomUUID()}"

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
    fun List<Event>.filterOutOccurrencesByExdates(originalEvent: Event, timeZoneId: String): List<Event> {

        val exZonedDateTimes =
            originalEvent.iCalEvent.exceptionDates.flatMap { exDates ->
                exDates.values.map { exDate ->
                    exDate.toZonedDateTime(timeZoneId)
                }
            }

        return if (exZonedDateTimes.isNullOrEmpty()) {
            this
        } else {
            this.filterNot {
                it.occurrence!!.startDateTime in exZonedDateTimes
            }
        }
    }

    fun List<EventAlarmEntity>.filterOutDuplicates(): List<EventAlarmEntity> {
        return this.distinctBy { "${it.eventId}${it.occurrence}" }
    }

    /**
     * Returns event ZonedDateTime on Date format
     * Converts it to default timezone when event is all day
     */
    fun eventStartZonedDateTimeToDate(startDate: ZonedDateTime, isAllDay: Boolean): Date {
        // TODO Make utils method to get correct Date.from value
        return if (isAllDay) Date.from(startDate.toLocalDate().atStartOfDay(ZoneId.systemDefault()).toInstant())
        else Date.from(startDate.toInstant())
    }

    fun calculateAlarmEntity(event: Event, vAlarm: VAlarm, timeZoneId: String, memberId: String): EventAlarmEntity {

            val occurrence = ZonedDateTime.ofInstant(vAlarm.trigger.duration.add(event.iCalEvent.dateStart.value).toInstant(), ZoneId.systemDefault())

            val occurrenceInTimeZone = if (event.isAllDay()) {
                occurrence.withZoneSameLocal(ZoneId.of(timeZoneId))
            } else occurrence

            return EventAlarmEntity(
                generateOfflineAlarmId(),
                occurrenceInTimeZone.toEpochSecond(),
                vAlarm.trigger.duration.toString(),
                if (vAlarm.action.isDisplay) 2 else 1,
                event.id,
                memberId,
                event.calendar.id
            )
    }

    /**
     * Calculates all Alarm Entities for any given Event occurrence in the format used in API.
     *
     * ID is generated locally.
     */
    fun calculateAlarmEntities(event: Event, timeZoneId: String, memberId: String): List<EventAlarmEntity> {
        return event.iCalEvent.alarms.map {
            calculateAlarmEntity(event, it, timeZoneId, memberId)
        }
    }

    /**
     * Calculates upcoming Alarms (triggering starting from [now]) for the upcoming occurrences of all the events
     * supplied, filtered by exdates and single edits if they are in [events].
     *
     * If you need to refresh all alarms for an Event, it's best to supply here all the events in chain
     * (sharing the same UID).
     */
    fun calculateUpcomingAlarmEntities(events: List<Event>, now: ZonedDateTime, memberId: String
    ): List<EventAlarmEntity> {
        return events.flatMap { event ->
            event.iCalEvent.alarms.mapNotNull { vAlarm ->

                val triggerRelativeSeconds = vAlarm.trigger.duration.toMillis() / 1000
//                TestsLogger.v("trigger $triggerRelativeSeconds (${vAlarm.trigger.duration}) }")

                val generateOccurrenceSince = now.minusSeconds(triggerRelativeSeconds)
//                TestsLogger.v("generating first occurrence since: ${generateOcurrenceSince} ")

                val eventOccurrence = if (event.isRecurring()) {
                    val occurrence = event.generateFirstRealOccurrenceSince(events, generateOccurrenceSince)
                    occurrence?.let { event.withOccurrence(it) }
                } else {
                    event
                }

                eventOccurrence?.let {
                    val alarmEntity = calculateAlarmEntity(it, vAlarm, now.zone.id, memberId)
                    if (alarmEntity.occurrence >= now.toEpochSecond()) {
//                        TestsLogger.v("returning alarmEntity (${Instant.ofEpochSecond(alarmEntity.occurrence).atZone(now.zone)}) for ${it.summary} at ${it.getActualStart(now.zone.id)} -> ${alarmEntity}")
                    }
                    alarmEntity
                }
            }
        }.filter { it.occurrence >= now.toEpochSecond() }
    }

}

data class CalendarSplit(
    val sharedPart: ICalendar,
    val sharedPartToEncrypt: ICalendar,
    val calendarPart: ICalendar?,
    val calendarPartToEncrypt: ICalendar?, // TODO all the other properties not mentioned in matrix should be here
    val personalPart: ICalendar?,
    val attendeesPart: ICalendar?
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

/**
 * Calculate ISO week number for given date, taking custom week start into account.
 */
fun LocalDate.weekNumber(startWeekOn: DayOfWeek): Int {

    val firstDayOfTheWeekNumber = this.dayOfWeek.value - startWeekOn.value
    val firstDayOfTheWeekOffset = if (firstDayOfTheWeekNumber < 0) firstDayOfTheWeekNumber + MiniCalendarItemAdapter.CalendarSettings.DAYS_IN_A_WEEK else firstDayOfTheWeekNumber

    var monday: LocalDate = this.minusDays(firstDayOfTheWeekOffset.toLong())
    while (monday.dayOfWeek != DayOfWeek.MONDAY) {
        monday = monday.plusDays(1)
    }

    return monday.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)

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

fun ZonedDateTime.formatDate(timeZoneId: String): String {
    return this
        .withZoneSameInstant(ZoneId.of(timeZoneId))
        .toLocalDate()
        .format(
            DateTimeFormatter
            .ofLocalizedDate(FormatStyle.FULL)
            .withLocale(getLocaleForFormatting())
        )
}

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

    return this.dateStart.value.toZonedDateTime(timeZoneId)
}

fun VEvent.getEnd(timeZoneId: String): ZonedDateTime? {

    if (this.dateEnd?.value == null) return null // TODO

    return this.dateEnd.value.toZonedDateTime(timeZoneId)
}

fun Attendee.extractEmail(): String? {
    return extractEmail(this.uri, this.email, this.commonName)
}

fun Organizer.extractEmail(): String? {
    return extractEmail(this.uri, this.email, this.commonName)
}

private fun extractEmail(uri: String?, email: String?, commonName: String?): String? {
    return when {
        uri?.contains("@") == true -> uri.substringAfter("mailto:")
        email?.contains("@") == true -> email
        commonName?.contains("@") == true -> commonName
        else -> null
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

fun ICalDate.toZonedDateTime(timezone: String): ZonedDateTime {
    return if (this.hasTime()) {
        ZonedDateTime.ofInstant(this.toInstant(), ZoneId.of(timezone))
    } else {
        this.toInstant().atZone(ZoneId.systemDefault()).withZoneSameLocal(ZoneId.of(timezone))
    }
}

fun Date.toZonedDateTime(timezone: String, isAllDay: Boolean): ZonedDateTime {
    return if (!isAllDay) {
        ZonedDateTime.ofInstant(this.toInstant(), ZoneId.of(timezone))
    } else {
        this.toInstant().atZone(ZoneId.systemDefault()).withZoneSameLocal(ZoneId.of(timezone))
    }
}

fun formatUidForICal(eventUid: String): String {
    // ICal fields maximum length is 75 octets. It separates its values with "\r\n[space]" when needed. In order to fetch
    //  the UID value from SharedEvents in DB, we need to add the ICal separator to our UID if its length is more than 75
    return if ((ICAL_UID_PREFIX + eventUid).length > ICAL_LINE_MAXIMUM_LENGTH) {
        val eventUidValueLines = arrayListOf<String>()
        val maxLengthWithPrefixIndex = ICAL_LINE_MAXIMUM_LENGTH - ICAL_UID_PREFIX.length
        eventUidValueLines.add(eventUid.substring(0, maxLengthWithPrefixIndex))
        var uid = eventUid.substring(maxLengthWithPrefixIndex)
        // From this point onward we need to count the space separator as part of the string when checking max line length
        while (uid.length > ICAL_LINE_MAXIMUM_LENGTH - 1) {
            eventUidValueLines.add(uid.substring(0, ICAL_LINE_MAXIMUM_LENGTH - 1))
            uid = uid.substring(ICAL_LINE_MAXIMUM_LENGTH - 1)
        }
        eventUidValueLines.add(uid)
        return eventUidValueLines.joinToString(ICAL_LINE_SEPARATOR)
    } else eventUid
}

fun getResponseIcs(
    responseICalendar: ICalendar,
    userAttendee: Attendee,
    participationStatus: ParticipationStatus,
    originalTimeZoneInfo: TimezoneInfo?
): String {

    if (responseICalendar.productId == null) responseICalendar.setProductId(generateProtonProdId())
    if (responseICalendar.version == null) responseICalendar.version = ICalVersion.V2_0

    // METHOD:REPLY as we answer the REQUEST of the organizer
    responseICalendar.setMethod(Method.REPLY)

    if (responseICalendar.calendarScale == null) responseICalendar.calendarScale = CalendarScale.gregorian()

    // Update user PARTSTAT and remove useless X_PM_TOKEN property
    userAttendee.participationStatus = participationStatus
    userAttendee.removeParameter(CustomICalPropertyParameter.X_PM_TOKEN)
    userAttendee.participationLevel = null
    userAttendee.rsvp = null

    // The other attendees (not linked with the current users) have to be removed
    responseICalendar.events.first().attendees.clear()
    responseICalendar.events.first().addAttendee(userAttendee)

    // Alarms should be dropped
    responseICalendar.events.first().alarms.clear()

    // The EXDATE must be filtered out
    responseICalendar.events.first().exceptionDates.clear()

    // Last-Modified should be dropped
    responseICalendar.lastModified = null

    // We set default timezone in EventVM, reset timezoneInfo to original values
    originalTimeZoneInfo?.let { responseICalendar.timezoneInfo = it }

    return responseICalendar.printToString()
}

fun getInviteIcs(
    newEvent: Event,
    sharedEventId: String,
    sharedSessionKey: String,
): String {

    val inviteICalendar = newEvent.iCalendar.clone()

    if (inviteICalendar.productId == null) inviteICalendar.setProductId(generateProtonProdId())
    if (inviteICalendar.version == null) inviteICalendar.version = ICalVersion.V2_0

    // METHOD:REPLY as we answer the REQUEST of the organizer
    inviteICalendar.setMethod(Method.REQUEST)

    if (inviteICalendar.calendarScale == null) inviteICalendar.calendarScale = CalendarScale.gregorian()

    // Add base64 encoded session key
    inviteICalendar.setExperimentalProperty(X_PM_SESSION_KEY, sharedSessionKey)
    // Add shared event ID
    inviteICalendar.setExperimentalProperty(X_PM_SHARED_EVENT_ID, sharedEventId)

    // Alarms should be dropped
    inviteICalendar.events.first().alarms.clear()

    // The EXDATE must be filtered out
    inviteICalendar.events.first().exceptionDates.clear()

    return inviteICalendar.printToString()
}
