package me.proton.android.calendar.domain.utils

import androidx.annotation.VisibleForTesting
import biweekly.util.ICalDate
import me.proton.android.calendar.common.DateTimeUtilsImpl.isBetween
import me.proton.android.calendar.common.allowedTimezoneIds
import me.proton.android.calendar.domain.CalendarsRepository
import java.text.SimpleDateFormat
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoField
import java.time.temporal.ChronoUnit
import java.util.*

interface DateTimeUtils {

    fun ZonedDateTime.formatDate(timeZoneId: String): String

    fun ZonedDateTime.formatTime(timeZoneId: String, is24Hour: Boolean): String

    /**
     * @param excludeTo will exclude exact toDateTime from rightmost range value
     */
    fun ZonedDateTime.isBetween(fromDateTime: ZonedDateTime, toDateTime: ZonedDateTime, excludeFrom: Boolean, excludeTo: Boolean): Boolean

    fun LocalDate.isBetween(fromDate: LocalDate, toDate: LocalDate): Boolean

    /**
     * Calculate ISO week number for given date, taking custom week start into account.
     */
    fun LocalDate.weekNumber(startWeekOn: DayOfWeek): Int
    fun LocalDate.toDate(timeZoneId: String? = null): Date
    fun DayOfWeek.format(firstLetter: Boolean = false): String
    fun DayOfWeek.toBiweeklyDayOfWeek(): biweekly.util.DayOfWeek
    fun biweekly.util.DayOfWeek.toDayOfWeek(): DayOfWeek

    fun ICalDate.toZonedDateTime(timezone: String): ZonedDateTime

    fun Date.toZonedDateTime(timezone: String, isAllDay: Boolean): ZonedDateTime

    fun formatTimeZoneId(timeZoneId: String, forInstant: Instant, displayId: Boolean = true): String

    fun areTimeZoneOffsetsDifferent(timeZoneIdA: String, timeZoneIdB: String, forInstant: Instant? = null): Boolean?

    fun allDayICalDateToDateTime(zonedDateTime: ZonedDateTime, timeZoneId: String): ICalDate
    fun partDayICalDateToDate(iCalDate: ICalDate, timeZoneId: String): ICalDate

    fun startEndOverlapsWithFullDayRange(startDateTime: ZonedDateTime, endDateTime: ZonedDateTime, fromDate: LocalDate, toDate: LocalDate, timeZoneId: String): Boolean

    /**
     * If supplied TimeZone is not supported, fallback retaining UTC offset.
     */
    fun fallbackTimeZone(timeZone: String, fallbackToDefault: Boolean = true): String?

    fun LocalTime.formatTime(is24Hour: Boolean?): String {
        return if (is24Hour == true) {
            this.format(DateTimeFormatter.ofPattern("HH:mm").withLocale(getLocaleForFormatting()))
        } else if (is24Hour == false) {
            this.format(DateTimeFormatter.ofPattern("hh:mm a").withLocale(getLocaleForFormatting()))
        } else {
            this.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(getLocaleForFormatting()))
        }
    }

    // TODO add and change parameters for customisation
    fun LocalDate.formatWithDayOfWeek(showDayOfWeek: Boolean = false): String

    fun LocalDate.isLastDayOfWeekInMonth(): Boolean

// TODO add function for calculating how many days-of-week are there in a given month, we can use it for "backwards" formatting then

    fun LocalDate.weekInMonth(): Int

    /**
     * Returns "January", etc.
     */
    fun LocalDate.formatMonth(capitalize: Boolean = false): String

    fun LocalDate.formatDayOfWeek(short: Boolean = false): String

    /**
     * We only allow Locales used to format date & time that our application is translated to.
     */
    fun getLocaleForFormatting(): Locale

    /**
     * Gets [EventsWindow] from the collection if argument fully overlaps with it.
     */
    fun Collection<CalendarsRepository.EventsWindow>.getFullyOverlappingWindow(eventsWindow: CalendarsRepository.EventsWindow): CalendarsRepository.EventsWindow?

}
