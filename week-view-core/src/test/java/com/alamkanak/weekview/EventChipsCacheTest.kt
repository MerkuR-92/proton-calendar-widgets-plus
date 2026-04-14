package com.alamkanak.weekview

import com.alamkanak.weekview.util.MockFactory
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import java.util.Calendar

class EventChipsCacheTest {

    private lateinit var cache: EventChipsCache

    @Before
    fun setUp() {
        cache = EventChipsCache()
    }

    @Test
    fun `new cache has no event chips`() {
        assertThat(cache.allEventChips).isEmpty()
    }

    @Test
    fun `query on empty cache returns empty`() {
        assertThat(cache.normalEventChipsByDate(today())).isEmpty()
    }

    @Test
    fun `allDay query on empty cache returns empty`() {
        assertThat(cache.allDayEventChipsByDate(today())).isEmpty()
    }

    @Test
    fun `addAll adds normal event chips retrievable by date`() {
        val event = MockFactory.resolvedWeekViewEntity(
            startTime = today().withHour(10),
            endTime = today().withHour(11)
        )
        val chip = createChip(event)

        cache.addAll(listOf(chip))

        assertThat(cache.normalEventChipsByDate(today())).containsExactly(chip)
        assertThat(cache.allEventChips).containsExactly(chip)
    }

    @Test
    fun `addAll adds all-day event chips retrievable by date`() {
        val event = MockFactory.resolvedWeekViewEntity(
            startTime = today().withHour(0),
            endTime = today().withHour(23),
            isAllDay = true
        )
        val chip = createChip(event)

        cache.addAll(listOf(chip))

        assertThat(cache.allDayEventChipsByDate(today())).containsExactly(chip)
        assertThat(cache.normalEventChipsByDate(today())).isEmpty()
    }

    @Test
    fun `addAll with duplicate event id replaces existing chip`() {
        val event = MockFactory.resolvedWeekViewEntity(
            startTime = today().withHour(10),
            endTime = today().withHour(11)
        )
        cache.addAll(listOf(createChip(event)))
        cache.addAll(listOf(createChip(event)))

        assertThat(cache.allEventChips).hasSize(1)
    }

    @Test
    fun `chips on different dates are retrievable separately`() {
        val todayEvent = MockFactory.resolvedWeekViewEntity(
            startTime = today().withHour(10),
            endTime = today().withHour(11)
        )
        val tomorrowEvent = MockFactory.resolvedWeekViewEntity(
            startTime = today().plusDays(1).withHour(10),
            endTime = today().plusDays(1).withHour(11)
        )
        val todayChip = createChip(todayEvent)
        val tomorrowChip = createChip(tomorrowEvent, startTime = today().plusDays(1).withHour(10))

        cache.addAll(listOf(todayChip, tomorrowChip))

        assertThat(cache.normalEventChipsByDate(today())).containsExactly(todayChip)
        assertThat(cache.normalEventChipsByDate(today().plusDays(1))).containsExactly(tomorrowChip)
    }

    @Test
    fun `allEventChipsInDateRange returns chips for specified dates only`() {
        val todayEvent = MockFactory.resolvedWeekViewEntity(
            startTime = today().withHour(10),
            endTime = today().withHour(11)
        )
        val tomorrowEvent = MockFactory.resolvedWeekViewEntity(
            startTime = today().plusDays(1).withHour(10),
            endTime = today().plusDays(1).withHour(11)
        )
        val dayAfterEvent = MockFactory.resolvedWeekViewEntity(
            startTime = today().plusDays(2).withHour(10),
            endTime = today().plusDays(2).withHour(11)
        )
        cache.addAll(
            listOf(
                createChip(todayEvent),
                createChip(tomorrowEvent, startTime = today().plusDays(1).withHour(10)),
                createChip(dayAfterEvent, startTime = today().plusDays(2).withHour(10))
            )
        )

        val range = listOf(today(), today().plusDays(1))
        val result = cache.allEventChipsInDateRange(range)

        assertThat(result).hasSize(2)
        assertThat(result.map { it.eventId }).doesNotContain(dayAfterEvent.id)
    }

    @Test
    fun `remove by eventId removes the chip`() {
        val event = MockFactory.resolvedWeekViewEntity(
            startTime = today().withHour(10),
            endTime = today().withHour(11)
        )
        val chip = createChip(event)
        cache.addAll(listOf(chip))

        cache.remove(eventId = event.id)

        assertThat(cache.allEventChips).isEmpty()
    }

    @Test
    fun `removeAll removes matching chips`() {
        val event1 = MockFactory.resolvedWeekViewEntity(
            startTime = today().withHour(10),
            endTime = today().withHour(11)
        )
        val event2 = MockFactory.resolvedWeekViewEntity(
            startTime = today().withHour(12),
            endTime = today().withHour(13)
        )
        cache.addAll(listOf(createChip(event1), createChip(event2)))

        cache.removeAll(listOf(event1))

        assertThat(cache.allEventChips).hasSize(1)
        assertThat(cache.allEventChips[0].eventId).isEqualTo(event2.id)
    }

    @Test
    fun `clear removes all chips`() {
        val event = MockFactory.resolvedWeekViewEntity(
            startTime = today().withHour(10),
            endTime = today().withHour(11)
        )
        cache.addAll(listOf(createChip(event)))

        cache.clear()

        assertThat(cache.allEventChips).isEmpty()
    }

    @Test
    fun `replaceAll clears existing and adds new`() {
        val event1 = MockFactory.resolvedWeekViewEntity(
            startTime = today().withHour(10),
            endTime = today().withHour(11)
        )
        cache.addAll(listOf(createChip(event1)))

        val event2 = MockFactory.resolvedWeekViewEntity(
            startTime = today().withHour(12),
            endTime = today().withHour(13)
        )
        cache.replaceAll(listOf(createChip(event2)))

        assertThat(cache.allEventChips).hasSize(1)
        assertThat(cache.allEventChips[0].eventId).isEqualTo(event2.id)
    }

    @Test
    fun `findHitEvent returns null when no chips`() {
        assertThat(cache.findHitEvent(50f, 50f)).isNull()
    }

    @Test
    fun `findHitEvent returns null when no chips have matching bounds`() {
        val event = MockFactory.resolvedWeekViewEntity(
            startTime = today().withHour(10),
            endTime = today().withHour(11)
        )
        cache.addAll(listOf(createChip(event)))

        assertThat(cache.findHitEvent(50f, 50f)).isNull()
    }

    @Test
    fun `generation increments on addAll`() {
        val gen = cache.generation
        cache.addAll(listOf(createChip(MockFactory.resolvedWeekViewEntity(
            startTime = today().withHour(10),
            endTime = today().withHour(11)
        ))))
        assertThat(cache.generation).isGreaterThan(gen)
    }

    @Test
    fun `generation increments on removeAll`() {
        val event = MockFactory.resolvedWeekViewEntity(
            startTime = today().withHour(10),
            endTime = today().withHour(11)
        )
        cache.addAll(listOf(createChip(event)))
        val gen = cache.generation
        cache.removeAll(listOf(event))
        assertThat(cache.generation).isGreaterThan(gen)
    }

    @Test
    fun `generation increments on clear`() {
        val gen = cache.generation
        cache.clear()
        assertThat(cache.generation).isGreaterThan(gen)
    }

    private fun createChip(
        event: ResolvedWeekViewEntity,
        startTime: Calendar = event.startTime,
        endTime: Calendar = event.endTime,
        index: Int = 0
    ) = EventChip(
        event = event,
        index = index,
        startTime = startTime,
        endTime = endTime
    )
}
