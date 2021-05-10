package me.proton.android.calendar.domain.utils

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.*

interface DateTimeUtils {

    fun ZonedDateTime.formatDate(timeZoneId: String): String

    fun ZonedDateTime.formatTime(timeZoneId: String, is24Hour: Boolean): String

    /**
     * @param excludeTo will exclude exact toDateTime from rightmost range value
     */
    fun ZonedDateTime.isBetween(fromDateTime: ZonedDateTime, toDateTime: ZonedDateTime, excludeFrom: Boolean, excludeTo: Boolean): Boolean

    /**
     * Calculate ISO week number for given date, taking custom week start into account.
     */
    fun LocalDate.weekNumber(startWeekOn: DayOfWeek): Int
    fun LocalDate.toDate(timeZoneId: String? = null): Date
    fun DayOfWeek.format(firstLetter: Boolean = false): String
    fun DayOfWeek.toBiweeklyDayOfWeek(): biweekly.util.DayOfWeek
}
