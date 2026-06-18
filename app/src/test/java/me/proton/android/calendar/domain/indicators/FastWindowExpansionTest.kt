package me.proton.android.calendar.domain.indicators

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import me.proton.android.calendar.common.utils.ICalUtilsImpl
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * guards which rrules take the fast path vs fall back to biweekly, since getting that boundary wrong
 * would silently produce wrong occurrences
 */
class FastWindowExpansionTest {

    private val zone = ZoneId.of("UTC")
    private val dtStart = LocalDateTime.of(2015, 1, 5, 9, 0) // a Monday
    private val durationSec = 3600L

    private fun recurrence(rrule: String) = ICalUtilsImpl.parseRecurrence(rrule)!!

    // classification: plain daily/weekly-on-weekdays qualify; COUNT/UNTIL are allowed (applied as bounds)

    @Test
    fun `simple daily qualifies, with or without COUNT or UNTIL`() {
        assertThat(FastWindowExpansion.isSimpleDaily(recurrence("FREQ=DAILY"))).isTrue()
        assertThat(FastWindowExpansion.isSimpleDaily(recurrence("FREQ=DAILY;INTERVAL=3"))).isTrue()
        assertThat(FastWindowExpansion.isSimpleDaily(recurrence("FREQ=DAILY;COUNT=10"))).isTrue()
        assertThat(FastWindowExpansion.isSimpleDaily(recurrence("FREQ=DAILY;UNTIL=20251231T000000Z"))).isTrue()
    }

    @Test
    fun `daily with BY parts does not qualify`() {
        assertThat(FastWindowExpansion.isSimpleDaily(recurrence("FREQ=DAILY;BYDAY=MO"))).isFalse()
        assertThat(FastWindowExpansion.isSimpleDaily(recurrence("FREQ=DAILY;BYMONTH=1"))).isFalse()
    }

    @Test
    fun `weekly and monthly do not qualify as daily`() {
        assertThat(FastWindowExpansion.isSimpleDaily(recurrence("FREQ=WEEKLY;BYDAY=MO"))).isFalse()
        assertThat(FastWindowExpansion.isSimpleDaily(recurrence("FREQ=MONTHLY"))).isFalse()
    }

    @Test
    fun `simple weekly with weekday list qualifies, with or without COUNT or UNTIL`() {
        assertThat(FastWindowExpansion.isSimpleWeekly(recurrence("FREQ=WEEKLY;BYDAY=MO,WE,FR"))).isTrue()
        assertThat(FastWindowExpansion.isSimpleWeekly(recurrence("FREQ=WEEKLY;INTERVAL=2;BYDAY=TU"))).isTrue()
        assertThat(FastWindowExpansion.isSimpleWeekly(recurrence("FREQ=WEEKLY;BYDAY=MO;COUNT=5"))).isTrue()
        assertThat(
            FastWindowExpansion.isSimpleWeekly(recurrence("FREQ=WEEKLY;BYDAY=MO;UNTIL=20251231T000000Z"))
        ).isTrue()
    }

    @Test
    fun `weekly without BYDAY does not qualify`() {
        assertThat(FastWindowExpansion.isSimpleWeekly(recurrence("FREQ=WEEKLY"))).isFalse()
    }

    @Test
    fun `weekly with ordinal BYDAY does not qualify`() {
        // ordinal byday (second monday), not a plain weekday list
        assertThat(FastWindowExpansion.isSimpleWeekly(recurrence("FREQ=WEEKLY;BYDAY=2MO"))).isFalse()
    }

    @Test
    fun `weekly with extra BY parts does not qualify`() {
        assertThat(FastWindowExpansion.isSimpleWeekly(recurrence("FREQ=WEEKLY;BYDAY=MO;BYMONTH=1"))).isFalse()
    }

    @Test
    fun `monthly and yearly never qualify`() {
        assertThat(FastWindowExpansion.isSimpleDaily(recurrence("FREQ=MONTHLY"))).isFalse()
        assertThat(FastWindowExpansion.isSimpleWeekly(recurrence("FREQ=MONTHLY"))).isFalse()
        assertThat(FastWindowExpansion.isSimpleDaily(recurrence("FREQ=YEARLY"))).isFalse()
        assertThat(FastWindowExpansion.isSimpleWeekly(recurrence("FREQ=YEARLY"))).isFalse()
    }

    // occurrencesInWindow returns null for anything it can't fast-path so the caller falls back

    private fun occurrencesInWindow(rrule: String, fullDay: Boolean = false, durationSeconds: Long = durationSec) =
        FastWindowExpansion.occurrencesInWindow(
            recurrence = recurrence(rrule),
            dtStartInstant = dtStart.atZone(zone).toInstant(),
            durationSeconds = durationSeconds,
            fullDay = fullDay,
            expansionZone = zone,
            windowStartInstant = LocalDate.of(2025, 9, 22).atStartOfDay(zone).toInstant(),
            windowEndInstant = LocalDate.of(2025, 9, 29).atStartOfDay(zone).toInstant(),
        )

    @Test
    fun `all-day is never fast-pathed`() {
        assertThat(occurrencesInWindow("FREQ=DAILY", fullDay = true)).isNull()
    }

    @Test
    fun `zero or negative duration is never fast-pathed`() {
        assertThat(occurrencesInWindow("FREQ=DAILY", durationSeconds = 0)).isNull()
        assertThat(occurrencesInWindow("FREQ=DAILY", durationSeconds = -1)).isNull()
    }

    @Test
    fun `monthly falls back`() {
        assertThat(occurrencesInWindow("FREQ=MONTHLY")).isNull()
    }

    @Test
    fun `bounded daily and weekly are fast-pathed`() {
        // COUNT/UNTIL no longer fall back; they are applied as bounds inside the generator
        assertThat(occurrencesInWindow("FREQ=DAILY;COUNT=3")).isNotNull()
        assertThat(occurrencesInWindow("FREQ=DAILY;UNTIL=20260101T090000Z")).isNotNull()
        assertThat(occurrencesInWindow("FREQ=WEEKLY;BYDAY=MO;COUNT=3")).isNotNull()
    }

    @Test
    fun `date-only UNTIL falls back`() {
        // a date-only UNTIL on a timed recurrence is left to biweekly to avoid instant-comparison drift
        assertThat(occurrencesInWindow("FREQ=DAILY;UNTIL=20260101")).isNull()
    }

    @Test
    fun `simple daily and weekly are fast-pathed`() {
        assertThat(occurrencesInWindow("FREQ=DAILY")).isNotNull()
        assertThat(occurrencesInWindow("FREQ=WEEKLY;BYDAY=MO,WE,FR")).isNotNull()
    }

    @Test
    fun `occurrencesInWindow daily matches the underlying generator`() {
        val winStart = LocalDate.of(2025, 9, 22).atStartOfDay(zone).toInstant()
        val winEnd = LocalDate.of(2025, 9, 29).atStartOfDay(zone).toInstant()
        val direct = FastOccurrenceGenerator.dailyOccurrencesInWindow(
            dtStartInstant = dtStart.atZone(zone).toInstant(),
            durationSeconds = durationSec,
            intervalDays = 1,
            expansionZone = zone,
            windowStartInstant = winStart,
            windowEndInstant = winEnd,
        )
        assertThat(occurrencesInWindow("FREQ=DAILY")).isEqualTo(direct)
    }
}
