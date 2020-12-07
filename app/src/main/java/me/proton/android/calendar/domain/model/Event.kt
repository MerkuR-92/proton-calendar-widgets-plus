package me.proton.android.calendar.domain.model

import android.content.res.Resources
import biweekly.ICalendar
import biweekly.component.VEvent
import biweekly.component.VTimezone
import biweekly.io.TimezoneAssignment
import biweekly.parameter.ParticipationStatus
import biweekly.property.*
import biweekly.util.ICalDate
import biweekly.util.Recurrence
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.proton.android.calendar.R
import me.proton.android.calendar.common.*
import me.proton.android.calendar.common.ICalUtils.clone
import me.proton.android.calendar.common.ICalUtils.filterOutOccurrencesByExdates
import me.proton.android.calendar.common.ICalUtils.iCalTimeZone
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoField
import java.time.temporal.ChronoUnit
import java.util.*


// TODO remove nullability from signature verification and decryption statuses
data class Event(
    override val id: String, // ID from API and local database
    val calendar: Calendar,
    val iCalendar: ICalendar,
    val verificationStatus: SignatureVerification? = null,
    val decryptionStatus: DecryptionStatus? = null
) : BaseModel() {

    var occurrence: Occurrence? = null

    val iCalEvent: VEvent get() = iCalendar.events.first()

    val uid: String get() = iCalEvent.uid.value
    val summary: String? get() = iCalEvent.summary?.value // TODO maybe go back to properties?
    val location: String? get() = iCalEvent.location?.value
    val description: String? get() = iCalEvent.description?.value

    val status: Status? get() = iCalEvent.status

    // TODO FIXME if we're not always setting it, it will be null!!!!!!!!!!!!!!!!!!
    val defaultTimeZone: String? get() = iCalendar.timezoneInfo?.defaultTimezone?.timeZone?.id

    val startLocalDate: LocalDate? get() = Instant.ofEpochMilli(iCalEvent.dateStart?.value?.getTime()!!)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()

    fun getStart(timeZoneId: String): ZonedDateTime? {
        return iCalEvent.getStart(timeZoneId)
    }

    fun getEnd(timeZoneId: String): ZonedDateTime? {
        return iCalEvent.getEnd(timeZoneId)
    }

    fun getActualStart(timeZoneId: String): ZonedDateTime? {
        return if (this.isSingleEdit()) {
            iCalEvent.getStart(timeZoneId)
        } else if (this.isAllDay()) {
            occurrence?.startDateTime?.withZoneSameLocal(ZoneId.of(timeZoneId)) ?: iCalEvent.getStart(timeZoneId)
        } else {
            occurrence?.startDateTime?.withZoneSameInstant(ZoneId.of(timeZoneId)) ?: iCalEvent.getStart(timeZoneId)
        }
    }

    fun getActualEnd(timeZoneId: String): ZonedDateTime? {
        return if (this.isSingleEdit()) {
            iCalEvent.getEnd(timeZoneId)
        } else if (this.isAllDay()) {
            occurrence?.endDateTime?.withZoneSameLocal(ZoneId.of(timeZoneId)) ?: iCalEvent.getEnd(timeZoneId)
        } else {
            occurrence?.endDateTime?.withZoneSameInstant(ZoneId.of(timeZoneId)) ?: iCalEvent.getEnd(timeZoneId)
        }
    }

    fun isInThePast(timeZoneId: String): Boolean {
//        return false
        return this.getActualEnd(timeZoneId)?.isBefore(ZonedDateTime.now(ZoneId.of(timeZoneId))) == true
    }

    fun isCancelled(): Boolean {
//        return true
        return (this.status != null) && (this.status as Status).isCancelled
    }

    // TODO: Once we have proper user management, check if we could get participation status for current user on event init
    fun getParticipationStatus(userEmail: String): ParticipationStatus? {
        return iCalEvent.attendees.find { it.email.toLowerCase(Locale.getDefault()) == userEmail.toLowerCase(Locale.getDefault()) }?.participationStatus
    }

    /**
     * For given LocalDate and TimeZoneId, returns
     * Pair<1, 3> if on that day, this is first day out of 3 days that the Event spans.
     */
    fun calculateFullDayCounter(date: LocalDate, timeZoneId: String): Pair<Int, Int> {

        return if (spansSingleDay(timeZoneId = timeZoneId)) {
            Pair(1, 1)
        } else {

            val todayOffset = ChronoUnit.DAYS.between(getActualStart(timeZoneId)!!.toLocalDate(), date).toInt() + 1
            val durationInDays = ChronoUnit.DAYS.between(getActualStart(timeZoneId)!!.toLocalDate(), getActualEnd(timeZoneId)!!.toLocalDate()).toInt() + (if (this.isAllDay()) 0 else 1)

            Pair(todayOffset, durationInDays)
        }
    }

    fun formatFullDayCounter(date: LocalDate, timeZoneId: String): String? {

        if (spansSingleDay(timeZoneId = timeZoneId)) return null

        val fullDayCounter = this.calculateFullDayCounter(date, timeZoneId)

        return "(${fullDayCounter.first}/${fullDayCounter.second})"
    }

    fun formatStart(timeZoneId: String, is24Hour: Boolean) = formatDateOrDateTimeProperty(iCalEvent.dateStart, timeZoneId, is24Hour, this.isAllDay())

    fun formatStartForNotification(timeZoneId: String, resources: Resources) : String {

        val startDate = this.getStart(timeZoneId)?.toLocalDate()
        val today = LocalDate.now(ZoneId.of(timeZoneId))

        if (startDate == null) return ""

        val formattedDateTime = formatDateOrDateTimeProperty(iCalEvent.dateStart, timeZoneId, is24Hour = null, this.isAllDay())

        return if (this.isAllDay()) {

            if (startDate == today) {
                resources.getString(R.string.notification_text_today)
            } else if (startDate == today.plusDays(1)) {
                resources.getString(R.string.notification_text_tomorrow)
            } else {
                formattedDateTime.first ?: ""
            }

        } else {

            if (startDate == today) {
                resources.getString(R.string.notification_text_today_part_time, formattedDateTime.second)
            } else if (startDate == today.plusDays(1)) {
                resources.getString(R.string.notification_text_tomorrow_part_time, formattedDateTime.second)
            } else {
                resources.getString(R.string.notification_text_part_time, formattedDateTime.first, formattedDateTime.second)
            }

        }

    }

    fun formatEnd(timeZoneId: String, is24Hour: Boolean) = formatDateOrDateTimeProperty(iCalEvent.dateEnd, timeZoneId, is24Hour, isAllDay())

    fun formatStartEndForActualEndDate(timeZoneId: String, resources: Resources, is24Hour: Boolean): String {
        TimberLogger.d("format startend for timezone=$timeZoneId")
        TimberLogger.d("format startend with occurrence=${this.occurrence}")
        return if (this.spansSingleDay(actualEndDate = true, timeZoneId = timeZoneId)) {

            // TODO cleanup and check against requirements
            val formattedStartDate = this.occurrence?.startDateTime?.formatDate(timeZoneId) ?: this.formatStart(timeZoneId, is24Hour).first

            if (this.isAllDay()) { // ignoring timezones
                formattedStartDate!! //TODO
            } else {

                val startDateTimeInStartTimezone = ZonedDateTime.ofInstant(this.iCalEvent.dateStart.value.toInstant(), ZoneId.of(timeZoneId))
                val endDateTimeInStartTimezone = ZonedDateTime.ofInstant(this.iCalEvent.dateEnd.value.toInstant(), ZoneId.of(timeZoneId))

                val formattedStartTime = this.occurrence?.startDateTime?.formatTime(timeZoneId, is24Hour) ?: startDateTimeInStartTimezone.formatTime(timeZoneId, is24Hour)
                val formattedEndTime = this.occurrence?.endDateTime?.formatTime(timeZoneId, is24Hour) ?: endDateTimeInStartTimezone.formatTime(timeZoneId, is24Hour)

                // TODO R dependency
                "${formattedStartDate}\n${resources.getString(R.string.event_time_period_spanning_single_day, formattedStartTime, formattedEndTime)}"
            }
        } else {

            if (this.isAllDay()) { // ignoring timezones
                val formattedStartDate = this.occurrence?.startDateTime?.formatDate(timeZoneId) ?: this.formatStart(timeZoneId, is24Hour).first
                val formattedEndDate = this.occurrence?.endDateTime?.minusDays(1)?.formatDate(timeZoneId) ?: this.formatEnd(timeZoneId, is24Hour).first

                resources.getString(R.string.event_time_period_spanning_many_days, formattedStartDate, formattedEndDate)
            } else {

                // TODO these two dates were formatted with calendar_timezone, check if this makes sense or not, it's changed to 1 timezone throughout this function
                val startDateTimeInStartTimezone = this.occurrence?.startDateTime?.withZoneSameInstant(ZoneId.of(timeZoneId)) ?: ZonedDateTime.ofInstant(this.iCalEvent.dateStart.value.toInstant(), ZoneId.of(timeZoneId))
                val endDateTimeInStartTimezone = this.occurrence?.endDateTime?.withZoneSameInstant(ZoneId.of(timeZoneId)) ?: ZonedDateTime.ofInstant(this.iCalEvent.dateEnd.value.toInstant(), ZoneId.of(timeZoneId))

                val startDateTime = "${startDateTimeInStartTimezone.formatDate(timeZoneId)} ${startDateTimeInStartTimezone.formatTime(timeZoneId, is24Hour)}"
                val endDateTime = "${endDateTimeInStartTimezone.formatDate(timeZoneId)} ${endDateTimeInStartTimezone.formatTime(timeZoneId, is24Hour)}"

                resources.getString(R.string.event_time_period_spanning_many_days, startDateTime, endDateTime)
            }
        }
    }


    /**
     * @return <formatted date?, formatted time?>
     */
    fun formatDateOrDateTimeProperty(property: DateOrDateTimeProperty?, timeZoneId: String, is24Hour: Boolean?, isAllDay: Boolean) : Pair<String?, String?> {
        var formattedDate: String? = null
        var formattedTime: String? = null

        if (property != null) {
            val zonedDateTime = property.value.toZonedDateTime(timeZoneId)

            formattedDate = zonedDateTime.toLocalDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(getLocaleForFormatting()))
            if (property.value.hasTime()) {
                formattedTime = zonedDateTime.toLocalTime().format(is24Hour)
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

    fun spansSingleDay(actualEndDate: Boolean = false, timeZoneId: String? = null): Boolean {

        val dateStart = this.getStart(timeZoneId ?: ZoneId.systemDefault().id)?.toLocalDate()
        val dateEnd = this.getEnd(timeZoneId ?: ZoneId.systemDefault().id)?.toLocalDate()

        if (dateStart == null) {
            return false
        }

        return if (isAllDay()) {
            dateEnd == null || dateStart == dateEnd.minusDays(if (actualEndDate) 0 else 1)
        } else {
            dateStart == dateEnd
        }
    }

    /**
     * Occurrence should always be expressed in timezone we format or display the calenendar with.
     */
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

    /**
     * Generates all occurrences of a recurring Event until given LocalDate in TimeZone
     * or the first X occurrences, whatever comes first.
     *
     * If only [fromDateTime] is provided, first occurrence will start at that date, or later.
     *
     * Occurrences are in passed timezone, not in original Event's timezone.
     *
     * You need to provide at least [toDate] or [occurrenceCount] or [fromDateTime]
     */
    fun generateOccurrences(timeZoneId: String, toDate: LocalDate?, fromDateTime: ZonedDateTime?, occurrenceCount: Int?): List<Occurrence>? {

        if (!isRecurring()) return null

        if (toDate == null && occurrenceCount == null && fromDateTime == null) return null

        val occurrences = mutableListOf<Occurrence>()

        val toDateTime = toDate?.plusDays(1)?.atStartOfDay(ZoneId.of(timeZoneId))

        val iCalTimeZoneStart = iCalendar.iCalTimeZone(iCalEvent.dateStart)

        // TODO Investigate issue with by set pos
        // this is a workaround for bugs in generating occurrences for RRULE with BYSETPOS
        //  when Event start date contains time different than 00:00, then:
        //  1. first occurrence is skipped
        //  2. all the occurrences are returned as happening at 00:00 anyway, so we lose time of day
        //
        //  we generate occurrences ignoring time of day & timezone and set it later manually,
        //   this will most likely only work for FREQUENCY at least DAILY

        val hasTime = !isAllDay() && iCalEvent.recurrenceRule.value.bySetPos.isNullOrEmpty()

        val startZonedDateTime = iCalEvent.dateStart.value.toZonedDateTime(timeZoneId)
        val startZonedDateTimeAllDayNormalised = ZonedDateTime.of(startZonedDateTime.toLocalDate(), LocalTime.MIDNIGHT, ZoneId.of(timeZoneId))

        val startICalDate = ICalDate(iCalEvent.dateStart.value, hasTime)
        val iteratorTimezone = if (hasTime) iCalendar.iCalTimeZone(iCalEvent.dateStart) else TimeZone.getDefault()

        val recurrenceRule = if (this.isAllDay() && this.iCalEvent.recurrenceRule?.value?.until != null) {
            RecurrenceRule(this.iCalEvent.recurrenceRule.value.clone(until = ICalDate(this.iCalEvent.recurrenceRule.value.until, true)))
        } else {
            this.iCalEvent.recurrenceRule
        }

        val startIterator = recurrenceRule.getDateIterator(startICalDate, iteratorTimezone)

        val untilZonedDateTime = if (iCalEvent.recurrenceRule.value.until != null) {
            iCalEvent.recurrenceRule.value.until.toZonedDateTime(timeZoneId)
        } else null

        val eventDurationInMillis = (iCalEvent.dateEnd.value.time - iCalEvent.dateStart.value.time)

        var occurrenceNumber = 0
        while (startIterator.hasNext()) {

            occurrenceNumber++

            val startIteratorNext = startIterator.next()
            val occurrenceStart =
                if (!hasTime && !isAllDay()) {
                    // Handle BySetPos edge case
                    startIteratorNext.toZonedDateTime(timeZoneId, !isAllDay()).withHour(startZonedDateTime.hour).withMinute(startZonedDateTime.minute)
                } else {
                    startIteratorNext.toZonedDateTime(timeZoneId, this.isAllDay())
                }

            val occurrenceEnd = occurrenceStart.plus(eventDurationInMillis, ChronoUnit.MILLIS)

            // occurrence start date-time is generated by us so we need to check the UNTIL manually
            if (untilZonedDateTime != null && occurrenceStart.isAfter(untilZonedDateTime)) {
                break
            }

            // ignore occurrences happening before they should be actually displayed
            if ((this.isAllDay() && occurrenceStart.isBefore(startZonedDateTimeAllDayNormalised)) || !this.isAllDay() && occurrenceStart.isBefore(startZonedDateTime)) {
                occurrenceNumber--
                continue
            }

            // ignore occurrences before the [fromDate]
            if (fromDateTime != null && fromDateTime.isAfter(occurrenceStart)) {
                continue
            }

            if ((toDateTime != null && occurrenceStart.isAfter(toDateTime)) || (occurrenceCount != null && occurrenceNumber > occurrenceCount)) {
                break
            }

            occurrences.add(Occurrence(occurrenceStart, occurrenceEnd, occurrenceNumber))

        }

        return occurrences

    }

    // TODO merge this method with "generate occurrence x" to have something like "generate occurrences"
    //  until X date or until Y occurrence number
    /**
     * Generates all occurrences of a recurring Event until given LocalDate in TimeZone.
     *
     * Occurrences are in passed timezone, not in original Event's timezone.
     */
    fun generateOccurrencesUntil(toDate: LocalDate, timeZoneId: String): List<Occurrence>? {
        return generateOccurrences(timeZoneId, toDate, null, null)
    }

    /**
     * Generate first occurrence happening at [fromDateTime] or later.
     */
    fun generateFirstOccurrenceSince(fromDateTime: ZonedDateTime): Occurrence? {
        return generateOccurrences(fromDateTime.zone.id, null, fromDateTime, null)?.firstOrNull()
    }

    // TODO unify filtering by exdates and single edits

    /**
     * Filtered by exdates and taking single edits into consideration.
     */
    fun generateFirstRealOccurrenceSince(allEvents: List<Event>, fromDateTime: ZonedDateTime): Occurrence? {

        val originalEvent = this
        val watchdog = ZonedDateTime.now().plusYears(50)
        var from = fromDateTime

        while (from.isBefore(watchdog)) {
            val firstOccurrence = generateOccurrences(fromDateTime.zone.id, null, from, null)?.firstOrNull() ?: return null
            val firstEventWithOccurrence = this.withOccurrence(firstOccurrence)

            val filteredBySingleEdits = listOf(firstEventWithOccurrence).filter { allEvents.find {
                it.iCalEvent.recurrenceId?.value == ICalUtils.eventStartZonedDateTimeToDate(
                    firstOccurrence.startDateTime,
                    originalEvent.isAllDay()
                )
            } == null }

//            TestsLogger.v("firstOccurrence $firstOccurrence")
            val filteredByExdates = filteredBySingleEdits.filterOutOccurrencesByExdates(this, fromDateTime.zone.id)

//            TestsLogger.v("filtered $filtered")
            if (filteredByExdates.isNotEmpty()) {
                return filteredByExdates.first().occurrence
            } else {
                from = firstOccurrence.startDateTime.plusNanos(1)
            }
        }

        return null
    }

    // TODO move all these helper methods to utils



    /**
     * @return Exception Date if it has been set
     */
    fun addExceptionDate(occurrenceNumber: Int, timezone: String? = null) { // TODO decrement COUNT in RRULE?
        if (isRecurring()) {

            var hasTime = !isAllDay() && iCalEvent.recurrenceRule.value.bySetPos.isNullOrEmpty()
            val startICalDate = ICalDate(iCalEvent.dateStart.value, hasTime)
            val iteratorTimezone = if (hasTime) iCalendar.iCalTimeZone(iCalEvent.dateStart) else TimeZone.getDefault()

            val recurrenceRule = if (this.isAllDay() && this.iCalEvent.recurrenceRule?.value?.until != null) {
                RecurrenceRule(this.iCalEvent.recurrenceRule.value.clone(until = ICalDate(this.iCalEvent.recurrenceRule.value.until, true)))
            } else {
                this.iCalEvent.recurrenceRule
            }
            val startIterator = recurrenceRule.getDateIterator(startICalDate, iteratorTimezone)

            val startZonedDateTime = iCalEvent.dateStart.value.toZonedDateTime(timezone ?: iteratorTimezone.id)

            var counter = 1
            while (startIterator.hasNext() && counter <= occurrenceNumber) {

                val startIteratorNext = startIterator.next()

                if (counter == occurrenceNumber) {
                    val exceptionDates = ExceptionDates()

                    if (!hasTime && !isAllDay()) {
                        // Handle BySetPos edge case
                        hasTime = true
                        val occurrenceStart = startIteratorNext.toZonedDateTime(timezone ?: iteratorTimezone.id, !isAllDay()).withHour(startZonedDateTime.hour).withMinute(startZonedDateTime.minute)
                        exceptionDates.values.add(ICalDate(Date.from(occurrenceStart.toInstant()), hasTime))
                    } else {
                        exceptionDates.values.add(ICalDate(startIteratorNext, hasTime))
                    }

                    val exceptionDateIndex = iCalEvent.exceptionDates?.size ?: 0
                    iCalEvent.addExceptionDates(exceptionDates)
                    if (hasTime) {
                        iCalendar.timezoneInfo.setTimezone(
                            iCalEvent.exceptionDates[exceptionDateIndex],
                            TimezoneAssignment(
                                iCalendar.iCalTimeZone(iCalEvent.dateStart),
                                VTimezone(iCalendar.iCalTimeZone(iCalEvent.dateStart).id)
                            )
                        )
                    }
                    return
                } else {
                    counter++
                }
            }

        }
    }

    fun setRecurrenceId(recurrenceId: ZonedDateTime, hasTime: Boolean) {
        iCalEvent.recurrenceId = RecurrenceId(ICalUtils.eventStartZonedDateTimeToDate(recurrenceId, !hasTime), hasTime)
        if (hasTime) iCalendar.timezoneInfo.setTimezone(iCalEvent.recurrenceId, TimezoneAssignment(TimeZone.getTimeZone(recurrenceId.zone.id), VTimezone(recurrenceId.zone.id)))
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
                // Use default timezone for part day only
                generateOccurrence(occurrenceNumber, if (this.isAllDay()) ZoneId.systemDefault().id else iCalendar.iCalTimeZone(this.iCalEvent.dateStart).id)?.let {
                    if (this.isAllDay()) {
                        this.iCalEvent.setRecurrenceRule(Recurrence.Builder(this.iCalEvent.recurrenceRule.value).until(
                            it.startDateTime
                                .minusDays(1)
                                .with(ChronoField.HOUR_OF_DAY, 0)
                                .toLocalDate()
                                .toDate(it.startDateTime.zone.id),
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

                it.values?.forEach { exDate ->
                    dates.add(exDate.toZonedDateTime(exceptionTimezone.id))
                }

            }

            dates

        } else null
    }

    /**
     * Occurrence is generated using DTSTART/DTEND timezone, but formatted with passed param.
     *
     * @param occurrenceNumber has to be a positive number
     */
    fun generateOccurrence(occurrenceNumber: Int, timeZoneId: String): Occurrence? {
        return generateOccurrences(timeZoneId, null, null, occurrenceNumber)?.getOrNull(occurrenceNumber - 1)
    }

    fun overlapsWithFullDayRange(fromDate: LocalDate, toDate: LocalDate, timeZoneId: String): Boolean {

        val fromDateTime = fromDate.atStartOfDay(ZoneId.of(timeZoneId))
        val toDateTime = toDate.plusDays(1).atStartOfDay(ZoneId.of(timeZoneId))

        val dateTimeStart = this.getActualStart(timeZoneId)
        val dateTimeEnd = this.getActualEnd(timeZoneId)

        return (dateTimeStart?.isBetween(fromDateTime, toDateTime, excludeFrom = false, excludeTo = true) ?: false) // starts in the range
                || (dateTimeEnd?.isBetween(fromDateTime, toDateTime, excludeFrom = true, excludeTo = false) ?: false) // ends in the range
                || ((dateTimeStart?.isBefore(fromDateTime) ?: false) && dateTimeEnd?.isAfter(toDateTime) ?: false) // starts before or ends after range, but happens during range
    }

    /**
     * Checks if Event starting at [startDateTime] and ending at [endDateTime] overlaps with
     * range [fromDate]-[toDate].
     */
    fun startEndOverlapsWithFullDayRange(fromDate: LocalDate, toDate: LocalDate, timeZoneId: String, startDateTime: ZonedDateTime, endDateTime: ZonedDateTime): Boolean {

        val fromDateTime = fromDate.atStartOfDay(ZoneId.of(timeZoneId))
        val toDateTime = toDate.plusDays(1).atStartOfDay(ZoneId.of(timeZoneId))

        return (startDateTime.withZoneSameLocal(ZoneId.of(timeZoneId)).isBetween(fromDateTime, toDateTime, excludeFrom = false, excludeTo = true)) // starts in the range
                || (endDateTime.withZoneSameLocal(ZoneId.of(timeZoneId)).isBetween(fromDateTime, toDateTime, excludeFrom = true, excludeTo = false)) // ends in the range
                || ((startDateTime.withZoneSameLocal(ZoneId.of(timeZoneId)).isBefore(fromDateTime) ?: false) && endDateTime.withZoneSameLocal(ZoneId.of(timeZoneId)).isAfter(toDateTime)) // starts before or ends after range, but happens during range
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

    fun isSingleEdit(): Boolean = this.iCalEvent.recurrenceId != null

    fun isSingleOccurrenceRecurring(timeZoneId: String): Boolean =
        isRecurring() && (this.iCalEvent.recurrenceRule.value.count == 1 || isRecurringUntilSameDay(timeZoneId) || isRecurringUntilBeforeNextOccurrence(timeZoneId))

    fun isRecurringUntilSameDay(timeZoneId: String): Boolean {
        if (iCalEvent.recurrenceRule.value.until == null) return false
        val untilZonedDateTime = iCalEvent.recurrenceRule.value.until.toZonedDateTime(timeZoneId)
        val dateStart = iCalEvent.dateStart.value.toZonedDateTime(timeZoneId)
        return ChronoUnit.DAYS.between(dateStart, untilZonedDateTime).toInt() <= 0
    }

    fun isRecurringUntilBeforeNextOccurrence(timeZoneId: String): Boolean {
        if (iCalEvent.recurrenceRule.value.until == null) return false
        val untilZonedDateTime = iCalEvent.recurrenceRule.value.until.toZonedDateTime(timeZoneId)
        val occurrenceCount = generateOccurrencesUntil(untilZonedDateTime.toLocalDate(), timeZoneId)?.size
        return occurrenceCount == null || occurrenceCount <= 1
    }

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

    // this event might be a single edit so it's technically a separate event in the database,
    //  but it's still considered as a part of a chain of events
    fun isPartOfChain(): Boolean = this.isRecurring() || this.isSingleEdit()

    fun withOccurrence(occurrenceNumber: Int, timeZoneId: String): Event? {

        val occurrence = generateOccurrence(occurrenceNumber, timeZoneId)

        return if (occurrence != null) withOccurrence(occurrence) else null
    }

    /**
     * Overwrites start & end datetime with [Occurrence] values.
     */
    fun withOccurrence(occurrence: Occurrence): Event {
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
            this.occurrence = occurrence
        }
    }

    sealed class EventPart {

        abstract val type: Int // 0: cleartext, 1: encrypted, 2: signed, 3: encrypted & signed
        abstract val data: String
        abstract val signature: String?
        abstract val author: String

        val isEncrypted: Boolean get() = type and 1 > 0
        val isSigned: Boolean get() = type and 2 > 0

        @Serializable
        data class Shared(
            @SerialName("Type")
            override val type: Int,
            @SerialName("Data")
            override val data: String,
            @SerialName("Signature")
            override val signature: String?,
            @SerialName("Author")
            override val author: String
        ): EventPart()

        @Serializable
        data class Calendar(
            @SerialName("Type")
            override val type: Int,
            @SerialName("Data")
            override val data: String,
            @SerialName("Signature")
            override val signature: String?,
            @SerialName("Author")
            override val author: String
        ): EventPart()

        @Serializable
        data class Personal(
            @SerialName("Type")
            override val type: Int,
            @SerialName("Data")
            override val data: String,
            @SerialName("Signature")
            override val signature: String?,
            @SerialName("Author")
            override val author: String,
            @SerialName("MemberID")
            val memberId: String
        ): EventPart()

        @Serializable
        data class Attendee(
            @SerialName("Type")
            override val type: Int,
            @SerialName("Data")
            override val data: String,
            @SerialName("Signature")
            override val signature: String?,
            @SerialName("Author")
            override val author: String
        ): EventPart()

    }

    @Serializable
    data class AttendeeStatusEvent(
        @SerialName("ID")
        val id: String,
        @SerialName("Token")
        val token: String,
        @SerialName("Status")
        val status: Int
    ) {
        val participationStatus: ParticipationStatus get() = when (status) {
            1 -> ParticipationStatus.TENTATIVE
            2 -> ParticipationStatus.DECLINED
            3 -> ParticipationStatus.ACCEPTED
            else -> ParticipationStatus.NEEDS_ACTION
        }
    }

    enum class SignatureVerification {
        SUCCESS,
        FAILURE,
        SIGNED_BUT_NO_KEYS,
        NOT_SIGNED
    }

    enum class DecryptionStatus {
        SUCCESS,
        FAILURE
    }
}




