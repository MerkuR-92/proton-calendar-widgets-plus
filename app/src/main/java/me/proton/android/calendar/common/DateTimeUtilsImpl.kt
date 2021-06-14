package me.proton.android.calendar.common

import biweekly.util.ICalDate
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.utils.DateTimeUtils
import me.proton.android.calendar.presentation.calendar.MiniCalendarItemAdapter
import java.text.SimpleDateFormat
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.ChronoField
import java.time.temporal.ChronoUnit
import java.time.temporal.IsoFields
import java.util.*
import java.util.Locale.getDefault

object DateTimeUtilsImpl : DateTimeUtils {

    override fun ZonedDateTime.formatDate(timeZoneId: String): String {
        return this
            .withZoneSameInstant(ZoneId.of(timeZoneId))
            .toLocalDate()
            .format(
                DateTimeFormatter
                    .ofLocalizedDate(FormatStyle.FULL)
                    .withLocale(getLocaleForFormatting())
            )
    }

    override fun ZonedDateTime.formatTime(timeZoneId: String, is24Hour: Boolean): String = this.withZoneSameInstant(ZoneId.of(timeZoneId)).toLocalTime().formatTime(is24Hour)

    /**
     * @param excludeTo will exclude exact toDateTime from rightmost range value
     */
    override fun ZonedDateTime.isBetween(fromDateTime: ZonedDateTime, toDateTime: ZonedDateTime, excludeFrom: Boolean, excludeTo: Boolean): Boolean {

        val thisInstant = this.toInstant()
        val fromInstant = fromDateTime.toInstant()
        val toInstant = toDateTime.toInstant()

        return (if (excludeFrom) thisInstant > fromInstant else thisInstant >= fromInstant) && (if (excludeTo) thisInstant < toInstant else thisInstant <= toInstant)
    }

    override fun LocalDate.isBetween(fromDate: LocalDate, toDate: LocalDate): Boolean {
        val thisLocalDate = this.atTime(LocalTime.MIDNIGHT).atZone(ZoneOffset.UTC)
        val fromZonedDateTime = fromDate.atTime(LocalTime.MIDNIGHT).atZone(ZoneOffset.UTC)
        val toZonedDateTime = toDate.atTime(LocalTime.MAX).atZone(ZoneOffset.UTC)

        return thisLocalDate.isBetween(fromZonedDateTime, toZonedDateTime, false, false)
    }

    /**
     * Calculate ISO week number for given date, taking custom week start into account.
     */
    override fun LocalDate.weekNumber(startWeekOn: DayOfWeek): Int {

        val firstDayOfTheWeekNumber = this.dayOfWeek.value - startWeekOn.value
        val firstDayOfTheWeekOffset = if (firstDayOfTheWeekNumber < 0) firstDayOfTheWeekNumber + MiniCalendarItemAdapter.CalendarSettings.DAYS_IN_A_WEEK else firstDayOfTheWeekNumber

        var monday: LocalDate = this.minusDays(firstDayOfTheWeekOffset.toLong())
        while (monday.dayOfWeek != DayOfWeek.MONDAY) {
            monday = monday.plusDays(1)
        }

        return monday.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)

    }

    override fun LocalDate.toDate(timeZoneId: String?): Date = Date.from(this.atStartOfDay(ZoneId.of(timeZoneId ?: ZoneId.systemDefault().id)).toInstant())

    override fun DayOfWeek.format(firstLetter: Boolean): String {
        val formatted = this.getDisplayName(
            TextStyle.FULL,
            getLocaleForFormatting()
        )
        return if (firstLetter) formatted.firstOrNull()?.toString() ?: "" else formatted
    }

    override fun DayOfWeek.toBiweeklyDayOfWeek(): biweekly.util.DayOfWeek {
        return biweekly.util.DayOfWeek.values()[(this.ordinal + 1) % 7]
    }

    override fun biweekly.util.DayOfWeek.toDayOfWeek(): DayOfWeek {
        return DayOfWeek.of((this.calendarConstant)).minus(1)
    }

    override fun ICalDate.toZonedDateTime(timezone: String): ZonedDateTime {
        return if (this.hasTime()) {
            ZonedDateTime.ofInstant(this.toInstant(), ZoneId.of(timezone))
        } else {
            this.toInstant().atZone(ZoneId.systemDefault()).withZoneSameLocal(ZoneId.of(timezone))
        }
    }

    override fun Date.toZonedDateTime(timezone: String, isAllDay: Boolean): ZonedDateTime {
        return if (!isAllDay) {
            ZonedDateTime.ofInstant(this.toInstant(), ZoneId.of(timezone))
        } else {
            this.toInstant().atZone(ZoneId.systemDefault()).withZoneSameLocal(ZoneId.of(timezone))
        }
    }

    override fun formatTimeZoneId(timeZoneId: String, forInstant: Instant, displayId: Boolean): String {
        val rawOffset = TimeZone.getTimeZone(timeZoneId).getOffset(Date.from(forInstant).time).toLong()
        val offsetLocalTime = LocalTime.MIDNIGHT.plus(if (rawOffset < 0) -rawOffset else rawOffset, ChronoUnit.MILLIS)

        val offset = "${offsetLocalTime.hour}${if (offsetLocalTime.minute > 0) ":${offsetLocalTime.minute}" else ""}"

        return "${if (displayId) "$timeZoneId " else ""}(GMT${if (rawOffset < 0) "-" else "+"}${offset})"
    }

    override fun areTimeZoneOffsetsDifferent(timeZoneIdA: String, timeZoneIdB: String, forInstant: Instant?): Boolean? {

        if (!TimeZone.getAvailableIDs().contains(timeZoneIdA) || !TimeZone.getAvailableIDs().contains(timeZoneIdB)) {
            return null
        }

        val offsetA = TimeZone.getTimeZone(timeZoneIdA).getOffset(Date.from(forInstant ?: Instant.now()).time).toLong()
        val offsetB = TimeZone.getTimeZone(timeZoneIdB).getOffset(Date.from(forInstant ?: Instant.now()).time).toLong()

        return offsetA != offsetB
    }

    override fun allDayICalDateToDateTime(zonedDateTime: ZonedDateTime, timeZoneId: String): ICalDate {
        return ICalDate(
            Date.from(
                ZonedDateTime.of(
                    zonedDateTime.toLocalDate(),
                    LocalTime.of(23, 59, 59),
                    ZoneId.of(timeZoneId)
                ).withZoneSameInstant(
                    ZoneId.of(timeZoneId)
                ).toInstant()
            ),
            true
        )
    }

    override fun partDayICalDateToDate(iCalDate: ICalDate, timeZoneId: String): ICalDate {
        return ICalDate(
            Date.from(
                ZonedDateTime.of(
                    iCalDate.toInstant().atZone(ZoneId.of(timeZoneId)).toLocalDate(),
                    LocalTime.MIDNIGHT,
                    ZoneId.systemDefault()
                ).toInstant()
            ), false
        )
    }

    override fun startEndOverlapsWithFullDayRange(startDateTime: ZonedDateTime, endDateTime: ZonedDateTime, fromDate: LocalDate, toDate: LocalDate, timeZoneId: String): Boolean {

        val fromDateTime = fromDate.atStartOfDay(ZoneId.of(timeZoneId))
        val toDateTime = toDate.plusDays(1).atStartOfDay(ZoneId.of(timeZoneId))

        return (startDateTime.withZoneSameLocal(ZoneId.of(timeZoneId)).isBetween(fromDateTime, toDateTime, excludeFrom = false, excludeTo = true)) // starts in the range
                || (endDateTime.withZoneSameLocal(ZoneId.of(timeZoneId)).isBetween(fromDateTime, toDateTime, excludeFrom = true, excludeTo = false)) // ends in the range
                || ((startDateTime.withZoneSameLocal(ZoneId.of(timeZoneId)).isBefore(fromDateTime)) && endDateTime.withZoneSameLocal(ZoneId.of(timeZoneId)).isAfter(toDateTime)) // starts before or ends after range, but happens during range
    }

    /**
     * If supplied TimeZone is not supported, fallback retaining UTC offset.
     */
    override fun fallbackTimeZone(timeZone: String, fallbackToDefault: Boolean): String? {

        return if (allowedTimezoneIds.contains(timeZone)) {
            timeZone
        } else {

            val aliasTimezone = aliasesTimezonesMap[timeZone]
            if (aliasTimezone != null) {
                fallbackTimeZone(aliasTimezone, fallbackToDefault)
            } else if (TimeZone.getAvailableIDs().contains(timeZone)) {

                val offset = TimeZone.getTimeZone(timeZone).getOffset(Date.from(Instant.now()).time)
                val alternativeTimezones = TimeZone.getAvailableIDs(offset).filter { allowedTimezoneIds.contains(it) }

                val alternative = alternativeTimezones.firstOrNull { it.startsWith(timeZone.substringBefore("/")) } ?: alternativeTimezones.firstOrNull()

                alternative ?: if (fallbackToDefault) TimeZone.getDefault().id else null
            } else if (!windowsTimeZoneMap[timeZone.toLowerCase(getDefault()).replace(".", "")].isNullOrEmpty()) {
                val windowsIdReplacement = windowsTimeZoneMap[timeZone.toLowerCase(getDefault()).replace(".", "")]
                    ?: return if (fallbackToDefault) TimeZone.getDefault().id
                    else null
                fallbackTimeZone(windowsIdReplacement, fallbackToDefault)
            } else {
                if (fallbackToDefault) TimeZone.getDefault().id
                else null
            }
        }
    }

    override fun LocalTime.formatTime(is24Hour: Boolean?): String {
        return if (is24Hour == true) {
            this.format(DateTimeFormatter.ofPattern("HH:mm").withLocale(getLocaleForFormatting()))
        } else if (is24Hour == false) {
            this.format(DateTimeFormatter.ofPattern("hh:mm a").withLocale(getLocaleForFormatting()))
        } else {
            this.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(getLocaleForFormatting()))
        }
    }

    // TODO add and change parameters for customisation
    override fun LocalDate.formatWithDayOfWeek(showDayOfWeek: Boolean): String {

        val dateTimeFormatter = if (showDayOfWeek) {
            DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(getLocaleForFormatting())
        } else {
            DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(getLocaleForFormatting())
        }

        return this.format(dateTimeFormatter)
    }

    override fun LocalDate.isLastDayOfWeekInMonth() = this.plusDays(7).monthValue != this.monthValue

// TODO add function for calculating how many days-of-week are there in a given month, we can use it for "backwards" formatting then

    override fun LocalDate.weekInMonth(): Int = this.get(ChronoField.ALIGNED_WEEK_OF_MONTH)

    /**
     * Returns "January", etc.
     */
    override fun LocalDate.formatMonth(capitalize: Boolean): String {
        val dateFormat = SimpleDateFormat("LLLL", getLocaleForFormatting())
        val formattedMonth = dateFormat.format(Date.from(this.atStartOfDay(ZoneId.systemDefault()).toInstant()))
        if (capitalize)
            return formattedMonth.substring(0, 1).toUpperCase(Locale.getDefault()) +
                    formattedMonth.substring(1).toLowerCase(Locale.getDefault())
        return formattedMonth
    }

    override fun LocalDate.formatDayOfWeek(short: Boolean): String {
        val dateFormat = SimpleDateFormat(if (short) "EEEEE" else "EEEE", getLocaleForFormatting())
        return dateFormat.format(Date.from(this.atStartOfDay(ZoneId.systemDefault()).toInstant()))
    }

    /**
     * We only allow Locales used to format date & time that our application is translated to.
     */
    override fun getLocaleForFormatting(): Locale {
        return when (Locale.getDefault()) {
            // add mapping for other supported Locales
            else -> Locale.US
        }
    }

    override fun Collection<CalendarsRepository.EventsWindow>.getFullyOverlappingWindow(eventsWindow: CalendarsRepository.EventsWindow): CalendarsRepository.EventsWindow? {
        return this.find {
            eventsWindow.fromDate.isBetween(it.fromDate, it.toDate) && eventsWindow.toDate.isBetween(it.fromDate, it.toDate)
        }
    }


}
