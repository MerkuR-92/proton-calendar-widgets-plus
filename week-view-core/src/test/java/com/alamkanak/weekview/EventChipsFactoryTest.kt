package com.alamkanak.weekview

import com.alamkanak.weekview.util.MockFactory
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class EventChipsFactoryTest {

    private val viewState: ViewState = mock()
    private val factory = EventChipsFactory()

    @Before
    fun setUp() {
        whenever(viewState.minHour).thenReturn(0)
        whenever(viewState.maxHour).thenReturn(24)
    }

    @Test
    fun `zero-duration midnight event produces an event chip`() {
        val midnight = today().withHour(0)
        val event = MockFactory.resolvedWeekViewEntity(startTime = midnight, endTime = midnight)

        val chips = factory.create(listOf(event), viewState)

        assertThat(chips).hasSize(1)
    }

    @Test
    fun `multi-day event ending at midnight does not bleed into next day`() {
        val startTime = today().withHour(10)
        val endTime = today().plusDays(1).withHour(0)
        val event = MockFactory.resolvedWeekViewEntity(startTime = startTime, endTime = endTime)

        val chips = factory.create(listOf(event), viewState)

        val lastChipEnd = chips.last().endTime
        assertThat(lastChipEnd.timeInMillis).isEqualTo(today().plusDays(1).timeInMillis - 1)
    }

    @Test
    fun `empty event list returns empty chips`() {
        val result = factory.create(emptyList(), viewState)
        assertThat(result).isEmpty()
    }

    @Test
    fun `single event produces one chip with full width`() {
        val event = MockFactory.resolvedWeekViewEntity(
            startTime = today().withHour(10),
            endTime = today().withHour(11)
        )

        val chips = factory.create(listOf(event), viewState)

        assertThat(chips).hasSize(1)
        assertThat(chips[0].eventId).isEqualTo(event.id)
        assertThat(chips[0].relativeStart).isEqualTo(0f)
        assertThat(chips[0].relativeWidth).isEqualTo(1f)
    }

    @Test
    fun `single event chip preserves start and end times`() {
        val event = MockFactory.resolvedWeekViewEntity(
            startTime = today().withHour(14),
            endTime = today().withHour(16)
        )

        val chips = factory.create(listOf(event), viewState)

        assertThat(chips).hasSize(1)
        assertThat(chips[0].startTime.timeInMillis).isEqualTo(event.startTime.timeInMillis)
        assertThat(chips[0].endTime.timeInMillis).isEqualTo(event.endTime.timeInMillis)
    }

    @Test
    fun `two sequential events each get full width`() {
        val event1 = MockFactory.resolvedWeekViewEntity(
            startTime = today().withHour(9),
            endTime = today().withHour(10)
        )
        val event2 = MockFactory.resolvedWeekViewEntity(
            startTime = today().withHour(11),
            endTime = today().withHour(12)
        )

        val chips = factory.create(listOf(event1, event2), viewState)

        assertThat(chips).hasSize(2)
        chips.forEach { chip ->
            assertThat(chip.relativeStart).isEqualTo(0f)
            assertThat(chip.relativeWidth).isEqualTo(1f)
        }
    }

    @Test
    fun `two overlapping events share width`() {
        val event1 = MockFactory.resolvedWeekViewEntity(
            startTime = today().withHour(9),
            endTime = today().withHour(11)
        )
        val event2 = MockFactory.resolvedWeekViewEntity(
            startTime = today().withHour(10),
            endTime = today().withHour(12)
        )

        val chips = factory.create(listOf(event1, event2), viewState)

        assertThat(chips).hasSize(2)
        val chip1 = chips.first { it.eventId == event1.id }
        val chip2 = chips.first { it.eventId == event2.id }

        assertThat(chip1.relativeWidth).isEqualTo(0.5f)
        assertThat(chip2.relativeWidth).isEqualTo(0.5f)
        assertThat(chip1.relativeStart).isNotEqualTo(chip2.relativeStart)
    }

    @Test
    fun `two fully overlapping events share width`() {
        val event1 = MockFactory.resolvedWeekViewEntity(
            startTime = today().withHour(9),
            endTime = today().withHour(11)
        )
        val event2 = MockFactory.resolvedWeekViewEntity(
            startTime = today().withHour(9),
            endTime = today().withHour(11)
        )

        val chips = factory.create(listOf(event1, event2), viewState)

        assertThat(chips).hasSize(2)
        assertThat(chips[0].relativeWidth).isEqualTo(0.5f)
        assertThat(chips[1].relativeWidth).isEqualTo(0.5f)
    }

    @Test
    fun `three overlapping events get one-third width each`() {
        val event1 = MockFactory.resolvedWeekViewEntity(
            startTime = today().withHour(9),
            endTime = today().withHour(12)
        )
        val event2 = MockFactory.resolvedWeekViewEntity(
            startTime = today().withHour(9),
            endTime = today().withHour(12)
        )
        val event3 = MockFactory.resolvedWeekViewEntity(
            startTime = today().withHour(9),
            endTime = today().withHour(12)
        )

        val chips = factory.create(listOf(event1, event2, event3), viewState)

        assertThat(chips).hasSize(3)
        chips.forEach { chip ->
            assertThat(chip.relativeWidth).isWithin(0.01f).of(1f / 3f)
        }
    }

    @Test
    fun `events are sorted by start time`() {
        val laterEvent = MockFactory.resolvedWeekViewEntity(
            startTime = today().withHour(14),
            endTime = today().withHour(15)
        )
        val earlierEvent = MockFactory.resolvedWeekViewEntity(
            startTime = today().withHour(9),
            endTime = today().withHour(10)
        )

        val chips = factory.create(listOf(laterEvent, earlierEvent), viewState)

        assertThat(chips).hasSize(2)
        assertThat(chips[0].eventId).isEqualTo(earlierEvent.id)
        assertThat(chips[1].eventId).isEqualTo(laterEvent.id)
    }

    @Test
    fun `multi-day event is split into multiple chips`() {
        val event = MockFactory.resolvedWeekViewEntity(
            startTime = today().withHour(20),
            endTime = today().plusDays(1).withHour(8)
        )

        val chips = factory.create(listOf(event), viewState)

        assertThat(chips.size).isGreaterThan(1)
        chips.forEach { chip ->
            assertThat(chip.eventId).isEqualTo(event.id)
        }
    }

    @Test
    fun `multi-day event chips have sequential indices`() {
        val event = MockFactory.resolvedWeekViewEntity(
            startTime = today().withHour(20),
            endTime = today().plusDays(2).withHour(8)
        )

        val chips = factory.create(listOf(event), viewState)

        val indices = chips.map { it.index }
        assertThat(indices).isEqualTo(indices.sorted())
        assertThat(indices.first()).isEqualTo(0)
    }

    @Test
    fun `events on different days are independent`() {
        val todayEvent = MockFactory.resolvedWeekViewEntity(
            startTime = today().withHour(9),
            endTime = today().withHour(11)
        )
        val tomorrowEvent = MockFactory.resolvedWeekViewEntity(
            startTime = today().plusDays(1).withHour(9),
            endTime = today().plusDays(1).withHour(11)
        )

        val chips = factory.create(listOf(todayEvent, tomorrowEvent), viewState)

        assertThat(chips).hasSize(2)
        chips.forEach { chip ->
            assertThat(chip.relativeStart).isEqualTo(0f)
            assertThat(chip.relativeWidth).isEqualTo(1f)
        }
    }

    @Test
    fun `overlapping pair plus non-overlapping event produces correct widths`() {
        val event1 = MockFactory.resolvedWeekViewEntity(
            startTime = today().withHour(9),
            endTime = today().withHour(11)
        )
        val event2 = MockFactory.resolvedWeekViewEntity(
            startTime = today().withHour(10),
            endTime = today().withHour(12)
        )
        val event3 = MockFactory.resolvedWeekViewEntity(
            startTime = today().withHour(14),
            endTime = today().withHour(15)
        )

        val chips = factory.create(listOf(event1, event2, event3), viewState)

        assertThat(chips).hasSize(3)
        val chip1 = chips.first { it.eventId == event1.id }
        val chip2 = chips.first { it.eventId == event2.id }
        val chip3 = chips.first { it.eventId == event3.id }

        assertThat(chip1.relativeWidth).isEqualTo(0.5f)
        assertThat(chip2.relativeWidth).isEqualTo(0.5f)
        assertThat(chip3.relativeWidth).isGreaterThan(0f)
    }

    @Test
    fun `event ending at midnight is sanitized`() {
        val event = MockFactory.resolvedWeekViewEntity(
            startTime = today().withHour(22),
            endTime = today().plusDays(1).withHour(0).withMinutes(0)
        )

        val chips = factory.create(listOf(event), viewState)

        assertThat(chips).isNotEmpty()
    }

    @Test
    fun `all-day event produces chip`() {
        val event = MockFactory.resolvedWeekViewEntity(
            startTime = today().withHour(0),
            endTime = today().withHour(23).withMinutes(59),
            isAllDay = true
        )

        val chips = factory.create(listOf(event), viewState)

        assertThat(chips).isNotEmpty()
        assertThat(chips[0].event.isAllDay).isTrue()
    }

    @Test
    fun `minutesFromStartHour is set correctly`() {
        whenever(viewState.minutesFromStart(org.mockito.kotlin.any())).thenAnswer { invocation ->
            val cal = invocation.getArgument<java.util.Calendar>(0)
            (cal.get(java.util.Calendar.HOUR_OF_DAY) - viewState.minHour) * 60 + cal.get(java.util.Calendar.MINUTE)
        }

        val event = MockFactory.resolvedWeekViewEntity(
            startTime = today().withHour(10).withMinutes(30),
            endTime = today().withHour(11).withMinutes(30)
        )

        val chips = factory.create(listOf(event), viewState)

        assertThat(chips).hasSize(1)
        assertThat(chips[0].minutesFromStartHour).isEqualTo(10 * 60 + 30)
    }

    @Test
    fun `same events produce identical chips on repeated calls`() {
        val events = (0 until 5).map {
            MockFactory.resolvedWeekViewEntity(
                startTime = today().withHour(9 + it),
                endTime = today().withHour(10 + it)
            )
        }

        val chips1 = factory.create(events, viewState)
        val chips2 = factory.create(events, viewState)

        assertThat(chips1.map { Triple(it.eventId, it.relativeStart, it.relativeWidth) })
            .isEqualTo(chips2.map { Triple(it.eventId, it.relativeStart, it.relativeWidth) })
    }
}
