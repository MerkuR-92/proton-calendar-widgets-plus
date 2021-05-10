package me.proton.android.calendar.common

import me.proton.android.calendar.domain.utils.DateTimeUtils
import me.proton.android.calendar.presentation.calendar.MiniCalendarItemAdapter
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.IsoFields
import java.util.*

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

    override fun ZonedDateTime.formatTime(timeZoneId: String, is24Hour: Boolean): String = this.withZoneSameInstant(ZoneId.of(timeZoneId)).toLocalTime().format(is24Hour)

    /**
     * @param excludeTo will exclude exact toDateTime from rightmost range value
     */
    override fun ZonedDateTime.isBetween(fromDateTime: ZonedDateTime, toDateTime: ZonedDateTime, excludeFrom: Boolean, excludeTo: Boolean): Boolean {

        val thisInstant = this.toInstant()
        val fromInstant = fromDateTime.toInstant()
        val toInstant = toDateTime.toInstant()

        return (if (excludeFrom) thisInstant > fromInstant else thisInstant >= fromInstant) && (if (excludeTo) thisInstant < toInstant else thisInstant <= toInstant)
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

}
