package com.alamkanak.weekview

import com.alamkanak.weekview.util.createResolvedWeekViewEvent
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class EventChipsFactoryTest {

    private val viewState: ViewState = mock()
    private val factory = EventChipsFactory()

    @Test
    fun `zero-duration midnight event produces an event chip`() {
        whenever(viewState.minHour).thenReturn(0)
        whenever(viewState.maxHour).thenReturn(24)

        val midnight = today().withHour(0)
        val event = createResolvedWeekViewEvent(startTime = midnight, endTime = midnight)

        val chips = factory.create(listOf(event), viewState)

        assertEquals(1, chips.size)
    }

    @Test
    fun `multi-day event ending at midnight does not bleed into next day`() {
        whenever(viewState.minHour).thenReturn(0)
        whenever(viewState.maxHour).thenReturn(24)

        val startTime = today().withHour(10)
        val endTime = today().plusDays(1).withHour(0)
        val event = createResolvedWeekViewEvent(startTime = startTime, endTime = endTime)

        val chips = factory.create(listOf(event), viewState)

        val lastChipEnd = chips.last().endTime
        assertEquals(today().plusDays(1).timeInMillis - 1, lastChipEnd.timeInMillis)
    }
}
