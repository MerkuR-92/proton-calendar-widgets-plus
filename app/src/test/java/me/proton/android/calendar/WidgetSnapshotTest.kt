package me.proton.android.calendar

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import io.mockk.mockk
import io.mockk.verify
import me.proton.android.calendar.domain.ResourceProvider
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.domain.model.UiEvent
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

internal class WidgetSnapshotTest {

    private val resourceProvider = mockk<ResourceProvider>(relaxed = true)

    @Test
    fun `caps the number of widget events`() {
        val zone = ZoneId.of("UTC")
        val base = ZonedDateTime.now(zone).plusDays(1)
        val events = (0 until 200).map { i ->
            UiEvent().copy(
                id = "event-$i",
                summary = "Event $i",
                dateStart = base,
                dateEnd = base.plusHours(1),
                isAllDay = false,
                decryptionStatus = Event.DecryptionStatus.Success,
                displayColor = "#FF0000",
            )
        }

        val today = LocalDate.now(zone)
        val snapshot = events.toWidgetSnapshot(
            resourceProvider = resourceProvider,
            fromDate = today,
            toDate = today.plusDays(CalendarMonthAgendaWidget.WIDGET_DAYS_AHEAD.toLong()),
            zoneId = zone,
            is24Hour = true,
        )

        assertThat(snapshot.events.size).isEqualTo(100)
    }

    @Test
    fun `stops building events once the cap is reached`() {
        val zone = ZoneId.of("UTC")
        val base = ZonedDateTime.now(zone).plusDays(1)
        val events = (0 until 200).map { i ->
            UiEvent().copy(
                id = "event-$i",
                summary = "Event $i",
                dateStart = base,
                dateEnd = base.plusDays(1),
                isAllDay = true,
                decryptionStatus = Event.DecryptionStatus.Success,
                displayColor = "#FF0000",
            )
        }

        val today = LocalDate.now(zone)
        events.toWidgetSnapshot(
            resourceProvider = resourceProvider,
            fromDate = today,
            toDate = today.plusDays(CalendarMonthAgendaWidget.WIDGET_DAYS_AHEAD.toLong()),
            zoneId = zone,
            is24Hour = true,
        )

        verify(atMost = MAX_WIDGET_EVENTS) { resourceProvider.provideString(R.string.event_all_day) }
    }

    @Test
    fun `keeps the earliest events in chronological order`() {
        val zone = ZoneId.of("UTC")
        val today = LocalDate.now(zone)
        val events = (1..13).flatMap { dayOffset ->
            val start = today.plusDays(dayOffset.toLong()).atTime(12, 0).atZone(zone)
            (0 until 10).map { k ->
                UiEvent().copy(
                    id = "day$dayOffset-event$k",
                    summary = "Event $dayOffset-$k",
                    dateStart = start,
                    dateEnd = start.plusHours(1),
                    isAllDay = false,
                    decryptionStatus = Event.DecryptionStatus.Success,
                    displayColor = "#FF0000",
                )
            }
        }

        val snapshot = events.toWidgetSnapshot(
            resourceProvider = resourceProvider,
            fromDate = today,
            toDate = today.plusDays(CalendarMonthAgendaWidget.WIDGET_DAYS_AHEAD.toLong()),
            zoneId = zone,
            is24Hour = true,
        )

        assertThat(snapshot.events.size).isEqualTo(100)
        val days = snapshot.events.map { it.happensOn }
        assertThat(days.zipWithNext().all { (a, b) -> !a.isAfter(b) }).isTrue()
        assertThat(days.last()).isEqualTo(today.plusDays(10))
    }
}
