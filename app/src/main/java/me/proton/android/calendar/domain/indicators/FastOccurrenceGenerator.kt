package me.proton.android.calendar.domain.indicators

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

// one occurrence: start/end instants and its number (1 = the event's first occurrence)
internal data class ExpandedOccurrence(val start: Instant, val end: Instant, val number: Int)

/**
 * Expands daily and weekly-on-weekdays repeats with plain date math
 * (so the skeleton can jump straight to the visible window instead of iterating from the event's start)
 */
internal object FastOccurrenceGenerator {

    private const val SECONDS_PER_DAY = 24L * 60L * 60L
    private const val SAFETY_CAP = 100_000

    /**
     * Plain daily repeat (FREQ=DAILY, no other BY* rules, no COUNT/UNTIL)
     * Occurrence n is (n-1) intervals after the first, so we jump to the window directly
     */
    fun dailyOccurrencesInWindow(
        dtStartInstant: Instant,
        durationSeconds: Long,
        intervalDays: Int,
        expansionZone: ZoneId,
        windowStartInstant: Instant,
        windowEndInstant: Instant,
    ): List<ExpandedOccurrence> {
        if (intervalDays < 1 || durationSeconds <= 0) return emptyList()

        val dtStartLocal = dtStartInstant.atZone(expansionZone).toLocalDateTime()
        // same wall-clock time each occurrence, even across DST. when the clock goes back a time happens twice, so
        // pick the later one to match biweekly.
        fun startOf(occurrence: Int): Instant =
            dtStartLocal.plusDays((occurrence - 1).toLong() * intervalDays)
                .atZone(expansionZone).withLaterOffsetAtOverlap().toInstant()

        // jump near the window with day math, then nudge to the first occurrence inside it
        val daysIntoWindow = (windowStartInstant.epochSecond - dtStartInstant.epochSecond) / SECONDS_PER_DAY
        var occurrence = (daysIntoWindow / intervalDays).coerceAtLeast(0L).toInt() + 1
        while (occurrence > 1 &&
            startOf(occurrence - 1).plusSeconds(durationSeconds).isAfter(windowStartInstant)
        ) occurrence--
        while (!startOf(occurrence).plusSeconds(durationSeconds).isAfter(windowStartInstant)) occurrence++

        val result = ArrayList<ExpandedOccurrence>()
        var start = startOf(occurrence)
        while (start.isBefore(windowEndInstant)) {
            result.add(ExpandedOccurrence(start, start.plusSeconds(durationSeconds), occurrence))
            occurrence++
            start = startOf(occurrence)
        }
        return result
    }

    /**
     * Plain weekly repeat on a set of weekdays (FREQ=WEEKLY;BYDAY=..., no ordinals like 2MO, no COUNT/UNTIL).
     * Repeats every [intervalWeeks] weeks on each [byDays] day at the start time;
     * the first week skips days before the start.
     */
    fun weeklyOccurrencesInWindow(
        dtStartInstant: Instant,
        durationSeconds: Long,
        intervalWeeks: Int,
        byDays: Set<DayOfWeek>,
        weekStart: DayOfWeek,
        expansionZone: ZoneId,
        windowStartInstant: Instant,
        windowEndInstant: Instant,
    ): List<ExpandedOccurrence>? {
        if (intervalWeeks < 1 || durationSeconds <= 0 || byDays.isEmpty()) return null

        val dtStartZdt = dtStartInstant.atZone(expansionZone)
        val dtStartDate = dtStartZdt.toLocalDate()
        val dtStartTime = dtStartZdt.toLocalTime()
        if (dtStartDate.dayOfWeek !in byDays) return null

        // where a weekday sits in the week, 0 = weekStart
        fun DayOfWeek.offset(): Int = (this.value - weekStart.value + 7) % 7
        val dtStartOffset = dtStartDate.dayOfWeek.offset()
        val byOffsets = byDays.map { it.offset() }.sorted()
        val perWeek = byOffsets.size
        val firstWeekCount = byOffsets.count { it >= dtStartOffset }
        val dtStartWeekStart = dtStartDate.minusDays(dtStartOffset.toLong())

        // occurrences before the given week (week 0 = start week)
        fun occurrencesBefore(week: Long): Int = if (week <= 0L) 0 else (firstWeekCount + (week - 1L) * perWeek).toInt()

        // same wall-clock / DST rule as daily
        fun LocalDate.startOf(dayOffset: Int): Instant =
            LocalDateTime.of(plusDays(dayOffset.toLong()), dtStartTime)
                .atZone(expansionZone).withLaterOffsetAtOverlap().toInstant()

        // jump to roughly the window's week (one early to be safe), keeping count exact
        val windowStartDate = windowStartInstant.atZone(expansionZone).toLocalDate()
        val windowWeekStart = windowStartDate.minusDays(windowStartDate.dayOfWeek.offset().toLong())
        val weeksFromStart = ChronoUnit.WEEKS.between(dtStartWeekStart, windowWeekStart)
        var activeWeek = if (weeksFromStart <= 0L) 0L else (weeksFromStart / intervalWeeks - 1L).coerceAtLeast(0L)

        val result = ArrayList<ExpandedOccurrence>()
        var count = occurrencesBefore(activeWeek)
        var iterations = 0
        while (iterations++ < SAFETY_CAP) {
            val weekStartDate = dtStartWeekStart.plusWeeks(activeWeek * intervalWeeks)
            val offsetsThisWeek = if (activeWeek == 0L) byOffsets.filter { it >= dtStartOffset } else byOffsets

            // days are sorted, so if the first is past the window the rest are too
            val firstOff = offsetsThisWeek.firstOrNull()
            if (firstOff != null && !weekStartDate.startOf(firstOff).isBefore(windowEndInstant)) break

            // count keeps advancing through skipped weeks, so it stays correct in the window
            for (off in offsetsThisWeek) {
                val start = weekStartDate.startOf(off)
                if (!start.isBefore(windowEndInstant)) break
                count++
                val end = start.plusSeconds(durationSeconds)
                if (end.isAfter(windowStartInstant)) {
                    result.add(ExpandedOccurrence(start, end, count))
                }
            }
            activeWeek++
        }
        return result
    }
}
