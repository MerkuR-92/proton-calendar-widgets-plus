package me.proton.android.calendar.domain.model

import biweekly.ICalendar
import biweekly.component.VEvent
import biweekly.component.VTimezone
import biweekly.io.TimezoneAssignment
import biweekly.property.DateOrDateTimeProperty
import biweekly.property.ExceptionDates
import biweekly.property.RecurrenceId
import biweekly.util.ICalDate
import biweekly.util.Recurrence
import me.proton.android.calendar.common.*
import me.proton.android.calendar.common.ICalUtils.iCalTimeZone
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoField
import java.time.temporal.ChronoUnit
import java.util.*


data class Event(
    override val id: String, // ID from API and local database
    val calendar: Calendar,
    val iCalendar: ICalendar,
    val verificationStatus: SignatureVerification? = null
) : BaseModel() {

    var occurrence: Occurrence? = null

    val iCalEvent: VEvent get() = iCalendar.events.first()

    val uid: String get() = iCalEvent.uid.value
    val summary: String? get() = iCalEvent.summary?.value // TODO maybe go back to properties?
    val location: String? get() = iCalEvent.location?.value
    val description: String? get() = iCalEvent.description?.value

    // TODO FIXME if we're not always setting it, it will be null!!!!!!!!!!!!!!!!!!
    val defaultTimeZone: String? get() = iCalendar.timezoneInfo?.defaultTimezone?.timeZone?.id







//    val startTimeZoneId: String? get() = iCalendar.timezoneInfo?.getTimezone(iCalEvent.dateStart)?.timeZone?.id
//    val startTimeZoneId: String? get() = iCalendar.timezoneInfo?.getTimezone(iCalEvent.dateStart)?.timeZone?.id


    val startLocalDate: LocalDate? get() = Instant.ofEpochMilli(iCalEvent.dateStart?.value?.getTime()!!)
        .atZone(ZoneId.systemDefault())
        .toLocalDate() //LocalDate.from(iCalEvent.dateStart?.value?.toInstant())
    // TODO TRY TO REMOVE THIS
    val endLocalDate: LocalDate? get() = Instant.ofEpochMilli(iCalEvent.dateEnd?.value?.getTime()!!)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
//    val endLocalDate: LocalDate? get() = LocalDate.from(iCalEvent.dateEnd?.value?.toInstant())






//    fun getStart(): LocalDateTime


    fun getStart(timeZoneId: String? = null): ZonedDateTime? {
        return iCalEvent.getStart(timeZoneId)
    }

    fun getEnd(timeZoneId: String? = null): ZonedDateTime? {
        return iCalEvent.getEnd(timeZoneId)
    }

    fun formatStart(timeZoneId: String) = formatDateOrDateTimeProperty(iCalEvent.dateStart, timeZoneId)

    fun formatEnd(timeZoneId: String) = formatDateOrDateTimeProperty(iCalEvent.dateEnd, timeZoneId)




    /**
     * @return <formatted date?, formatted time?>
     */
    private fun formatDateOrDateTimeProperty(property: DateOrDateTimeProperty?, timeZoneId: String) : Pair<String?, String?> {
        var formattedDate: String? = null
        var formattedTime: String? = null

        if (property != null) {
            val zonedDateTime = ZonedDateTime.ofInstant(property.value.toInstant(), ZoneId.of(timeZoneId))
            formattedDate = zonedDateTime.toLocalDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL))
            if (property.value.hasTime()) {
                formattedTime = zonedDateTime.toLocalTime().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
            }
        }
        return Pair(formattedDate, formattedTime)
    }

    //val notes: List<String> = iCalEvent.comments.map { it.value }
//        val dateStart: String = iCalEvent.dateStart.value.toString() // TODO probably won't be able to use this in GUI anyway
//        val dateEnd: String = iCalEvent.dateEnd.value.toString() // TODO probably won't be able to use this in GUI anyway
        /**
         * Timestamp when this iCalendar Event was created.
         */
//        val dateTimeStamp: String

    fun isSyncedWithApi(): Boolean = !id.startsWith(OFFLINE_EVENT_ID_PREFIX, ignoreCase = false)

    fun isAllDay(): Boolean = iCalEvent.dateStart?.value?.hasTime() == false && (if (iCalEvent.dateEnd != null) iCalEvent.dateEnd?.value?.hasTime() == false else true)

    fun spansSingleDay(): Boolean {

        val dateStart = this.getStart("UTC")?.toLocalDate()
        val dateEnd = this.getEnd("UTC")?.toLocalDate()

        if (dateStart == null) {
            return false
        }

        return if (isAllDay()) {
            dateEnd == null || dateStart == dateEnd.minusDays(1)
        } else {
            dateStart == dateEnd
        }
    }

    // TODO isEndless?
    data class Occurrence(val startDateTime: ZonedDateTime, val endDateTime: ZonedDateTime, val occurrenceNumber: Int)

    /**
     * Occurrences are generated using DTSTART/DTEND timezone, but formatted with passed timeZoneId param.
     */
    fun generateOccurrencesInFullDayRange(fromDate: LocalDate, toDate: LocalDate, timeZoneId: String): List<Occurrence>? {

        if (!isRecurring()) return null

        val occurences = generateOccurrencesUntil(toDate, timeZoneId) ?: emptyList()

        return occurences.filter {
            startEndOverlapsWithFullDayRange(fromDate, toDate, timeZoneId, it.startDateTime, it.endDateTime)
        }

    }

    fun generateExdateFilteredOccurrencesInFullDayRange(fromDate: LocalDate, toDate: LocalDate, timeZoneId: String): List<Occurrence>? {

        if (!isRecurring()) return null

        val occurences = generateExdateFilteredOccurrencesUntil(toDate, timeZoneId) ?: emptyList()

        return occurences.filter {
            startEndOverlapsWithFullDayRange(fromDate, toDate, timeZoneId, it.startDateTime, it.endDateTime)
        }

    }

    /**
     * Generated occurrences and filters them out by EXDATE.
     *
     * @param timeZoneId timezone of toDate and returned occurrences
     */
    fun generateExdateFilteredOccurrencesUntil(toDate: LocalDate, timeZoneId: String): List<Occurrence>? {

        if (!isRecurring()) return null

        val occurences = generateOccurrencesUntil(toDate, if (this.isAllDay()) ZoneId.systemDefault().id else timeZoneId) ?: emptyList()
        val exceptionDates = this.getExceptionDates() ?: emptyList()

        val result = occurences.filter { occurrence ->
            !exceptionDates.any { it.toInstant() == occurrence.startDateTime.toInstant() }
        }

        return result.map {
            it.copy(
                startDateTime = it.startDateTime.withZoneSameInstant(ZoneId.of(timeZoneId)),
                endDateTime = it.endDateTime.withZoneSameInstant(ZoneId.of(timeZoneId))
            )
        }

    }



    fun filterOutOccurrences(occurences: List<Occurrence>): List<Occurrence> {
        val exceptionDates = this.getExceptionDates() ?: emptyList()
        return occurences.filter { occurrence ->
            !exceptionDates.any { it.toInstant() == occurrence.startDateTime.toInstant() }
        }
    }

    // TODO GENERATE FIRST X OCCURRENCES?

    // TODO merge this method with "generate occurrence x" to have something like "generate occurrences"
    //  until X date or until Y occurrence number
    /**
     * Generates all occurrences of a recurring Event until given LocalDate in TimeZone.
     */
    fun generateOccurrencesUntil(toDate: LocalDate, timeZoneId: String): List<Occurrence>? {

        if (!isRecurring()) return null

        val occurences = mutableListOf<Occurrence>()

        val toDateTime = toDate.plusDays(1).atStartOfDay(ZoneId.of(timeZoneId))

        val iCalTimeZoneStart = iCalendar.iCalTimeZone(iCalEvent.dateStart)

        // this is a workaround for bugs in generating occurrences for RRULE with BYSETPOS
        //  when Event start date contains time different than 00:00, then:
        //  1. first occurrence is skipped
        //  2. all the occurrences are returned as happening at 00:00 anyway, so we lose time of day
        //
        //  we generate occurrences ignoring time of day and set it later manually, this will most likely
        //  only work for FREQUENCY at least DAILY
        val startZonedDateTime = ZonedDateTime.ofInstant(iCalEvent.dateStart.value.toInstant(), ZoneId.of(timeZoneId))
        val startICalDate = ICalDate(iCalEvent.dateStart.value, false)
        val startIterator = iCalEvent.recurrenceRule.getDateIterator(startICalDate, iCalTimeZoneStart)

        val eventDurationInMillis = (iCalEvent.dateEnd.value.time - iCalEvent.dateStart.value.time)

        var occurrenceNumber = 0
        while (startIterator.hasNext()) {

            occurrenceNumber++

            val occurrenceStart = if (this.isAllDay()) { // we force the timezone to be the one we got in param
                // unfortunately parser uses Calendar object with default timezone
                ZonedDateTime.of(ZonedDateTime.ofInstant(startIterator.next().toInstant(), ZoneId.systemDefault()).toLocalDate(), LocalTime.MIDNIGHT, ZoneId.of(timeZoneId))
            } else ZonedDateTime.ofInstant(startIterator.next().toInstant(), ZoneId.of(timeZoneId)).withHour(startZonedDateTime.hour).withMinute(startZonedDateTime.minute)

            val occurrenceEnd = occurrenceStart.plus(eventDurationInMillis, ChronoUnit.MILLIS)

            if (occurrenceStart.isAfter(toDateTime)) break

            occurences.add(Occurrence(occurrenceStart, occurrenceEnd, occurrenceNumber))

        }

        return occurences
    }

    // TODO move all these helper methods to utils



    /**
     * @return Exception Date if it has been set
     */
    fun addExceptionDate(occurrenceNumber: Int): ZonedDateTime? { // TODO decrement COUNT in RRULE?
        if (isRecurring()) {

            val iCalTimeZoneStart = iCalendar.iCalTimeZone(iCalEvent.dateStart)

            val startIterator = iCalEvent.recurrenceRule.getDateIterator(iCalEvent.dateStart.value, iCalTimeZoneStart)

            var counter = 1
            while (startIterator.hasNext() && counter <= occurrenceNumber) {

                val nextValue = startIterator.next()

                if (counter == occurrenceNumber) {
                    val exceptionDates = ExceptionDates()
                    exceptionDates.values.add(ICalDate(nextValue, iCalEvent.dateStart.value.hasTime()))
                    val exceptionDateIndex = iCalEvent.exceptionDates?.size ?: 0
                    iCalEvent.addExceptionDates(exceptionDates)
                    if (iCalEvent.dateStart.value.hasTime()) {
                        iCalendar.timezoneInfo.setTimezone(iCalEvent.exceptionDates[exceptionDateIndex], TimezoneAssignment(iCalTimeZoneStart, VTimezone(iCalTimeZoneStart.id)))
                    }
                    return ZonedDateTime.ofInstant(nextValue.toInstant(), ZoneId.systemDefault())
                } else {
                    counter++
                }
            }

        }

        return null // no exception date has been set
    }

    fun setRecurrenceId(recurrenceId: ZonedDateTime, hasTime: Boolean) {

        iCalEvent.setRecurrenceId(RecurrenceId(Date.from(recurrenceId.toInstant()), hasTime))

        val iCalTimeZoneStart = iCalendar.iCalTimeZone(iCalEvent.dateStart)

        if (iCalEvent.dateStart.value.hasTime()) {
            iCalendar.timezoneInfo.setTimezone(iCalEvent.recurrenceId, TimezoneAssignment(iCalTimeZoneStart, VTimezone(iCalTimeZoneStart.id)))
        }
    }

    /**
     * @param occurrenceNumber has to be 2 or more for this to make sense
     */
    fun handleDeleteThisAndFuture(occurrenceNumber: Int) { // TODO decrement COUNT in RRULE?

        val recurrenceRule = this.iCalEvent.recurrenceRule.value

        if (occurrenceNumber > 1) { // update COUNT
            if (recurrenceRule.count != null && recurrenceRule.count >= occurrenceNumber) {
                this.iCalEvent.setRecurrenceRule(Recurrence.Builder(this.iCalEvent.recurrenceRule.value).count(
                    if (occurrenceNumber == 1) 0 else occurrenceNumber - 1
                ).build())
            } else { // otherwise, set or update UNTIL
                generateOccurrence(occurrenceNumber, iCalendar.iCalTimeZone(this.iCalEvent.dateStart).id)?.let {
                    if (this.isAllDay()) {
                        this.iCalEvent.setRecurrenceRule(Recurrence.Builder(this.iCalEvent.recurrenceRule.value).until(
                            Date.from(it.startDateTime.minusDays(1).toInstant()),
                            false
                        ).build())
                    } else {
                        this.iCalEvent.setRecurrenceRule(Recurrence.Builder(this.iCalEvent.recurrenceRule.value).until(
                            Date.from(it.startDateTime.with(ChronoField.HOUR_OF_DAY, 0).minusSeconds(1).toInstant()),
                            true
                        ).build())
                    }
                }
            }
        }
    }

    /**
     * Calculate all Exception Dates in either the timezone of the EXDATE property, or default system timezone (if event is All-Day).
     */
    fun getExceptionDates(): List<ZonedDateTime>? {
        return if (isRecurring()) {

            val dates = mutableListOf<ZonedDateTime>()

            iCalEvent.exceptionDates?.forEach {

                val exceptionTimezone = iCalendar.iCalTimeZone(it)

                it.values?.forEach {
                    val date = if (it.hasTime()) {
                        ZonedDateTime.ofInstant(it.toInstant(), ZoneId.of(exceptionTimezone.id))
                    } else {
                        // unfortunately parser uses Calendar object with default timezone
                        ZonedDateTime.ofInstant(it.toInstant(), ZoneId.systemDefault())
                    }

                    dates.add(date)
                }

            }

            dates

        } else null
    }

    /**
     * Occurrence is generated using DTSTART/DTEND timezone, but formatted with passed param.
     */
    fun generateOccurrence(occurrenceNumber: Int, timeZoneId: String): Occurrence? {

        if (!isRecurring()) return null

        val iCalTimeZoneStart = iCalendar.iCalTimeZone(iCalEvent.dateStart)

        val startZonedDateTime = ZonedDateTime.ofInstant(iCalEvent.dateStart.value.toInstant(), ZoneId.of(timeZoneId))
        val startICalDate = ICalDate(iCalEvent.dateStart.value, false)
        val startIterator = iCalEvent.recurrenceRule.getDateIterator(startICalDate, iCalTimeZoneStart)

        val eventDurationInMillis = (iCalEvent.dateEnd.value.time - iCalEvent.dateStart.value.time)

        var counter = 1
        while (startIterator.hasNext() && counter <= occurrenceNumber) {

            if (counter == occurrenceNumber) {

                val occurrenceStart = if (this.isAllDay()) { // we force the timezone to be the one we got in param
                    // unfortunately parser uses Calendar object with default timezone
                    ZonedDateTime.of(ZonedDateTime.ofInstant(startIterator.next().toInstant(), ZoneId.systemDefault()).toLocalDate(), LocalTime.MIDNIGHT, ZoneId.of(timeZoneId))
                } else ZonedDateTime.ofInstant(startIterator.next().toInstant(), ZoneId.of(timeZoneId)).withHour(startZonedDateTime.hour).withMinute(startZonedDateTime.minute)

                val occurrenceEnd = occurrenceStart.plus(eventDurationInMillis, ChronoUnit.MILLIS)

                return Occurrence(occurrenceStart, occurrenceEnd, counter)

            } else {
                counter++
                startIterator.next()
            }
        }

        return null
    }

    fun overlapsWithFullDayRange(fromDate: LocalDate, toDate: LocalDate, timeZoneId: String): Boolean {

        val fromDateTime = fromDate.atStartOfDay(ZoneId.of(timeZoneId))
        val toDateTime = toDate.plusDays(1).atStartOfDay(ZoneId.of(timeZoneId))

        return (iCalEvent.getStart(fromDateTime.zone.id)?.isBetween(fromDateTime, toDateTime, excludeFrom = false, excludeTo = true) ?: false) // starts in the range
                || (iCalEvent.getEnd(toDateTime.zone.id)?.isBetween(fromDateTime, toDateTime, excludeFrom = true, excludeTo = false) ?: false) // ends in the range
                || ((iCalEvent.getStart(fromDateTime.zone.id)?.isBefore(fromDateTime) ?: false) && iCalEvent.getEnd(toDateTime.zone.id)?.isAfter(toDateTime) ?: false) // starts before or ends after range, but happens during range
    }

    /**
     * Checks if Event starting at [startDateTime] and ending at [endDateTime] overlaps with
     * range [fromDate]-[toDate].
     */
    fun startEndOverlapsWithFullDayRange(fromDate: LocalDate, toDate: LocalDate, timeZoneId: String, startDateTime: ZonedDateTime, endDateTime: ZonedDateTime): Boolean {

        val fromDateTime = fromDate.atStartOfDay(ZoneId.of(timeZoneId))
        val toDateTime = toDate.plusDays(1).atStartOfDay(ZoneId.of(timeZoneId))

        return (startDateTime.isBetween(fromDateTime, toDateTime, excludeFrom = false, excludeTo = true)) // starts in the range
                || (endDateTime.isBetween(fromDateTime, toDateTime, excludeFrom = true, excludeTo = false)) // ends in the range
                || ((startDateTime.isBefore(fromDateTime) ?: false) && endDateTime.isAfter(toDateTime)) // starts before or ends after range, but happens during range
    }



    fun overlapsWithDateRange(fromDateTime: ZonedDateTime, toDateTime: ZonedDateTime): Boolean {
                return (iCalEvent.getStart(fromDateTime.zone.id)?.isBetween(fromDateTime, toDateTime, excludeFrom = false, excludeTo = false) ?: false) // starts in the range
                || (iCalEvent.getEnd(toDateTime.zone.id)?.isBetween(fromDateTime, toDateTime, excludeFrom = false, excludeTo = false) ?: false) // ends in the range
                || ((iCalEvent.getStart(fromDateTime.zone.id)?.isBefore(fromDateTime) ?: false) && iCalEvent.getEnd(toDateTime.zone.id)?.isAfter(toDateTime) ?: false) // starts before or ends after range, but happens during range
    }

    fun isFirstOccurrence(): Boolean {
        return isRecurring() && this.occurrence?.occurrenceNumber == 1
    }

    fun isRecurring(): Boolean = this.iCalEvent.recurrenceRule != null

    fun isFromRecurring(): Boolean = this.iCalEvent.recurrenceId != null

    fun isCustomRecurring(): Boolean {

        // no Recurrence Rule
        if (!this.isRecurring()) return false

        // look for any of the supported properties
        if (this.iCalEvent.recurrenceRule.value.interval != null ||
            this.iCalEvent.recurrenceRule.value.count != null ||
            this.iCalEvent.recurrenceRule.value.until != null ||
            this.iCalEvent.recurrenceRule.value.byDay?.size ?: 0 > 0
        ) return true

        // by default we return false which means we will ignore non-supported combinations
        return false

    }

    fun withOccurrence(occurrenceNumber: Int, timeZoneId: String): Event? {

        val occurrence = generateOccurrence(occurrenceNumber, timeZoneId)

        return if (occurrence != null) withOccurrence(occurrence) else null
    }

    /**
     * Overwrites start & end datetime with [Occurrence] values.
     */
    fun withOccurrence(occurrence: Occurrence): Event? {
        return this.copy(iCalendar = this.iCalendar.copy() as ICalendar).apply {
            if (this.isAllDay()) {
                this.iCalEvent.setStart(occurrence.startDateTime.toLocalDate())
                this.iCalEvent.setEnd(occurrence.endDateTime.toLocalDate())
            } else {
                this.iCalEvent.setStart(occurrence.startDateTime.toLocalDate(), occurrence.startDateTime.toLocalTime(), occurrence.startDateTime.zone.id)
                this.iCalEvent.setEnd(occurrence.endDateTime.toLocalDate(), occurrence.endDateTime.toLocalTime(), occurrence.endDateTime.zone.id)
                this.iCalendar.setStartTimeZone(occurrence.startDateTime.zone.id)
                this.iCalendar.setEndTimeZone(occurrence.endDateTime.zone.id)
            }
        }
    }


        data class SharedEvent( // TODO maybe this could be named "SharedPart" or "SharedSplit", the same for others
            val type: Int, // 2 for SIGNED 3 for encrypted + signed
            val data: String,
            val signature: String,
            val author: String
        ) {
            val isEncrypted: Boolean get() = type and 1 > 0
//        val isSigned: Boolean get() = type and 2 > 0
        }

        data class CalendarEvent(
            val type: Int, // 2 for SIGNED 3 for encrypted + signed
            val data: String,
            val signature: String,
            val author: String
        ) {
            val isEncrypted: Boolean get() = type and 1 > 0
//        val isSigned: Boolean get() = type and 2 > 0
        }

        data class PersonalEvent(
            val type: Int, // 2 for SIGNED 3 for encrypted + signed
            val data: String,
            val signature: String,
            val author: String,
            val memberId: String
        ) {
//        val isSigned: Boolean get() = type and 2 > 0
        }

        enum class SignatureVerification {
            SUCCESS,
            FAILURE,
            NO_KEYS
        }

    }


