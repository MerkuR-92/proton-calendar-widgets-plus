package me.proton.android.calendar.common

import biweekly.Biweekly
import biweekly.ICalendar
import biweekly.component.VEvent
import biweekly.io.TimezoneAssignment
import biweekly.parameter.ParticipationStatus
import biweekly.property.DateOrDateTimeProperty
import biweekly.property.ExceptionDates
import biweekly.util.Frequency
import biweekly.util.ICalDate
import me.proton.android.calendar.common.ICalUtils.clone
import me.proton.android.calendar.common.ICalUtils.iCalTimeZone
import me.proton.android.calendar.common.IcsParsingValidation.DESCRIPTION_MAX_LENGTH
import me.proton.android.calendar.common.IcsParsingValidation.LOCATION_MAX_LENGTH
import me.proton.android.calendar.common.IcsParsingValidation.MAX_ATTENDEES
import me.proton.android.calendar.common.IcsParsingValidation.MAX_COUNT
import me.proton.android.calendar.common.IcsParsingValidation.MAX_COUNT_INVITATION
import me.proton.android.calendar.common.IcsParsingValidation.MAX_DAILY_INTERVAL
import me.proton.android.calendar.common.IcsParsingValidation.MAX_DATE
import me.proton.android.calendar.common.IcsParsingValidation.MAX_MONTHLY_INTERVAL
import me.proton.android.calendar.common.IcsParsingValidation.MAX_WEEKLY_INTERVAL
import me.proton.android.calendar.common.IcsParsingValidation.MAX_YEARLY_INTERVAL
import me.proton.android.calendar.common.IcsParsingValidation.MIN_DATE
import me.proton.android.calendar.common.IcsParsingValidation.SUMMARY_MAX_LENGTH
import me.proton.android.calendar.common.IcsParsingValidation.TZID
import me.proton.android.calendar.common.IcsParsingValidation.UID_MAX_LENGTH
import me.proton.android.calendar.common.IcsParsingValidation.X_WR_TIMEZONE
import me.proton.android.calendar.common.IcsSurgeryUtils.localizeDateToTimezone
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*
import java.util.concurrent.TimeUnit

object IcsSurgeryUtils {

    enum class HandleIcsAction {
        OPEN_EVENT,
        UPDATE_EVENT,
        CREATE_EVENT
    }

    sealed class HandleIcsResult {
        data class Success(
            val eventId: String,
            val action: HandleIcsAction,
            val newAttendeeStatus: Pair<String, ParticipationStatus>? = null
        ): HandleIcsResult()

        data class ParsingSuccessful(
            val iCalendar: ICalendar? = null
        ): HandleIcsResult()

        data class RawParsingSuccessful(
            val cleanICalString: String
        ): HandleIcsResult()

        sealed class Error: HandleIcsResult() {
            object DefaultError: Error()
            object EditCreateEventError: Error()
            object EventDeleted: Error()
            object ParsingFailed: Error()
            object PartyCrasher: Error()
            object MissingUid: Error()
            object NoDefaultCalendarFound: Error()
            object DurationNotSupported: Error()
            object TooManyEvents: Error()
            object NoEvents: Error()

            data class ReplyPartyCrasher(val eventId: String? = null): Error()
            data class DecryptionFailed(val eventId: String? = null, val isRecurring: Boolean? = null): Error()
            data class DisabledCalendar(val eventId: String? = null): Error()

            sealed class Unsupported: Error() {
                object Method: Error()
                object Add: Error()
                object Refresh: Error()
                object Counter: Error()
                object Publish: Error()
            }

            sealed class Invalid: Error() {
                object Version: Error()
                object CalScale: Error()
                object DateOrDateTimeProperty: Error()
                object DateStart: Error()
                object DateEnd: Error()
                object Description: Error()
                object Location: Error()
                object Summary: Error()
                object RRule: Error()
                object RecurrenceId: Error()
                object ExDate: Error()
                object Sequence: Error()
                object Attendees: Error()
            }
        }
    }

    fun cleanIcs(iCalString: String, allowMultipleEvents: Boolean = false): HandleIcsResult {
        // Clean the iCal String first to check for invalid Date or DateTime Properties
        val cleanRawIcsResult = iCalString.cleanRawIcs()

        if (cleanRawIcsResult !is HandleIcsResult.RawParsingSuccessful) return cleanRawIcsResult

        val iCalendar = try {
            val importedICalendars = Biweekly.parse(cleanRawIcsResult.cleanICalString).all() ?: return HandleIcsResult.Error.ParsingFailed

            // We only allow importing one calendar at a time for now
            if (importedICalendars.size > 1) return HandleIcsResult.Error.TooManyEvents

            if (importedICalendars.isEmpty()) return HandleIcsResult.Error.NoEvents

            (importedICalendars.firstOrNull() ?: return HandleIcsResult.Error.ParsingFailed).also { ICalUtils.normaliseICalendar(it) }
        } catch (e: Exception) {
            TimberLogger.e("IcsSurgeryUtils: error parsing iCalendar", e)
            return HandleIcsResult.Error.ParsingFailed
        }

        if (iCalendar.events.isEmpty()) return HandleIcsResult.Error.NoEvents

        // We only allow importing one event at a time for now
        if (iCalendar.events.size > 1 && !allowMultipleEvents) {
            // TODO If is an invitation we take first event only
            return HandleIcsResult.Error.TooManyEvents
        }

        /* Calendar properties */

        if (!iCalendar.cleanCalscale()) return HandleIcsResult.Error.Invalid.CalScale

        iCalendar.cleanXWrTimezone()

        if (!iCalendar.cleanTimezones()) return HandleIcsResult.Error.Invalid.DateOrDateTimeProperty

        /* Event properties */

        // TODO Once we handle multiple events, allow them to fail separately
        iCalendar.events.forEach { event ->
            if (!event.cleanUid()) return HandleIcsResult.Error.MissingUid

            if (!event.cleanDtStart()) return HandleIcsResult.Error.Invalid.DateStart

            if (!event.cleanDuration()) return HandleIcsResult.Error.DurationNotSupported

            if (!event.cleanDtEnd()) return HandleIcsResult.Error.Invalid.DateEnd

            if (!event.cleanDescription()) return HandleIcsResult.Error.Invalid.Description

            if (!event.cleanLocation()) return HandleIcsResult.Error.Invalid.Location

            if (!event.cleanSummary()) return HandleIcsResult.Error.Invalid.Summary

            if (!event.cleanRRule(iCalendar)) return HandleIcsResult.Error.Invalid.RRule

            if (!event.cleanExDate()) return HandleIcsResult.Error.Invalid.ExDate

            if (!event.cleanSequence()) return HandleIcsResult.Error.Invalid.Sequence

            if (!event.cleanAttendees()) return HandleIcsResult.Error.Invalid.Attendees

            if ((iCalendar.method?.isReply == true || iCalendar.method?.isRequest == true || iCalendar.method?.isCancel == true) && !event.alarms.isNullOrEmpty()) {
                // We drop alarms for invites as those would be the personal alarms of the organizer
                event.alarms.clear()
            }
        }

        return HandleIcsResult.ParsingSuccessful(iCalendar)
    }

    fun String.cleanRawIcs(): HandleIcsResult {
        var cleanICalString = this

        // VERSION: We don't support iCal versions other than 2.0.
        if (!cleanICalString.contains(Regex("VERSION:2\\.0\\r?\\n"))) return HandleIcsResult.Error.Invalid.Version

        // DATETIME or DATE properties

        // 1) For all day events
        // We need to check in iCal string for all day dates with bad format because Biweekly doesn't properly save VALUE parameter

        // If the type DATE is specified, drop time information in case it could be there.
        cleanICalString = cleanICalString.replace(Regex("(?<=;VALUE=DATE:\\d{8})T\\d{6}[Z]?"), "")

        // If the type DATE is not specified for an all-day event, we currently reject (as invalid) the event.
        if (cleanICalString.contains(Regex("(DTSTART|DTEND|RECURRENCE-ID):\\d{8}\\n"))) return HandleIcsResult.Error.Invalid.DateOrDateTimeProperty

        // 2) For part day events

        // If it's a floating date (i.e. no TZID present, e.g. DTSTART:20200101T120000), reject (as unsupported) the event if there is no X-WR-TIMEZONE.
        if (cleanICalString.contains(Regex("(DTSTART|DTEND|RECURRENCE-ID):\\d{8}T\\d{6}\\n"))
            && !cleanICalString.contains(Regex("X-WR-TIMEZONE:.+\\n"))) return HandleIcsResult.Error.Invalid.DateOrDateTimeProperty

        return HandleIcsResult.RawParsingSuccessful(cleanICalString)
    }

    fun ICalendar.cleanCalscale(): Boolean {
        // CALSCALE: The calendar scale must be either 'Gregorian' or empty.
        return this.calendarScale?.value.isNullOrEmpty() || this.calendarScale.isGregorian
    }

    fun ICalendar.cleanXWrTimezone(): Boolean {
        // X-WR-TIMEZONE: This one is not an official iCal property, but if present, we should try to convert it into a supported timezone, and use it to localize some UTC dates in the ICS.
        val xWrTimezone = this.getExperimentalProperty(X_WR_TIMEZONE)
        if (xWrTimezone != null) {
            val timezoneId = AndroidUtils.fallbackTimeZone(xWrTimezone.value, fallbackToDefault = false)
            if (timezoneId != null) {
                this.setExperimentalProperty(X_WR_TIMEZONE, timezoneId)
            } else {
                this.removeExperimentalProperties(X_WR_TIMEZONE)
            }
        }
        return true
    }

    fun ICalendar.getXWrTimezone(): String? {
        return this.getExperimentalProperty(X_WR_TIMEZONE)?.value
    }

    fun VEvent.cleanUid(): Boolean {
        // UID: As per RFC, we require it to be present. Also, there's a BE limit of 191 characters. If we need to crop, we keep the last 191 characters of the uid.
        if (this.uid?.value == null || this.uid.value.isEmpty()) return false
        else if (this.uid.value.length > UID_MAX_LENGTH) {
            this.setUid(this.uid.value.takeLast(UID_MAX_LENGTH))
        }
        return true
    }

    fun VEvent.cleanDtStart(): Boolean {
        // DTSTART: As per RFC, we require that it be present and in bounds.
        if (this.dateStart?.value == null || this.dateStart.value.toInstant().isBefore(MIN_DATE.toInstant()) ||
            this.dateStart.value.toInstant().isAfter(MAX_DATE.toInstant())) return false

        // DTSTART & DTEND: They should both use the same value type: either: date-time (default) or date. If one is a date and the other a date-time, the API will reject the request.
        if (this.dateEnd?.value != null && this.dateStart.value.hasTime() != this.dateEnd.value.hasTime()) return false

        return true
    }

    fun VEvent.cleanDuration(): Boolean {
        // DURATION: This property (to specify duration of events instead of a DTEND) is not supported.
        return !(this.duration?.value != null && this.dateEnd?.value == null)
    }

    fun VEvent.cleanDtEnd(): Boolean {
        // DTEND: If not present, we don't add it either. If present, the standard DATETIME/DATE sanitization operations must be performed.
        if (this.dateEnd?.value != null && this.dateEnd.value.before(this.dateStart.value)) return false
        if (this.dateEnd?.value == null) {
            // DTEND can be omitted
            if (this.dateStart.value.hasTime()) {
                // For partial day, the DTEND is by default set to the DTSTART value
                this.setDateEnd(this.dateStart.value)
            } else {
                // For full day, the DTEND is by default set to the day after DTSTART such that the event is one day long
                val dateEnd = this.dateStart.value.clone() as ICalDate
                dateEnd.time += TimeUnit.DAYS.toMillis(1)
                this.setDateEnd(dateEnd)
            }
        } else {
            // DTEND: If present check if in bounds
            if (this.dateEnd.value.toInstant().isBefore(MIN_DATE.toInstant()) ||
                this.dateEnd.value.toInstant().isAfter(MAX_DATE.toInstant())) return false
        }

        return true
    }

    fun VEvent.cleanDescription(): Boolean {
        // DESCRIPTION: This field is limited to 3k characters.
        return this.description?.value == null || this.description.value.length <= DESCRIPTION_MAX_LENGTH
    }

    fun VEvent.cleanLocation(): Boolean {
        // LOCATION: This field is limited to 255 characters.
        return this.location?.value == null || this.location.value.length <= LOCATION_MAX_LENGTH
    }

    fun VEvent.cleanSummary(): Boolean {
        // SUMMARY: This field is limited to 255 characters.
        return this.summary?.value == null || this.summary.value.length <= SUMMARY_MAX_LENGTH
    }

    fun VEvent.cleanRRule(iCalendar: ICalendar): Boolean {

        // If the event contains both a RECURRENCE-ID and an RRULE and the method is REPLY, simply ignore the RRULE (the external provider forgot to remove it when adding the RECURRENCE-ID).
        if (iCalendar.method?.isReply == true && this.recurrenceRule?.value != null && this.recurrenceId?.value != null) this.recurrenceRule = null

        if (recurrenceRule?.value == null) return true

        // We only support certain types of RRULEs, basically the ones that can be created from ProtonCalendar

        // We do not accept events with a rrule below daily
        if (recurrenceRule.value.frequency == Frequency.HOURLY || recurrenceRule.value.frequency == Frequency.MINUTELY || recurrenceRule.value.frequency == Frequency.SECONDLY) return false

        // The interval is limited to the following max values:
        if (recurrenceRule.value.frequency != null && recurrenceRule.value.interval != null) {
            when (recurrenceRule.value.frequency) {
                Frequency.DAILY -> if (recurrenceRule.value.interval > MAX_DAILY_INTERVAL) return false
                Frequency.WEEKLY -> if (recurrenceRule.value.interval > MAX_WEEKLY_INTERVAL) return false
                Frequency.MONTHLY -> if (recurrenceRule.value.interval > MAX_MONTHLY_INTERVAL) return false
                Frequency.YEARLY -> if (recurrenceRule.value.interval > MAX_YEARLY_INTERVAL) return false
            }
        }

        // COUNT: Because this is pretty expensive BE side, the maximum count value is set to 49 (included).
        if (recurrenceRule.value.count != null && recurrenceRule.value.count > if (iCalendar.isInvitation()) MAX_COUNT_INVITATION else MAX_COUNT) return false

        // UNTIL: The maximum value is currently 01/01/2038 @ 00:00:00 UTC (soon to become 01/01/2200 @ 12:00am (UTC)
        if (recurrenceRule.value.until != null && recurrenceRule.value.until.toInstant().isAfter(MAX_DATE.toInstant())) return false

        // We reject as invalid RRULEs that:
        val iteratorTimezone = if (dateStart.value.hasTime()) iCalendar.iCalTimeZone(dateStart) else TimeZone.getDefault()
        var startIterator = recurrenceRule.getDateIterator(dateStart.value, iteratorTimezone)

        if (startIterator.hasNext()) {
            val startIteratorNext = startIterator.next()
            // Do not generate DTSTART as occurrence (which is mandatory as per RFC).
            if (startIteratorNext != dateStart.value) return false

        } else return false

        // UNTIL: we should use UTC dates if and only if the event is not all-day.
        if (!this.dateStart.value.hasTime() && this.recurrenceRule.value.until?.hasTime() == true) {
            this.recurrenceRule.value = this.recurrenceRule.value.clone(until = ICalDate(this.recurrenceRule.value.until, false))
        }

        // UNTIL: we should transform a DATE into the UTC DATETIME that corresponds to the end of the day in the DTSTART timezone
        if (this.dateStart.value.hasTime() && this.recurrenceRule.value.until?.hasTime() == false) {
            val timezone = iCalendar.timezoneInfo.getTimezone(this.dateStart).timeZone.id
            val newUntil = ICalUtils.allDayICalDateToDateTime(this.recurrenceRule.value.until.toZonedDateTime(timezone), timezone)
            this.recurrenceRule.value = this.recurrenceRule.value.clone(until = newUntil)
        }

        // Special case: YEARLY with BYMONTHDAY but no BYMONTH
        if (recurrenceRule.value.frequency == Frequency.YEARLY && !recurrenceRule.value.byMonthDay.isNullOrEmpty() && recurrenceRule.value.byMonth.isNullOrEmpty()) return false

        // Do not generate any occurrence.
        startIterator = recurrenceRule.getDateIterator(dateStart.value, iteratorTimezone)
        var generatesOccurrences = false
        while (startIterator.hasNext()) {
            val startIteratorNext = startIterator.next()
            if (exceptionDates.firstOrNull { exDates -> exDates.values.any { exDateValue -> exDateValue == startIteratorNext } } == null) {
                generatesOccurrences = true
                break
            }
        }
        if (!generatesOccurrences) return false

        return true
    }

    fun ICalendar.cleanRecurrenceId(isReply: Boolean, parentCalendar: ICalendar? = null): Boolean {
        val event = this.events.firstOrNull() ?: return false

        if (event.recurrenceId?.value == null) return true

        // RECURRENCE-ID: If the event contains both a RECURRENCE-ID and an RRULE, it is rejected (as unsupported) unless it's an invitation with REPLY method.
        if (!isReply && event.recurrenceId?.value != null && event.recurrenceRule?.value != null) return false

        // Allow standalone single edits
        val parentEvents = parentCalendar?.events?.firstOrNull() ?: return true

        // If RECURRENCE-ID is of type DATE-TIME for a parent all-day event, convert to type DATE by keeping just the date part.
        if (event.recurrenceId.value.hasTime() && parentEvents.dateStart?.value?.hasTime() == false) {
            event.recurrenceId.value = ICalDate(event.recurrenceId.value, false)
        }

        // If RECURRENCE-ID is of type DATE for a parent part-day event then we cannot recover and reject (as invalid).
        if (!event.recurrenceId.value.hasTime() && parentEvents.dateStart?.value?.hasTime() == true) {
            return false
        }

        // If RECURRENCE-ID has a timezone different from the parent DTSTART one, re-localize in the parent DTSTART timezone.
        if (event.recurrenceId.value != null && this.timezoneInfo.getTimezone(event.recurrenceId) != parentCalendar.timezoneInfo?.getTimezone(parentEvents.dateStart)) {
            this.timezoneInfo.setTimezone(event.recurrenceId, parentCalendar.timezoneInfo?.getTimezone(parentEvents.dateStart))
        }

        return true
    }

    fun VEvent.cleanExDate(): Boolean {
        // EXDATE: If the event contains an EXDATE, but not an RRULE, reject (as invalid).
        if (!this.exceptionDates.isNullOrEmpty() && this.recurrenceRule?.value == null) return false

        // Otherwise we apply the same operations as for RECURRENCE-ID to each of the dates contained in the property.
        // The re-localization of the timezone here is easier and simpler as the reference timezone is the DTSTART timezone of the same event.

        val exceptionDatesIterator: Iterator<ExceptionDates> = this.exceptionDates.iterator()
        while (exceptionDatesIterator.hasNext()) {
            val exceptionDates = exceptionDatesIterator.next()

            val exceptionDatesValuesIterator: Iterator<ICalDate> = exceptionDates.values.iterator()
            while (exceptionDatesValuesIterator.hasNext()) {
                val exceptionDateValue = exceptionDatesValuesIterator.next()

                // If EXDATE is of type DATE for a part-day event then we cannot recover and reject (as invalid).
                if (!exceptionDateValue.hasTime() && this.dateStart.value.hasTime()) {
                    return false
                }

                // If EXDATE is of type DATE-TIME for an all-day event, convert to type DATE by keeping just the date part.
                if (exceptionDateValue.hasTime() && !this.dateStart.value.hasTime()) {
                    exceptionDates.values[exceptionDates.values.indexOf(exceptionDateValue)] =
                        ICalDate(exceptionDateValue, false)
                }
            }
        }

        return true
    }

    fun VEvent.cleanSequence(): Boolean {
        // SEQUENCE: If not present, assume it's zero. If present, make sure it's a non-negative integer or convert it to zero otherwise.
        if (this.sequence?.value == null || this.sequence.value < 0) this.setSequence(0)
        return true
    }

    fun VEvent.cleanAttendees(): Boolean {
        // If there are more than 100 attendees, reject invitation as unsupported.
        if (this.attendees != null && this.attendees.size > MAX_ATTENDEES) return false

        // In case some attendee emails are repeated, we reject (as unsupported) the invite
        val attendeesEmail = mutableListOf<String>()
        this.attendees?.forEach {
            val email = it.extractEmail() ?: return false
            if (attendeesEmail.contains(email)) return false
            attendeesEmail.add(email)
        }

        return true
    }

    fun ICalendar.cleanTimezones(): Boolean {

        // If EXDATE has a timezone different from the DTSTART, re-localize in the DTSTART timezone.
        this.events.forEach {
            it.exceptionDates.forEach { exDate ->
                if (this.timezoneInfo.getTimezone(exDate) != this.timezoneInfo.getTimezone(this.events.first().dateStart)) {
                    this.timezoneInfo.setTimezone(exDate, this.timezoneInfo.getTimezone(this.events.first().dateStart))
                }
            }

            // DATESTART, DATEEND, RECURRENCE-ID:

            // Extract TZID parameter to timezoneInfo if Biweekly didn't process it during parsing
            if (!this.extractTzid(it.dateStart)) return false
            if (!this.extractTzid(it.dateEnd)) return false
            if (!this.extractTzid(it.recurrenceId)) return false

            // If a TZID is present, we try to convert it into a supported timezone. If not possible, reject (as unsupported) the event. Otherwise localize it to the supported timezone.
            if (!this.convertToSupportedTimezone(it.dateStart)) return false
            if (!this.convertToSupportedTimezone(it.dateEnd)) return false
            if (!this.convertToSupportedTimezone(it.recurrenceId)) return false

            // If it's a floating date (i.e. no TZID present, e.g. DTSTART:20200101T120000), localize it to the x-wr-timezone if supported.
            it.dateStart?.localizeFloatingDate(this)
            it.dateEnd?.localizeFloatingDate(this)
            it.recurrenceId?.localizeFloatingDate(this)

            // If it's a Zulu time, the event is non-recurring and x-wr-timezone is present and supported, localize the date-time to the supported timezone.
            it.dateStart?.localizeZuluTimeDate(this, it)
            it.dateEnd?.localizeZuluTimeDate(this, it)
            it.recurrenceId?.localizeZuluTimeDate(this, it)
        }

        return true
    }

    private fun ICalendar.extractTzid(date: DateOrDateTimeProperty?): Boolean {
        // Extract TZID parameter to timezoneInfo if Biweekly didn't process it during parsing
        if (date?.value != null && !date.getParameter(TZID).isNullOrEmpty()) {
            val supportedTzid = AndroidUtils.fallbackTimeZone(date.getParameter(TZID), fallbackToDefault = false) ?: return false
            this.timezoneInfo.setTimezone(date, TimezoneAssignment(TimeZone.getTimeZone(supportedTzid), supportedTzid))
        }
        return true
    }

    private fun ICalendar.convertToSupportedTimezone(date: DateOrDateTimeProperty?): Boolean {
        // If a TZID is present, we try to convert it into a supported timezone. If not possible, reject (as unsupported) the event. Otherwise localize it to the supported timezone.
        this.timezoneInfo.getTimezone(date)?.let { timezoneAssignment ->
            val supportedTzid = AndroidUtils.fallbackTimeZone(timezoneAssignment.timeZone.id, fallbackToDefault = false) ?: return false
            if (supportedTzid != timezoneAssignment.timeZone.id) this.timezoneInfo.setTimezone(date, TimezoneAssignment(TimeZone.getTimeZone(supportedTzid), supportedTzid))
        }
        return true
    }

    private fun DateOrDateTimeProperty.localizeFloatingDate(iCalendar: ICalendar) {
        // If it's a floating date (i.e. no TZID present, e.g. DTSTART:20200101T120000), localize it to the x-wr-timezone if supported.
        val xWrTimezone = iCalendar.getXWrTimezone()
        if (this.value.hasTime() && iCalendar.timezoneInfo.getTimezone(this) == null && !this.value.rawComponents.toString().contains("Z") && xWrTimezone != null) {
            iCalendar.timezoneInfo.setTimezone(this, TimezoneAssignment(TimeZone.getTimeZone(xWrTimezone), xWrTimezone))
            this.localizeDateToTimezone(xWrTimezone)
        }
    }

    private fun DateOrDateTimeProperty.localizeZuluTimeDate(iCalendar: ICalendar, event: VEvent) {
        // If it's a Zulu time, the event is non-recurring and x-wr-timezone is present and supported, localize the date-time to the supported timezone.
        val xWrTimezone = iCalendar.getXWrTimezone()
        if (event.recurrenceRule?.value == null && this.value.hasTime() && iCalendar.timezoneInfo.getTimezone(this) == null && this.value.rawComponents.toString().contains("Z") && xWrTimezone != null) {
            iCalendar.timezoneInfo.setTimezone(this, TimezoneAssignment(TimeZone.getTimeZone(xWrTimezone), xWrTimezone))
            this.setParameter(TZID, xWrTimezone)
        }
    }

    private fun DateOrDateTimeProperty.localizeDateToTimezone(timezone: String) {
        this.value = ICalDate(
            Date.from(
                this.value.toInstant().atZone(ZoneId.systemDefault()).withZoneSameLocal(ZoneId.of(timezone))
                    .toInstant()
            ), true
        )
        this.setParameter(TZID, timezone)
    }

    private fun ICalendar.isInvitation(): Boolean {
        return this.method?.isReply == true || this.method?.isCounter == true || this.method?.isRefresh == true ||
                this.method?.isRequest == true || this.method?.isCancel == true || this.method?.isAdd == true ||
                this.method?.isDeclineCounter == true
    }
}
