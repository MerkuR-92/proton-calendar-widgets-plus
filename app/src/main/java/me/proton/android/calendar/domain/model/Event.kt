package me.proton.android.calendar.domain.model

import biweekly.ICalendar
import biweekly.component.VEvent
import biweekly.component.VTimezone
import biweekly.io.TimezoneAssignment
import biweekly.property.DateOrDateTimeProperty
import biweekly.property.ExceptionDates
import biweekly.property.ICalProperty
import biweekly.util.ICalDate
import biweekly.util.Recurrence
import me.proton.android.calendar.common.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoField
import java.util.*


data class Event(
    override val id: String, // ID from API and local database
    val calendar: Calendar,
    val iCalendar: ICalendar,
    val verificationStatus: SignatureVerification? = null
) : BaseModel() {

    var occurence: Occurrence? = null

    val iCalEvent: VEvent get() = iCalendar.events.first()

    val uid: String get() = iCalEvent.uid.value
    val summary: String? get() = iCalEvent.summary?.value // TODO maybe go back to properties?
    val location: String? get() = iCalEvent.location?.value
    val description: String? get() = iCalEvent.description?.value

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

        val fromDateTime = fromDate.atStartOfDay(ZoneId.of(timeZoneId))
        val toDateTime = toDate.plusDays(1).atStartOfDay(ZoneId.of(timeZoneId))

        val occurences = generateOccurrencesUntil(toDate, timeZoneId) ?: emptyList()

        return occurences.filter {
            it.startDateTime.isBetween(fromDateTime, toDateTime, false, true)
        }

    }

    /**
     * Generated occurrences and filters them out by EXDATE.
     *
     * @param timeZoneId timezone of toDate and returned occurrences
     */
    fun generateFilteredOccurrencesUntil(toDate: LocalDate, timeZoneId: String): List<Occurrence>? {

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

    /**
     * Generates all occurrences of a recurring Event until given LocalDate in TimeZone.
     */
    fun generateOccurrencesUntil(toDate: LocalDate, timeZoneId: String): List<Occurrence>? {

        if (!isRecurring()) return null

        val occurences = mutableListOf<Occurrence>()

        val toDateTime = toDate.plusDays(1).atStartOfDay(ZoneId.of(timeZoneId))

        val iCalTimeZoneStart = iCalTimeZone(iCalEvent.dateStart)

        val iCalTimeZoneEnd = iCalTimeZone(iCalEvent.dateEnd)

        val startIterator = iCalEvent.recurrenceRule.getDateIterator(iCalEvent.dateStart.value, iCalTimeZoneStart)
        val endIterator = iCalEvent.recurrenceRule.getDateIterator(iCalEvent.dateEnd.value, iCalTimeZoneEnd)

        var occurrenceNumber = 0
        while (startIterator.hasNext()) {

            occurrenceNumber++

            val occurrenceStart = if (this.isAllDay()) { // we force the timezone to be the one we got in param
                // unfortunately parser uses Calendar object with default timezone
                ZonedDateTime.of(ZonedDateTime.ofInstant(startIterator.next().toInstant(), ZoneId.systemDefault()).toLocalDate(), LocalTime.MIDNIGHT, ZoneId.of(timeZoneId))
            } else ZonedDateTime.ofInstant(startIterator.next().toInstant(), ZoneId.of(timeZoneId))

            TimberLogger.d("has more? " + endIterator.hasNext())
            TimberLogger.d("summary " + this.iCalEvent.summary)
            TimberLogger.d("occurrence " + occurrenceNumber)

            val occurrenceEnd = if (this.isAllDay()) { // we force the timezone to be the one we got in param
                // unfortunately parser uses Calendar object with default timezone

                // TODO FIXME for last day of recurring, when endIterator finishes early because of UNTIL date

                ZonedDateTime.of(ZonedDateTime.ofInstant(endIterator.next().toInstant(), ZoneId.systemDefault()).toLocalDate(), LocalTime.MIDNIGHT, ZoneId.of(timeZoneId))
            } else ZonedDateTime.ofInstant(endIterator.next().toInstant(), ZoneId.of(timeZoneId))

            if (occurrenceStart.isAfter(toDateTime)) break

            occurences.add(Occurrence(occurrenceStart, occurrenceEnd, occurrenceNumber))

        }

        return occurences
    }

    // TODO move all these helper methods to utils

    private fun iCalTimeZone(property: ICalProperty): TimeZone {
        return if (iCalendar.timezoneInfo.isFloating(property)) {
            TimeZone.getDefault()
        } else {
            val timezone = iCalendar.timezoneInfo.getTimezone(property)
            if (timezone == null) TimeZone.getTimeZone("UTC") else timezone.timeZone
        }
    }

    fun addExceptionDate(occurrenceNumber: Int) { // TODO decrement COUNT in RRULE?
        if (isRecurring()) {

            val iCalTimeZoneStart = iCalTimeZone(iCalEvent.dateStart)

            val startIterator = iCalEvent.recurrenceRule.getDateIterator(iCalEvent.dateStart.value, iCalTimeZoneStart)

            var counter = 1
            while (startIterator.hasNext() && counter <= occurrenceNumber) {

                if (counter == occurrenceNumber) {
                    val exceptionDates = ExceptionDates()
                    exceptionDates.values.add(ICalDate(startIterator.next(), iCalEvent.dateStart.value.hasTime()))
                    iCalendar.events.first().dateStart
                    val exceptionDateIndex = iCalEvent.exceptionDates.size
                    iCalEvent.addExceptionDates(exceptionDates)
                    iCalendar.timezoneInfo.setTimezone(iCalEvent.exceptionDates[exceptionDateIndex], TimezoneAssignment(iCalTimeZoneStart, VTimezone(iCalTimeZoneStart.id)))
                    return
                } else {
                    counter++
                    startIterator.next()
                }
            }


        }
    }

    /**
     * @param occurrenceNumber has to be 2 or more for this to make sense
     */
    fun handleDeleteThisAndFollowing(occurrenceNumber: Int) { // TODO decrement COUNT in RRULE?

        val recurrenceRule = this.iCalEvent.recurrenceRule.value

        if (occurrenceNumber > 1) { // update COUNT
            if (recurrenceRule.count != null && recurrenceRule.count >= occurrenceNumber) {
                this.iCalEvent.setRecurrenceRule(Recurrence.Builder(this.iCalEvent.recurrenceRule.value).count(
                    if (occurrenceNumber == 1) 0 else occurrenceNumber - 1
                ).build())
            } else { // otherwise, set or update UNTIL
                generateOccurrence(occurrenceNumber, iCalTimeZone(this.iCalEvent.dateStart).id)?.let {
                    this.iCalEvent.setRecurrenceRule(Recurrence.Builder(this.iCalEvent.recurrenceRule.value).until(
                        Date.from(it.startDateTime.with(ChronoField.HOUR_OF_DAY, 0).minusSeconds(1).toInstant())
                    ).build())
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

                val exceptionTimezone = iCalTimeZone(it)

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

        val iCalTimeZoneStart = iCalTimeZone(iCalEvent.dateStart)

        val iCalTimeZoneEnd = iCalTimeZone(iCalEvent.dateEnd)

        val startIterator = iCalEvent.recurrenceRule.getDateIterator(iCalEvent.dateStart.value, iCalTimeZoneStart)
        val endIterator = iCalEvent.recurrenceRule.getDateIterator(iCalEvent.dateEnd.value, iCalTimeZoneEnd)

        var counter = 1
        while (startIterator.hasNext() && counter <= occurrenceNumber) {

            if (counter == occurrenceNumber) {

                return if (this.isAllDay()) { // we force the timezone to be the one we got in param
                    Occurrence(
                        // unfortunately parser uses Calendar object with default timezone
                        ZonedDateTime.of(ZonedDateTime.ofInstant(startIterator.next().toInstant(), ZoneId.systemDefault()).toLocalDate(), LocalTime.MIDNIGHT, ZoneId.of(timeZoneId)),
                        ZonedDateTime.of(ZonedDateTime.ofInstant(endIterator.next().toInstant(), ZoneId.systemDefault()).toLocalDate(), LocalTime.MIDNIGHT, ZoneId.of(timeZoneId)),
                        counter)
                } else {
                    Occurrence(
                        ZonedDateTime.ofInstant(startIterator.next().toInstant(), ZoneId.of(timeZoneId)),
                        ZonedDateTime.ofInstant(endIterator.next().toInstant(), ZoneId.of(timeZoneId)),
                        counter)
                }
            } else {
                counter++
                startIterator.next()
                endIterator.next()
            }
        }

        return null
    }

//    fun overlapsRecurringWithFullDayRange(fromDate: LocalDate, toDate: LocalDate, timeZoneId: String): RecurrenceInfo? {
//
//
//
//        val overlaps = overlapsWithFullDayRange(fromDate, toDate, timeZoneId)
//
//        return
//    }

    fun overlapsWithFullDayRange(fromDate: LocalDate, toDate: LocalDate, timeZoneId: String): Boolean {

        val fromDateTime = fromDate.atStartOfDay(ZoneId.of(timeZoneId))
        val toDateTime = toDate.plusDays(1).atStartOfDay(ZoneId.of(timeZoneId))

        return (iCalEvent.getStart(fromDateTime.zone.id)?.isBetween(fromDateTime, toDateTime, excludeFrom = false, excludeTo = true) ?: false) // starts in the range
                || (iCalEvent.getEnd(toDateTime.zone.id)?.isBetween(fromDateTime, toDateTime, excludeFrom = true, excludeTo = false) ?: false) // ends in the range
                || ((iCalEvent.getStart(fromDateTime.zone.id)?.isBefore(fromDateTime) ?: false) && iCalEvent.getEnd(toDateTime.zone.id)?.isAfter(toDateTime) ?: false) // starts before or ends after range, but happens during range
    }

    fun overlapsWithDateRange(fromDateTime: ZonedDateTime, toDateTime: ZonedDateTime): Boolean {
                return (iCalEvent.getStart(fromDateTime.zone.id)?.isBetween(fromDateTime, toDateTime, excludeFrom = false, excludeTo = false) ?: false) // starts in the range
                || (iCalEvent.getEnd(toDateTime.zone.id)?.isBetween(fromDateTime, toDateTime, excludeFrom = false, excludeTo = false) ?: false) // ends in the range
                || ((iCalEvent.getStart(fromDateTime.zone.id)?.isBefore(fromDateTime) ?: false) && iCalEvent.getEnd(toDateTime.zone.id)?.isAfter(toDateTime) ?: false) // starts before or ends after range, but happens during range
    }

    fun isFirstOccurrence(): Boolean {
        return isRecurring() && this.occurence?.occurrenceNumber == 1
    }

    fun isRecurring(): Boolean = this.iCalEvent.recurrenceRule != null

    fun isCustomRecurring(): Boolean {

        // no Recurrence Rule
        if (this.iCalEvent.recurrenceRule == null) return false

        // custom rule exists when there is at least Interval set
        if (this.iCalEvent.recurrenceRule.value.interval != null) return true

        // or if no Interval is set, it's implicitly set to 1 and Count or Until are set
        if (this.iCalEvent.recurrenceRule.value.count != null || this.iCalEvent.recurrenceRule.value.until != null) return true

        // by default we return false which means we will ignore non-supported combinations
        return false

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


