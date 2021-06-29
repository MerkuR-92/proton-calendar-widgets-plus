package me.proton.android.calendar.common

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import me.proton.android.calendar.common.DateTimeUtilsImpl.calculateWeekNumberBetween
import me.proton.android.calendar.common.DateTimeUtilsImpl.getFullyOverlappingWindow
import me.proton.android.calendar.common.DateTimeUtilsImpl.getLastWeekOfMonthOffset
import me.proton.android.calendar.domain.CalendarsRepository
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDate

internal class DateTimeUtilsTest {

    @Test
    fun `check if EventsWindow fully overlaps with collection`() {

        val timeZone = "Europe/Zurich"

        val eventsWindows = listOf(
            CalendarsRepository.EventsWindow(LocalDate.of(2021, 6, 1), LocalDate.of(2021, 6, 30), timeZone),
            CalendarsRepository.EventsWindow(LocalDate.of(2021, 8, 1), LocalDate.of(2021, 8, 31), timeZone)
        )

        val windowBefore =
            CalendarsRepository.EventsWindow(LocalDate.of(2021, 5, 1), LocalDate.of(2021, 5, 31), timeZone)
        val windowOverlappingStart =
            CalendarsRepository.EventsWindow(LocalDate.of(2021, 5, 1), LocalDate.of(2021, 6, 1), timeZone)
        val windowInside =
            CalendarsRepository.EventsWindow(LocalDate.of(2021, 6, 10), LocalDate.of(2021, 6, 10), timeZone)
        val windowBetween =
            CalendarsRepository.EventsWindow(LocalDate.of(2021, 7, 1), LocalDate.of(2021, 7, 31), timeZone)
        val windowOverlappingEnd =
            CalendarsRepository.EventsWindow(LocalDate.of(2021, 8, 31), LocalDate.of(2021, 9, 1), timeZone)
        val windowAfter =
            CalendarsRepository.EventsWindow(LocalDate.of(2021, 10, 1), LocalDate.of(2021, 10, 1), timeZone)

        assertThat(eventsWindows.getFullyOverlappingWindow(windowBefore)).isNull()
        assertThat(eventsWindows.getFullyOverlappingWindow(windowOverlappingStart)).isNull()
        assertThat(eventsWindows.getFullyOverlappingWindow(windowInside)).isNotNull()
        assertThat(eventsWindows.getFullyOverlappingWindow(windowBetween)).isNull()
        assertThat(eventsWindows.getFullyOverlappingWindow(windowOverlappingEnd)).isNull()
        assertThat(eventsWindows.getFullyOverlappingWindow(windowAfter)).isNull()

    }

    @Test
    fun `Calculate weeks between dates`() {
        assertThat(calculateWeekNumberBetween(
            LocalDate.of(2021, 6, 1),
            LocalDate.of(2021, 7, 15),
            DayOfWeek.MONDAY))
            .isEqualTo(6)
        assertThat(calculateWeekNumberBetween(
            LocalDate.of(2021, 6, 1),
            LocalDate.of(2021, 4, 20),
            DayOfWeek.MONDAY))
            .isEqualTo(-6)
        assertThat(calculateWeekNumberBetween(
            LocalDate.of(2021, 6, 1),
            LocalDate.of(2023, 4, 6),
            DayOfWeek.MONDAY))
            .isEqualTo(96)
        // End date's week number is previous year's last week number
        assertThat(calculateWeekNumberBetween(
            LocalDate.of(2021, 6, 1),
            LocalDate.of(2023, 1, 1),
            DayOfWeek.MONDAY))
            .isEqualTo(82)
        // Start date's week number is previous year's last week number
        assertThat(calculateWeekNumberBetween(
            LocalDate.of(2023, 1, 1),
            LocalDate.of(2021, 6, 1),
            DayOfWeek.MONDAY))
            .isEqualTo(-82)
        assertThat(calculateWeekNumberBetween(
            LocalDate.of(2021, 6, 1),
            LocalDate.of(2019, 9, 18),
            DayOfWeek.MONDAY))
            .isEqualTo(-89)
    }

    @Test
    fun `Calculate last week of the month offset`() {
        assertThat(getLastWeekOfMonthOffset(
            DayOfWeek.MONDAY,
            LocalDate.of(2021, 6, 30)
        )).isEqualTo(4)
        assertThat(getLastWeekOfMonthOffset(
            DayOfWeek.SATURDAY,
            LocalDate.of(2021, 6, 30)
        )).isEqualTo(2)
        assertThat(getLastWeekOfMonthOffset(
            DayOfWeek.SUNDAY,
            LocalDate.of(2021, 6, 30)
        )).isEqualTo(3)

        assertThat(getLastWeekOfMonthOffset(
            DayOfWeek.SUNDAY,
            LocalDate.of(2021, 7, 31)
        )).isEqualTo(0)
        assertThat(getLastWeekOfMonthOffset(
            DayOfWeek.SATURDAY,
            LocalDate.of(2021, 7, 31)
        )).isEqualTo(6)
        assertThat(getLastWeekOfMonthOffset(
            DayOfWeek.MONDAY,
            LocalDate.of(2021, 7, 31)
        )).isEqualTo(1)
    }
}
