package me.proton.android.calendar.common

import android.content.res.Resources
import biweekly.component.VTimezone
import biweekly.io.TimezoneAssignment
import biweekly.parameter.ParticipationStatus
import biweekly.property.DateOrDateTimeProperty
import biweekly.property.ExceptionDates
import biweekly.property.RecurrenceId
import biweekly.property.RecurrenceRule
import biweekly.util.ICalDate
import biweekly.util.Recurrence
import me.proton.android.calendar.R
import me.proton.android.calendar.common.DateTimeUtilsImpl.formatDate
import me.proton.android.calendar.common.DateTimeUtilsImpl.formatTime
import me.proton.android.calendar.common.DateTimeUtilsImpl.getLocaleForFormatting
import me.proton.android.calendar.common.DateTimeUtilsImpl.isBetween
import me.proton.android.calendar.common.DateTimeUtilsImpl.startEndOverlapsWithFullDayRange
import me.proton.android.calendar.common.DateTimeUtilsImpl.toDate
import me.proton.android.calendar.common.DateTimeUtilsImpl.toZonedDateTime
import me.proton.android.calendar.common.ICalUtilsImpl.clone
import me.proton.android.calendar.common.ICalUtilsImpl.extractEmail
import me.proton.android.calendar.common.ICalUtilsImpl.filterOutOccurrencesByExdates
import me.proton.android.calendar.common.ICalUtilsImpl.iCalTimeZone
import me.proton.android.calendar.common.ProtonUtilsImpl.canonicalizeProtonEmail
import me.proton.android.calendar.common.ProtonUtilsImpl.isShortDomainAddress
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.domain.utils.EventUtils
import me.proton.core.user.domain.entity.UserAddress
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoField
import java.time.temporal.ChronoUnit
import java.util.*
import java.util.concurrent.TimeUnit

object EventUtilsImpl : EventUtils {

    // TODO: Once we have proper user management, check if we could get participation status for current user on event init
    override fun Event.getParticipationStatus(userEmails: List<String>): ParticipationStatus? {
        return iCalEvent.attendees.find { attendee ->
            userEmails.firstOrNull { userEmail ->
                val attendeeEmail = attendee.extractEmail()
                attendeeEmail != null && canonicalizeProtonEmail(attendeeEmail, forceCanonicalization = true).equals(
                    canonicalizeProtonEmail(userEmail, forceCanonicalization = true), ignoreCase = true
                )
            } != null
        }?.participationStatus
    }

    override fun Event.isUserAddressAllowedSend(userAddresses: List<UserAddress>, isFreeUser: Boolean): Boolean {
        iCalEvent.attendees.forEach { attendee ->
            val userAddress = userAddresses.firstOrNull { userAddress ->
                val attendeeEmail = attendee.extractEmail()
                attendeeEmail != null && canonicalizeProtonEmail(attendeeEmail, forceCanonicalization = true).equals(
                    canonicalizeProtonEmail(userAddress.email, forceCanonicalization = true), ignoreCase = true
                )
            }
            userAddress?.let {
                return it.enabled && it.canSend
            }
        }
        return false
    }

    override fun Event.updateParticipationStatus(userEmails: List<String>, status: ParticipationStatus) {
        iCalEvent.attendees.find { attendee ->
            userEmails.firstOrNull { userEmail ->
                val attendeeEmail = attendee.extractEmail()
                attendeeEmail != null && canonicalizeProtonEmail(attendeeEmail, forceCanonicalization = true).equals(
                    canonicalizeProtonEmail(userEmail, forceCanonicalization = true), ignoreCase = true
                )
            } != null
        }?.participationStatus = status
    }

    /**
     * For given LocalDate and TimeZoneId, returns
     * Pair<1, 3> if on that day, this is first day out of 3 days that the Event spans.
     *
     * // TODO it will return nonsensical values if the Event doesn't span the [date]
     */
    override fun Event.calculateFullDayCounter(date: LocalDate, timeZoneId: String): Pair<Int, Int> {

        val occurrenceStart = getOccurrenceStart(timeZoneId).toLocalDate()
        val occurrenceEndAdjustedForMidnight = getOccurrenceEnd(timeZoneId).toLocalDate().minusDays(if (getOccurrenceEnd(timeZoneId).toLocalTime() == LocalTime.MIDNIGHT) 1 else 0)

        val todayOffset = ChronoUnit.DAYS.between(occurrenceStart, date).toInt() + 1
        val durationInDays = ChronoUnit.DAYS.between(occurrenceStart, occurrenceEndAdjustedForMidnight).toInt() + 1

        return Pair(todayOffset, durationInDays)
    }

    override fun Event.formatFullDayCounter(date: LocalDate, timeZoneId: String): String? {

        if (spansSingleDay(timeZoneId = timeZoneId)) return null

        val fullDayCounter = this.calculateFullDayCounter(date, timeZoneId)

        return "(${fullDayCounter.first}/${fullDayCounter.second})"
    }

    override fun Event.formatStart(timeZoneId: String, is24Hour: Boolean) = formatDateOrDateTimeProperty(iCalEvent.dateStart, timeZoneId, is24Hour, this.isAllDay())

    override fun Event.formatStartForNotification(timeZoneId: String, resources: Resources, is24Hour: Boolean?) : String {

        val startDate = this.getStart(timeZoneId).toLocalDate()
        val today = LocalDate.now(ZoneId.of(timeZoneId))

        if (startDate == null) return ""

        val formattedDateTime = formatDateOrDateTimeProperty(iCalEvent.dateStart, timeZoneId, is24Hour, this.isAllDay())

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

    override fun Event.formatEnd(timeZoneId: String, is24Hour: Boolean) = formatDateOrDateTimeProperty(iCalEvent.dateEnd, timeZoneId, is24Hour, isAllDay())

    override fun Event.formatStartEndForActualEndDate(timeZoneId: String, resources: Resources, is24Hour: Boolean): String {
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
    override fun Event.formatDateOrDateTimeProperty(property: DateOrDateTimeProperty?, timeZoneId: String, is24Hour: Boolean?, isAllDay: Boolean) : Pair<String?, String?> {
        var formattedDate: String? = null
        var formattedTime: String? = null

        if (property != null) {
            val zonedDateTime = property.value.toZonedDateTime(timeZoneId)

            formattedDate = zonedDateTime.toLocalDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(getLocaleForFormatting()))
            if (property.value.hasTime()) {
                formattedTime = zonedDateTime.toLocalTime().formatTime(is24Hour)
            }
        }
        return Pair(formattedDate, formattedTime)
    }


    /**
     * Occurrences are generated using DTSTART/DTEND timezone, but formatted with passed timeZoneId param.
     */
    override fun Event.generateOccurrencesInFullDayRange(fromDate: LocalDate, toDate: LocalDate, timeZoneId: String): List<Event.Occurrence>? {

        if (!isRecurring()) return null

        val occurences = generateOccurrencesUntil(toDate, timeZoneId) ?: emptyList()

        return occurences.filter {
            startEndOverlapsWithFullDayRange(it.startDateTime, it.endDateTime, fromDate, toDate, timeZoneId)
        }

    }

    /**
     * Generates all occurrences of a recurring Event until given LocalDate in TimeZone
     * or the first X occurrences, whatever comes first.
     *
     * If only [firstOccurrenceFromDateTime] is provided, first occurrence will start at that date, or later,
     * and we will return only that first one. NOT all occurrences between <firstOccurrenceFromDateTime - toDate>
     *
     * Occurrences are in passed timezone, not in original Event's timezone.
     *
     * You need to provide at least [toDate] or [occurrenceCount] or [firstOccurrenceFromDateTime]
     */
    override fun Event.generateOccurrences(timeZoneId: String, toDate: LocalDate?, firstOccurrenceFromDateTime: ZonedDateTime?, occurrenceCount: Int?): List<Event.Occurrence>? {

        if (!isRecurring()) return null

        if (toDate == null && occurrenceCount == null && firstOccurrenceFromDateTime == null) return null

        val occurrences = mutableListOf<Event.Occurrence>()

        val toDateTime = if (isAllDay()) toDate?.atStartOfDay(ZoneId.of(timeZoneId)) else toDate?.plusDays(1)?.atStartOfDay(ZoneId.of(timeZoneId))

        val iCalTimeZoneStart = iCalendar.iCalTimeZone(iCalEvent.dateStart)

        // TODO Investigate issue with by set pos
        // this is a workaround for bugs in generating occurrences for RRULE with BYSETPOS
        //  when Event start date contains time different than 00:00, then:
        //  1. first occurrence is skipped
        //  2. all the occurrences are returned as happening at 00:00 anyway, so we lose time of day
        //
        //  we generate occurrences ignoring time of day & timezone and set it later manually,
        //   this will most likely only work for FREQUENCY at least DAILY

        val hasTime = !isAllDay() && iCalEvent.recurrenceRule.value.bySetPos.isNullOrEmpty() && iCalEvent.recurrenceRule.value.byDay.isNullOrEmpty()

        val startZonedDateTime = iCalEvent.dateStart.value.toZonedDateTime(timeZoneId)
        val startZonedDateTimeAllDayNormalised = ZonedDateTime.of(startZonedDateTime.toLocalDate(), LocalTime.MIDNIGHT, ZoneId.of(timeZoneId))

        val iteratorTimezone = if (hasTime) iCalendar.iCalTimeZone(iCalEvent.dateStart) else TimeZone.getDefault()

        // Special case is for events that have date iterator skip the first occurrence
        //  (either by having bysetpos, or by having a display tz that makes it jump to the next / previous day)
        var specialCase = false
        val startICalDate = if (!isAllDay() && (!iCalEvent.recurrenceRule.value.bySetPos.isNullOrEmpty() || iCalEvent.dateStart.value.toZonedDateTime(TimeZone.getDefault().id).toLocalDate().dayOfYear > iCalEvent.dateStart.value.toZonedDateTime(iCalendar.iCalTimeZone(iCalEvent.dateStart).id).toLocalDate().dayOfYear)) {
            specialCase = true
            ICalDate(iCalEvent.dateStart.value.toZonedDateTime(iCalendar.iCalTimeZone(iCalEvent.dateStart).id).withZoneSameLocal(ZoneId.of(TimeZone.getDefault().id)).toLocalDate().toDate(TimeZone.getDefault().id), false)
        } else ICalDate(iCalEvent.dateStart.value, hasTime)

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
                if (specialCase) {
                    ZonedDateTime.ofInstant(startIteratorNext.toZonedDateTime(iCalendar.iCalTimeZone(iCalEvent.dateStart).id, true).withHour(iCalEvent.dateStart.value.toZonedDateTime(iCalendar.iCalTimeZone(iCalEvent.dateStart).id).hour).withMinute(iCalEvent.dateStart.value.toZonedDateTime(iCalendar.iCalTimeZone(iCalEvent.dateStart).id).minute).toInstant(), ZoneId.of(timeZoneId))
                } else if (!hasTime && !isAllDay()) {
                    // Handle BySetPos edge case
                    startIteratorNext.toZonedDateTime(timeZoneId, !isAllDay()).withHour(startZonedDateTime.hour).withMinute(startZonedDateTime.minute)
                } else {
                    startIteratorNext.toZonedDateTime(timeZoneId, this.isAllDay())
                }

            val occurrenceEnd =
                if (isAllDay()) occurrenceStart.plusDays(TimeUnit.MILLISECONDS.toDays(eventDurationInMillis))
                else occurrenceStart.plus(eventDurationInMillis, ChronoUnit.MILLIS)

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
            if (firstOccurrenceFromDateTime != null && firstOccurrenceFromDateTime.isAfter(occurrenceStart)) {
                continue
            }

            if ((toDateTime != null && occurrenceStart.isAfter(toDateTime)) || (occurrenceCount != null && occurrenceNumber > occurrenceCount)) {
                break
            }

            occurrences.add(Event.Occurrence(occurrenceStart, occurrenceEnd, occurrenceNumber))

            // ignore occurrences before the [fromDate]
            if (firstOccurrenceFromDateTime != null) {
                if (firstOccurrenceFromDateTime.isAfter(occurrenceStart)) {
                    continue
                } else {
                    break
                }
            }

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
    override fun Event.generateOccurrencesUntil(toDate: LocalDate, timeZoneId: String): List<Event.Occurrence>? {
        return generateOccurrences(timeZoneId, toDate, null, null)
    }

    /**
     * Generate first occurrence happening at [fromDateTime] or later.
     */
    override fun Event.generateFirstOccurrenceSince(fromDateTime: ZonedDateTime): Event.Occurrence? {
        return generateOccurrences(fromDateTime.zone.id, null, fromDateTime, null)?.firstOrNull()
    }

    // TODO unify filtering by exdates and single edits

    /**
     * Filtered by exdates and taking single edits into consideration.
     */
    override fun Event.generateFirstRealOccurrenceSince(allEvents: List<Event>, fromDateTime: ZonedDateTime): Event.Occurrence? {

        val originalEvent = this
        val watchdog = ZonedDateTime.now().plusYears(50)
        var from = fromDateTime

        while (from.isBefore(watchdog)) {
            val firstOccurrence = generateFirstOccurrenceSince(from) ?: return null
            val firstEventWithOccurrence = Event.withOccurrence(this, firstOccurrence)

            val filteredBySingleEdits = listOf(firstEventWithOccurrence).filter { allEvents.find {
                it.iCalEvent.recurrenceId?.value == ICalUtilsImpl.eventStartZonedDateTimeToDate(
                    firstOccurrence.startDateTime,
                    originalEvent.isAllDay()
                )
            } == null }

            val filteredByExdates = filteredBySingleEdits.filterOutOccurrencesByExdates(this, fromDateTime.zone.id)

            if (filteredByExdates.isNotEmpty()) {
                return filteredByExdates.first().occurrence
            } else {
                from = firstOccurrence.startDateTime.plusSeconds(1)
            }
        }

        return null
    }

    /**
     * @return Exception Date if it has been set
     */
    override fun Event.addExceptionDate(occurrenceNumber: Int, timezone: String?) { // TODO decrement COUNT in RRULE?
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

    override fun Event.setRecurrenceId(recurrenceId: ZonedDateTime, hasTime: Boolean) {
        iCalEvent.recurrenceId = RecurrenceId(ICalUtilsImpl.eventStartZonedDateTimeToDate(recurrenceId, !hasTime), hasTime)
        if (hasTime) iCalendar.timezoneInfo.setTimezone(iCalEvent.recurrenceId, TimezoneAssignment(TimeZone.getTimeZone(recurrenceId.zone.id), VTimezone(recurrenceId.zone.id)))
    }

    /**
     * @param occurrenceNumber has to be 2 or more for this to make sense
     */
    override fun Event.handleDeleteThisAndFuture(occurrenceNumber: Int) { // TODO decrement COUNT in RRULE?

        val recurrenceRule = this.iCalEvent.recurrenceRule.value

        if (occurrenceNumber > 1) { // update COUNT
            if (recurrenceRule.count != null && recurrenceRule.count >= occurrenceNumber) {
                this.iCalEvent.setRecurrenceRule(
                    Recurrence.Builder(this.iCalEvent.recurrenceRule.value).count(
                    if (occurrenceNumber == 1) 0 else occurrenceNumber - 1
                ).build())
            } else { // otherwise, set or update UNTIL
                // Use default timezone for part day only
                generateOccurrence(occurrenceNumber, if (this.isAllDay()) ZoneId.systemDefault().id else iCalendar.iCalTimeZone(this.iCalEvent.dateStart).id)?.let {
                    if (this.isAllDay()) {
                        this.iCalEvent.setRecurrenceRule(
                            Recurrence.Builder(this.iCalEvent.recurrenceRule.value).until(
                            it.startDateTime
                                .minusDays(1)
                                .with(ChronoField.HOUR_OF_DAY, 0)
                                .toLocalDate()
                                .toDate(it.startDateTime.zone.id),
                            false
                        ).build())
                    } else {
                        this.iCalEvent.setRecurrenceRule(
                            Recurrence.Builder(this.iCalEvent.recurrenceRule.value).until(
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
    override fun Event.getExceptionDates(): List<ZonedDateTime>? {
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
    override fun Event.generateOccurrence(occurrenceNumber: Int, timeZoneId: String): Event.Occurrence? {
        return generateOccurrences(timeZoneId, null, null, occurrenceNumber)?.getOrNull(occurrenceNumber - 1)
    }

    // TODO remove nullability from dateTimeStart/End and use function from ICalUtils
    override fun Event.overlapsWithFullDayRange(fromDate: LocalDate, toDate: LocalDate, timeZoneId: String): Boolean {

        val fromDateTime = fromDate.atStartOfDay(ZoneId.of(timeZoneId))
        val toDateTime = toDate.plusDays(1).atStartOfDay(ZoneId.of(timeZoneId))

        val dateTimeStart = this.getOccurrenceStart(timeZoneId)
        val dateTimeEnd = this.getOccurrenceEnd(timeZoneId)

        return (dateTimeStart.isBetween(fromDateTime, toDateTime, excludeFrom = false, excludeTo = true)) // starts in the range
                || (dateTimeEnd.isBetween(fromDateTime, toDateTime, excludeFrom = true, excludeTo = false)) // ends in the range
                || ((dateTimeStart.isBefore(fromDateTime)) && dateTimeEnd.isAfter(toDateTime)) // starts before or ends after range, but happens during range
    }

    override fun Event.overlapsWithDateRange(fromDateTime: ZonedDateTime, toDateTime: ZonedDateTime): Boolean {
        return (getStart(fromDateTime.zone.id).isBetween(fromDateTime, toDateTime, excludeFrom = false, excludeTo = false)) // starts in the range
                || (getEnd(toDateTime.zone.id).isBetween(fromDateTime, toDateTime, excludeFrom = false, excludeTo = false)) // ends in the range
                || ((getStart(fromDateTime.zone.id).isBefore(fromDateTime)) && getEnd(toDateTime.zone.id).isAfter(toDateTime)) // starts before or ends after range, but happens during range
    }

}
