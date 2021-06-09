package me.proton.android.calendar.common

import assertk.assertThat
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import me.proton.android.calendar.common.DateTimeUtilsImpl.getFullyOverlappingWindow
import me.proton.android.calendar.domain.CalendarsRepository
import org.junit.jupiter.api.Test
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

}
