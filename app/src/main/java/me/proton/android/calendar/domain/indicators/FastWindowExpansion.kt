package me.proton.android.calendar.domain.indicators

import biweekly.util.Frequency
import biweekly.util.Recurrence
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId

/**
 * window-relative expansion for plain daily and weekly-on-weekdays recurrences, shared by the skeleton
 * and the real expansion so neither walks every occurrence from an old dtstart; returns null for
 * anything it can't fast-path so callers fall back to full biweekly generation
 */
internal object FastWindowExpansion {

    fun occurrencesInWindow(
        recurrence: Recurrence,
        dtStartInstant: Instant,
        durationSeconds: Long,
        fullDay: Boolean,
        expansionZone: ZoneId,
        windowStartInstant: Instant,
        windowEndInstant: Instant,
    ): List<ExpandedOccurrence>? {
        if (fullDay || durationSeconds <= 0) return null
        return when {
            isSimpleInfiniteDaily(recurrence) -> FastOccurrenceGenerator.dailyOccurrencesInWindow(
                dtStartInstant = dtStartInstant,
                durationSeconds = durationSeconds,
                intervalDays = recurrence.interval ?: 1,
                expansionZone = expansionZone,
                windowStartInstant = windowStartInstant,
                windowEndInstant = windowEndInstant,
            )
            isSimpleInfiniteWeekly(recurrence) -> {
                val byDays = recurrence.byDay.mapNotNull { bd ->
                    bd.day?.let { runCatching { DayOfWeek.valueOf(it.name) }.getOrNull() }
                }.toSet()
                // fall back if any byday didn't convert cleanly
                if (byDays.size != recurrence.byDay.size) return null
                val wkst = recurrence.workweekStarts
                    ?.let { runCatching { DayOfWeek.valueOf(it.name) }.getOrNull() } ?: DayOfWeek.MONDAY
                FastOccurrenceGenerator.weeklyOccurrencesInWindow(
                    dtStartInstant = dtStartInstant,
                    durationSeconds = durationSeconds,
                    intervalWeeks = recurrence.interval ?: 1,
                    byDays = byDays,
                    weekStart = wkst,
                    expansionZone = expansionZone,
                    windowStartInstant = windowStartInstant,
                    windowEndInstant = windowEndInstant,
                )
            }
            else -> null
        }
    }

    // plain daily, no by* parts and no count/until
    fun isSimpleInfiniteDaily(recurrence: Recurrence): Boolean {
        if (recurrence.frequency != Frequency.DAILY) return false
        if (recurrence.count != null || recurrence.until != null) return false
        return recurrence.byDay.isEmpty() && recurrence.byMonth.isEmpty() &&
            recurrence.byMonthDay.isEmpty() && recurrence.byYearDay.isEmpty() &&
            recurrence.byWeekNo.isEmpty() && recurrence.bySetPos.isEmpty() &&
            recurrence.byHour.isEmpty() && recurrence.byMinute.isEmpty() &&
            recurrence.bySecond.isEmpty()
    }

    // plain weekly on a weekday list, no ordinals/count/until/other by* parts
    fun isSimpleInfiniteWeekly(recurrence: Recurrence): Boolean {
        if (recurrence.frequency != Frequency.WEEKLY) return false
        if (recurrence.count != null || recurrence.until != null) return false
        if (recurrence.byDay.isEmpty() || recurrence.byDay.any { it.num != null }) return false
        return recurrence.byMonth.isEmpty() && recurrence.byMonthDay.isEmpty() &&
            recurrence.byYearDay.isEmpty() && recurrence.byWeekNo.isEmpty() &&
            recurrence.bySetPos.isEmpty() && recurrence.byHour.isEmpty() &&
            recurrence.byMinute.isEmpty() && recurrence.bySecond.isEmpty()
    }
}
